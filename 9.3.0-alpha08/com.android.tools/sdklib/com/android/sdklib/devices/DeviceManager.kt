/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.sdklib.devices

import com.android.SdkConstants
import com.android.io.CancellableFileIo
import com.android.prefs.AndroidLocationsProvider
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.utils.ILogger
import com.google.common.collect.HashBasedTable
import com.google.common.collect.ImmutableList
import com.google.common.collect.Table
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.EnumSet
import javax.xml.parsers.ParserConfigurationException
import org.xml.sax.SAXException

/** Manager class for interacting with [Device]s within the SDK */
class DeviceManager
private constructor(sdkHandler: AndroidSdkHandler, private val log: ILogger, private val isSupportedDevice: (Device) -> Boolean) {
  private val androidFolder: Path? = sdkHandler.androidFolder
  private val vendorDevices = DeviceResourceTable(log, isSupportedDevice, VENDOR_DEVICE_RESOURCES)
  private val defaultDevices = DeviceResourceTable(log, isSupportedDevice = { true }, listOf("devices"))
  private val lock = Any()
  private val listeners: MutableList<DevicesChangedListener> = ArrayList()

  // These are keyed by (device ID, manufacturer)
  private var sysImgDevices = SystemImageDeviceTable(log, isSupportedDevice, sdkHandler)
  private var userDevices: Table<String, String, Device>? = null

  enum class DeviceCategory {
    /** getDevices() flag to list default devices from the bundled devices.xml definitions. */
    DEFAULT,
    /** getDevices() flag to list user devices saved in the .android home folder. */
    USER,
    /** getDevices() flag to list vendor devices -- the bundled nexus.xml devices as well as all those coming from extra packages. */
    VENDOR,
    /** getDevices() flag to list devices from system-images/platform-N/tag/abi/devices.xml */
    SYSTEM_IMAGES,
  }

  /** Interface implemented by objects which want to know when changes occur to the [Device] lists. */
  fun interface DevicesChangedListener {
    /** Called after one of the [Device] lists has been updated. */
    fun onDevicesChanged()
  }

  /**
   * Register a listener to be notified when the device lists are modified.
   *
   * @param listener The listener to add. Ignored if already registered.
   */
  fun registerListener(listener: DevicesChangedListener) {
    synchronized(listeners) {
      if (listener !in listeners) {
        listeners.add(listener)
      }
    }
  }

  /**
   * Removes a listener from the notification list such that it will no longer receive notifications when modifications to the [Device] list
   * occur.
   *
   * @param listener The listener to remove.
   */
  fun unregisterListener(listener: DevicesChangedListener): Boolean {
    synchronized(listeners) {
      return listeners.remove(listener)
    }
  }

  fun getDevice(id: String, manufacturer: String): Device? {
    initDevicesLists()
    return userDevices?.get(id, manufacturer)
      ?: sysImgDevices.getDevice(id, manufacturer)
      ?: defaultDevices.getDevice(id, manufacturer)
      ?: vendorDevices.getDevice(id, manufacturer)
  }

  fun getDevice(avdInfo: AvdInfo): Device? {
    return getDevice(avdInfo.deviceName, avdInfo.deviceManufacturer)
  }

  /**
   * Returns the known [Device] list.
   *
   * @param deviceCategory One of the [DeviceCategory] constants.
   * @return A copy of the list of [Device]s. Can be empty but not null.
   */
  fun getDevices(deviceCategory: DeviceCategory): Collection<Device> {
    return getDevices(EnumSet.of(deviceCategory))
  }

  /**
   * Returns the known [Device] list.
   *
   * @param deviceCategory A combination of the [DeviceCategory] constants or the constant [ALL_DEVICES].
   * @return A copy of the list of [Device]s. Can be empty but not null.
   */
  fun getDevices(deviceCategory: Collection<DeviceCategory>): Collection<Device> {
    initDevicesLists()
    val devices = HashBasedTable.create<String, String, Device>()
    userDevices?.let { if (DeviceCategory.USER in deviceCategory) devices.putAll(it) }
    if (DeviceCategory.DEFAULT in deviceCategory) {
      devices.putAll(defaultDevices.getDevices())
    }
    if (DeviceCategory.VENDOR in deviceCategory) {
      devices.putAll(vendorDevices.getDevices())
    }
    if (DeviceCategory.SYSTEM_IMAGES in deviceCategory) {
      devices.putAll(sysImgDevices.getDevices())
    }
    return Collections.unmodifiableCollection(devices.values())
  }

  private fun initDevicesLists() {
    val changed = initUserDevices()
    if (changed) {
      notifyListeners()
    }
  }

  /**
   * Initializes all user-created [Device]s
   *
   * @return True if the list has changed.
   */
  private fun initUserDevices(): Boolean {
    synchronized(lock) {
      if (userDevices != null) {
        return false
      }
      // User devices should be saved out to $HOME/.android/devices.xml
      val newUserDevices = HashBasedTable.create<String, String, Device>()
      val userDevicesFile = androidFolder?.resolve(SdkConstants.FN_DEVICES_XML)
      if (userDevicesFile != null && Files.exists(userDevicesFile)) {
        try {
          DeviceParser.parse(userDevicesFile).cellSet().forEach { cell ->
            val device = cell.value
            if (isSupportedDevice(device)) {
              newUserDevices.put(cell.rowKey, cell.columnKey, device)
            } else {
              log.warning("Unsupported device %s", cell.rowKey)
            }
          }

          userDevices = newUserDevices
          return true
        } catch (e: SAXException) {
          // Probably an old config file which we don't want to overwrite.
          val parent = userDevicesFile.toAbsolutePath().parent
          val base = userDevicesFile.fileName.toString() + ".old"
          var renamedConfig = parent.resolve(base)
          var i = 0
          while (CancellableFileIo.exists(renamedConfig)) {
            renamedConfig = parent.resolve("$base.${i++}")
          }
          log.error(e, "Error parsing %1\$s, backing up to %2\$s", userDevicesFile.toAbsolutePath(), renamedConfig.toAbsolutePath())
          try {
            Files.move(userDevicesFile, renamedConfig)
          } catch (moveException: IOException) {
            log.error(moveException, "Failed to rename old config file")
          }
        } catch (e: ParserConfigurationException) {
          log.error(e, "Error parsing %1\$s", userDevicesFile.toAbsolutePath())
        } catch (e: IOException) {
          log.error(e, "Error parsing %1\$s", userDevicesFile.toAbsolutePath())
        }
      }
    }
    userDevices = HashBasedTable.create()
    return false
  }

  fun addUserDevice(d: Device) {
    if (!isSupportedDevice(d)) return
    var changed = false
    synchronized(lock) {
      if (userDevices == null) {
        initUserDevices()
      }
      userDevices?.let {
        it.put(d.id, d.manufacturer, d)
        changed = true
      }
    }
    if (changed) {
      notifyListeners()
    }
  }

  fun removeUserDevice(d: Device) {
    synchronized(lock) {
      if (userDevices == null) {
        initUserDevices()
      }
      userDevices?.let {
        if (it.contains(d.id, d.manufacturer)) {
          it.remove(d.id, d.manufacturer)
          notifyListeners()
        }
      }
    }
  }

  fun replaceUserDevice(d: Device) {
    synchronized(lock) {
      if (userDevices == null) {
        initUserDevices()
      }
      removeUserDevice(d)
      addUserDevice(d)
    }
  }

  /** Saves out the user devices to [SdkConstants.FN_DEVICES_XML] in the Android folder. */
  fun saveUserDevices() {
    val currentDevices = userDevices ?: return
    val currentFolder = androidFolder ?: return
    val userDevicesFile = currentFolder.resolve(SdkConstants.FN_DEVICES_XML)

    if (currentDevices.isEmpty) {
      try {
        Files.deleteIfExists(userDevicesFile)
      } catch (_: IOException) {}
      return
    }

    synchronized(lock) {
      if (!currentDevices.isEmpty) {
        try {
          Files.newOutputStream(userDevicesFile).use { outputStream -> DeviceWriter.writeToXml(outputStream, currentDevices.values()) }
        } catch (e: Exception) {
          log.warning("Error writing file: 1%\$s", e.message)
        }
      }
    }
  }

  private fun notifyListeners() {
    synchronized(listeners) {
      for (listener in listeners) {
        listener.onDevicesChanged()
      }
    }
  }

  companion object {
    /** getDevices() flag to list all devices. */
    @JvmField val ALL_DEVICES: EnumSet<DeviceCategory> = EnumSet.allOf(DeviceCategory::class.java)

    /** Names of XML files bundled as resources containing device definitions for [DeviceCategory.VENDOR]. */
    val VENDOR_DEVICE_RESOURCES: ImmutableList<String> = ImmutableList.of("nexus", "wear", "tv", "automotive", "desktop", "xr")

    @JvmStatic
    fun createInstance(androidLocationsProvider: AndroidLocationsProvider, sdkLocation: Path?, log: ILogger): DeviceManager {
      return createInstance(AndroidSdkHandler.getInstance(androidLocationsProvider, sdkLocation), log, isSupportedDevice = { true })
    }

    @JvmStatic
    @JvmOverloads
    fun createInstance(sdkHandler: AndroidSdkHandler, log: ILogger, isSupportedDevice: (Device) -> Boolean = { true }): DeviceManager {
      return DeviceManager(sdkHandler, log, isSupportedDevice)
    }
  }
}
