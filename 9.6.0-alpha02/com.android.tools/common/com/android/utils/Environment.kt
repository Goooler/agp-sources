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
package com.android.utils

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

private const val TRANSLATED = 1

// https://learn.microsoft.com/en-us/windows/win32/sysinfo/image-file-machine-constants
private const val IMAGE_FILE_MACHINE_ARM64 = 0xAA64

abstract class Environment {

  enum class EnvironmentVariable(val key: String) {
    HOST_TYPE("HOSTTYPE"),
    PROCESSOR_ARCHITEW6432("PROCESSOR_ARCHITEW6432"),
  }

  enum class SystemProperty(val key: String) {
    OS_ARCH("os.arch"),
    OS_NAME("os.name"),
  }

  abstract fun getVariable(name: EnvironmentVariable): String?

  open fun getSystemProperty(name: SystemProperty): String? = System.getProperty(name.key)

  /** Whether or not Rosetta is being run on a m1 device. See [computeIsRosetta] */
  open val isRosetta: Boolean
    get() = computeIsRosetta()

  /** Whether or not x86_64 is being emulated on Windows ARM64. See [computeIsWindowsArm64] */
  open val isWindowsArm64: Boolean
    get() = computeIsWindowsArm64()

  companion object {
    val SYSTEM: Environment =
      object : Environment() {
        override fun getVariable(name: EnvironmentVariable) = System.getenv(name.key)
      }

    var instance = SYSTEM
    var initialized = false

    @JvmStatic
    fun initialize(customEnvironment: Environment? = null) {
      initialized = true
      customEnvironment?.let { instance = it }
    }

    fun ensureInitialized() {
      if (!initialized) {
        // TODO(b/283002867): log info to let test environments know that the environment was not initialized.
        initialize()
      }
    }
  }
}

/**
 * Determines if the current process is being translated to ARM by Rosetta.
 *
 * Processes running under Rosetta translation return 1 when sysctlbyname is called with sysctl.proc_translated ref:
 * https://developer.apple.com/documentation/apple_silicon/about_the_rosetta_translation_environment
 *
 * Checking for Rosetta requires jna. The current version of jna (net.java.dev.jna:jna-5.60) will fail if running on an arm jvm. Only call
 * if we're in an x86_64 jvm.
 */
private fun computeIsRosetta(): Boolean {
  val clazz =
    try {
      @Suppress("UNCHECKED_CAST")
      Class.forName("com.sun.jna.platform.mac.SystemB") as? Class<Library> ?: return false
    } catch (e: ClassNotFoundException) {
      return false
    } catch (e: LinkageError) {
      return false
    }

  val instanceField =
    try {
      clazz.getField("INSTANCE")
    } catch (e: NoSuchFieldException) {
      return false
    }

  val instance =
    try {
      instanceField.get(null)
    } catch (e: IllegalArgumentException) {
      return false
    }

  val sysctlbyname =
    try {
      clazz.getMethod(
        "sysctlbyname",
        String::class.java,
        Pointer::class.java,
        IntByReference::class.java,
        Pointer::class.java,
        Int::class.java,
      )
    } catch (e: NoSuchMethodException) {
      return false
    }

  val memory = Memory(4)
  val retSize = IntByReference(4)

  val errorCode =
    try {
      sysctlbyname.invoke(instance, "sysctl.proc_translated", memory, retSize, null, 0)
    } catch (e: Exception) {
      return false
    }

  return errorCode == 0 && memory.getInt(0) == TRANSLATED
}

private fun computeIsWindowsArm64(): Boolean {
  try {
    // https://learn.microsoft.com/en-us/windows/arm/apps-on-arm-x86-emulation#detecting-emulation
    val pidHandle = Kernel32.INSTANCE.GetCurrentProcess()
    val lib = Native.load("kernel32", NativeIsWow64::class.java, W32APIOptions.DEFAULT_OPTIONS)
    val processMachine = WinDef.USHORTByReference()
    val nativeMachine = WinDef.USHORTByReference()
    val result = lib.IsWow64Process2(pidHandle, processMachine, nativeMachine)
    return result && (nativeMachine.value.toInt() == IMAGE_FILE_MACHINE_ARM64)
  } catch (_: Throwable) {
    return false
  }
}

private interface NativeIsWow64 : StdCallLibrary {
  fun IsWow64Process2(hProcess: WinNT.HANDLE, processMachine: WinDef.USHORTByReference, nativeMachine: WinDef.USHORTByReference): Boolean
}
