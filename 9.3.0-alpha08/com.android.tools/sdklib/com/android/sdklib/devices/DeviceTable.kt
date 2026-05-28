/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.ProgressManagerAdapter
import com.android.SdkConstants
import com.android.repository.api.RepoManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.utils.ILogger
import com.google.common.collect.HashBasedTable
import com.google.common.collect.ImmutableTable
import com.google.common.collect.Table
import java.io.FileNotFoundException
import java.io.InputStream

interface DeviceTable {
  fun getDevice(id: String, manufacturer: String): Device?

  fun getDevices(): Table<String, String, Device>
}

/** A [DeviceTable] implementation that loads from XML files on demand. */
abstract class AbstractLazyDeviceTable(
  private val log: ILogger,
  private val isSupportedDevice: (Device) -> Boolean,
  private val abortOnFailure: Boolean = false,
) : DeviceTable {

  private val table by lazy { load() }

  abstract fun load(): Table<String, String, Device>?

  /**
   * For each input, produces an input stream using [open] and parses a device XML from it. Entries in later inputs override entries from
   * earlier inputs.
   */
  protected fun <T> readInputsToTable(inputs: List<T>, open: (T) -> InputStream): Table<String, String, Device>? {
    val table = HashBasedTable.create<String, String, Device>()
    for (input in inputs) {
      try {
        parseDevicesToTable(open(input), table)
      } catch (e: Exception) {
        ProgressManagerAdapter.throwIfCancellation(e)
        log.error(e, "Could not load devices from $input")
        if (abortOnFailure) {
          return null
        }
      }
    }
    return ImmutableTable.copyOf(table)
  }

  /** Parses a device XML from [inputStream] and adds the resulting devices to [table]. */
  protected fun parseDevicesToTable(inputStream: InputStream, table: Table<String, String, Device>) {
    inputStream.use { stream ->
      DeviceParser.parse(stream).cellSet().forEach { cell: Table.Cell<String, String, Device> ->
        if (isSupportedDevice(cell.getValue())) {
          table.put(cell.getRowKey(), cell.getColumnKey(), mapDevice(cell.getValue()))
        } else {
          log.warning("Unsupported device %s", cell.getRowKey())
        }
      }
    }
  }

  /** Transforms a [Device] before it is added to the output table. */
  protected open fun mapDevice(device: Device): Device = device

  override fun getDevice(id: String, manufacturer: String): Device? = table?.get(id, manufacturer)

  override fun getDevices(): Table<String, String, Device> = table ?: ImmutableTable.of()
}

/** A [DeviceTable] that loads from XML files stored as resources. */
class DeviceResourceTable(log: ILogger, isSupportedDevice: (Device) -> Boolean, val resources: List<String>) :
  AbstractLazyDeviceTable(log, isSupportedDevice, abortOnFailure = true) {

  override fun load(): Table<String, String, Device>? {
    return readInputsToTable(resources) {
      DeviceManager::class.java.getResourceAsStream("$it.xml") ?: throw FileNotFoundException("$it.xml")
    }
  }
}

/** A [DeviceTable] that loads from devices.xml files that are stored in system images. */
class SystemImageDeviceTable(val log: ILogger, isSupportedDevice: (Device) -> Boolean, val sdkHandler: AndroidSdkHandler) :
  AbstractLazyDeviceTable(log, isSupportedDevice) {

  override fun load(): Table<String, String, Device>? {
    val progress = LoggerProgressIndicatorWrapper(log)
    val repoManager = sdkHandler.getRepoManager(progress)
    repoManager.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progress)
    val paths =
      repoManager.packages.localPackages.values
        .filter { it.typeDetails is DetailsTypes.SysImgDetailsType }
        .sortedBy { (it.typeDetails as DetailsTypes.SysImgDetailsType).androidVersion }
        .mapNotNull { it.location.resolve(SdkConstants.FN_DEVICES_XML) }
    return readInputsToTable(paths, DeviceParser::openInputStream)
  }

  override fun mapDevice(device: Device): Device =
    when {
      isDeprecatedWearDevice(device) -> Device.Builder(device).apply { setDeprecated(true) }.build()
      else -> device
    }

  /**
   * Some device definitions are present in old system images which can override some vendor device definitions. This method ensures these
   * devices are considered as deprecated. For example, `wearos_rect` is deprecated in the vendor definition, however APIs 30 and 33 also
   * provide a `wearos_rect` which is not deprecated. This function checks whether the device is a deprecated wear device based on its id.
   * If the device's id does not start with `wearos` or the id is either `wearos_square` or `wearos_rect` then it is considered as
   * deprecated.
   */
  private fun isDeprecatedWearDevice(device: Device): Boolean {
    if ("android-wear" != device.tagId) return false
    return !device.id.startsWith("wearos") || "wearos_square" == device.id || "wearos_rect" == device.id
  }
}
