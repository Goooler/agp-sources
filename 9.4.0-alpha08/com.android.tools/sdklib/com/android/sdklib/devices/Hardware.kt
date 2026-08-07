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

import com.android.resources.Keyboard
import com.android.resources.Navigation
import com.android.resources.UiMode
import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import java.io.File
import java.util.EnumSet

class Hardware {
  var screen: Screen? = null
  var environment: Environment? = null
  var touchpad: Touchpad? = null
  var hinge: Hinge? = null

  private val _networking = EnumSet.noneOf(Network::class.java)
  val networking: Set<Network>
    get() = _networking

  private val _sensors = EnumSet.noneOf(Sensor::class.java)
  val sensors: Set<Sensor>
    get() = _sensors

  @get:JvmName("hasMic") var hasMic: Boolean = false

  private val _cameras = mutableListOf<Camera>()
  val cameras: List<Camera>
    get() = _cameras

  var keyboard: Keyboard? = null
  var nav: Navigation? = null
  var ram: Storage? = null
  var buttonType: ButtonType? = null

  private val _internalStorage = mutableListOf<Storage>()
  val internalStorage: List<Storage>
    get() = _internalStorage

  private val _removableStorage = mutableListOf<Storage>()
  val removableStorage: List<Storage>
    get() = _removableStorage

  var cpu: String? = null
  var gpu: String? = null

  private val _abis = mutableListOf<Abi>()
  val supportedAbis: List<Abi>
    get() = ImmutableList.copyOf(_abis)

  private val _translatedAbis = mutableListOf<Abi>()
  val translatedAbis: List<Abi>
    get() = ImmutableList.copyOf(_translatedAbis)

  private val _uiModes = EnumSet.noneOf(UiMode::class.java)
  val supportedUiModes: Set<UiMode>
    get() = _uiModes

  var chargeType: PowerType? = null
  var skinFile: File? = null

  // Set default value to be false, DeviceParser will change it to true
  // when devices has <removable-storage>
  @get:JvmName("hasSdCard") var sdCard: Boolean = false

  fun addNetwork(network: Network) {
    _networking.add(network)
  }

  fun addAllNetworks(networks: Collection<Network>) {
    _networking.addAll(networks)
  }

  fun addSensor(sensor: Sensor) {
    _sensors.add(sensor)
  }

  fun addAllSensors(sensors: Collection<Sensor>) {
    _sensors.addAll(sensors)
  }

  fun addCamera(camera: Camera) {
    _cameras.add(camera)
  }

  fun addAllCameras(cameras: Collection<Camera>) {
    _cameras.addAll(cameras)
  }

  fun getCamera(index: Int): Camera = _cameras[index]

  fun getCamera(location: CameraLocation): Camera? = _cameras.find { it.location == location }

  fun addInternalStorage(storage: Storage) {
    _internalStorage.add(storage)
  }

  fun addAllInternalStorage(storages: Collection<Storage>) {
    _internalStorage.addAll(storages)
  }

  fun addRemovableStorage(storage: Storage) {
    _removableStorage.add(storage)
  }

  fun addAllRemovableStorage(storages: Collection<Storage>) {
    _removableStorage.addAll(storages)
  }

  fun addSupportedAbi(abi: Abi) {
    if (!_abis.contains(abi)) {
      _abis.add(abi)
    }
  }

  fun addAllSupportedAbis(abis: Collection<Abi>) {
    abis.forEach { addSupportedAbi(it) }
  }

  fun addTranslatedAbi(abi: Abi) {
    _translatedAbis.add(abi)
  }

  fun addAllTranslatedAbis(abis: Collection<Abi>) {
    _translatedAbis.addAll(abis)
  }

  fun addSupportedUiMode(uiMode: UiMode) {
    _uiModes.add(uiMode)
  }

  fun addAllSupportedUiModes(uiModes: Collection<UiMode>) {
    _uiModes.addAll(uiModes)
  }

