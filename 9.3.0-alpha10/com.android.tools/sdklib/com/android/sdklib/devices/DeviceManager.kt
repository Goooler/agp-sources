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
import com.android.prefs.AndroidLocationsProvider
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.utils.ILogger
import com.google.common.collect.HashBasedTable
import com.google.common.collect.ImmutableList
import com.google.common.collect.Table
import java.nio.file.Path
import java.util.Collections
import java.util.EnumSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Manager class for interacting with [Device]s within the SDK */
class DeviceManager(tables: Map<DeviceCategory, DeviceTable>) {
  private val deviceTables: Map<DeviceCategory, DeviceTable> = tables.toSortedMap()

  /**
   * Sources of device definitions available to the device manager, in priority order (earlier sources take precedence). We favor built-in
   * definitions over user and system image provided definitions to avoid surprises; Studio may depend on the built-in definitions.
   */
  enum class DeviceCategory {
    /** getDevices() flag to list default devices from the bundled devices.xml definitions. */
    DEFAULT,
    /** getDevices() flag to list vendor devices -- the bundled nexus.xml devices as well as all those coming from extra packages. */
    VENDOR,
    /** getDevices() flag to list devices from system-images/platform-N/tag/abi/devices.xml */
    SYSTEM_IMAGES,
    /** getDevices() flag to list user devices saved in the .android home folder. */
    USER,
  }

  fun getDevice(id: String, manufacturer: String): Device? {
    return deviceTables.values.firstNotNullOfOrNull { it.getDevice(id, manufacturer) }
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
    val devices = HashBasedTable.create<String, String, Device>()
    for (category in deviceCategory.sortedDescending()) {
      deviceTables[category]?.getDevices()?.let { devices.putAll(it) }
    }
    return Collections.unmodifiableCollection(devices.values())
  }

  fun getDevices(): Collection<Device> = getDevices(ALL_DEVICES)

  fun getUserDevices(): UserDeviceTable? = deviceTables[DeviceCategory.USER] as? UserDeviceTable

  val deviceFlow: Flow<Table<String, String, Device>> =
    combine(deviceTables.values.map { it.deviceFlow }) { tables ->
      val devices = HashBasedTable.create<String, String, Device>()
      for (table in tables.reversed()) {
        devices.putAll(table)
      }
      devices
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
      val vendorDevices = DeviceResourceTable(log, isSupportedDevice, VENDOR_DEVICE_RESOURCES)
      val defaultDevices = DeviceResourceTable(log, isSupportedDevice = { true }, listOf("devices"))
      val sysImgDevices = SystemImageDeviceTable(log, isSupportedDevice, sdkHandler)
      val userDevices = sdkHandler.androidFolder?.let { UserDeviceTable(log, isSupportedDevice, it.resolve(SdkConstants.FN_DEVICES_XML)) }

      return DeviceManager(
        buildMap {
          put(DeviceCategory.SYSTEM_IMAGES, sysImgDevices)
          put(DeviceCategory.VENDOR, vendorDevices)
          put(DeviceCategory.DEFAULT, defaultDevices)
          userDevices?.let { put(DeviceCategory.USER, it) }
        }
      )
    }
  }
}