  /**
   * Returns a copy of the object that shares no state with it, but is initialized to equivalent values.
   *
   * @return A copy of the object.
   */
  fun deepCopy(): Hardware {
    val hw = Hardware()
    hw.screen = screen?.deepCopy()
    hw.environment = environment?.deepCopy()
    hw.touchpad = touchpad?.deepCopy()
    hw.hinge = hinge?.deepCopy()
    hw._networking.addAll(_networking)
    hw._sensors.addAll(_sensors)
    hw.hasMic = hasMic
    for (c in _cameras) {
      hw._cameras.add(c.deepCopy())
    }
    hw.keyboard = keyboard
    hw.nav = nav
    hw.ram = ram
    hw.buttonType = buttonType
    hw._internalStorage.addAll(_internalStorage)
    hw._removableStorage.addAll(_removableStorage)
    hw.cpu = cpu
    hw.gpu = gpu
    hw._abis.addAll(_abis)
    hw._translatedAbis.addAll(_translatedAbis)
    hw._uiModes.addAll(_uiModes)
    hw.chargeType = chargeType
    hw.skinFile = skinFile
    hw.sdCard = sdCard
    return hw
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other !is Hardware) {
      return false
    }
    return screen == other.screen &&
      environment == other.environment &&
      touchpad == other.touchpad &&
      hinge == other.hinge &&
      _networking == other._networking &&
      _sensors == other._sensors &&
      hasMic == other.hasMic &&
      sdCard == other.sdCard &&
      _cameras == other._cameras &&
      keyboard == other.keyboard &&
      nav == other.nav &&
      ram == other.ram &&
      buttonType == other.buttonType &&
      _internalStorage == other._internalStorage &&
      _removableStorage == other._removableStorage &&
      cpu == other.cpu &&
      gpu == other.gpu &&
      _abis == other._abis &&
      _translatedAbis == other._translatedAbis &&
      _uiModes == other._uiModes &&
      chargeType == other.chargeType &&
      skinFile?.path == other.skinFile?.path
  }

  override fun hashCode(): Int {
    return Objects.hashCode(
      screen,
      environment,
      touchpad,
      hinge,
      _networking,
      _sensors,
      hasMic,
      sdCard,
      _cameras,
      keyboard,
      nav,
      ram,
      buttonType,
      _internalStorage,
      _removableStorage,
      cpu,
      gpu,
      _abis,
      _translatedAbis,
      _uiModes,
      chargeType,
      skinFile,
    )
  }

  override fun toString(): String {
    val sb = StringBuilder()
    sb.append("Hardware <screen=")
    sb.append(screen)
    sb.append(", environment=")
    sb.append(environment)
    sb.append(", touchpad=")
    sb.append(touchpad)
    sb.append(", networking=")
    sb.append(_networking)
    sb.append(", sensors=")
    sb.append(_sensors)
    sb.append(", mic=")
    sb.append(hasMic)
    sb.append(", sdCard=")
    sb.append(sdCard)
    sb.append(", cameras=")
    sb.append(_cameras)
    sb.append(", keyboard=")
    sb.append(keyboard)
    sb.append(", nav=")
    sb.append(nav)
    sb.append(", ram=")
    sb.append(ram)
    sb.append(", buttons=")
    sb.append(buttonType)
    sb.append(", internalStorage=")
    sb.append(_internalStorage)
    sb.append(", removableStorage=")
    sb.append(_removableStorage)
    sb.append(", cpu=")
    sb.append(cpu)
    sb.append(", gpu=")
    sb.append(gpu)
    sb.append(", abis=")
    sb.append(_abis)
    sb.append(", translatedAbis=")
    sb.append(_translatedAbis)
    sb.append(", uiModes=")
    sb.append(_uiModes)
    sb.append(", pluggedIn=")
    sb.append(chargeType)
    sb.append(", skinFile=")
    sb.append(skinFile)
    sb.append(">")
    return sb.toString()
  }
}
