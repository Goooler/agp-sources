/*
 * Copyright (C) 2021 The Android Open Source Project
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

enum class CpuArchitecture {
  X86,
  X86_64,
  ARM,
  X86_ON_ARM,
  UNKNOWN;

  companion object {
    @JvmStatic
    val jvmArchitecture: CpuArchitecture
      get() {
        ensureInitialized()
        val arch = Environment.instance.getSystemProperty(Environment.SystemProperty.OS_ARCH)
        return architectureFromString(arch)
      }

    @JvmStatic
    val osArchitecture: CpuArchitecture
      get() {
        val jvmArchitecture = jvmArchitecture

        // When running the JVM in x86, then that means either we are running a 32 bit environment
        // under a 64 bit OS or a 32 bit environment on a 32 bit OS. This can't happen on mac, as
        // they always run in a 64 bit environment. So we need to take a look at Windows and Linux.
        if (jvmArchitecture == X86) {
          when {
            OperatingSystem.isWindows() -> {
              // When WOW64 emulates a 32 bit environment under a 64 bit,
              // it sets PROCESSOR_ARCHITEW6432 to AMD64 or IA64.
              // Ref: http://msdn.microsoft.com/en-us/library/aa384274(v=vs.85).aspx
              val w6432 = Environment.instance.getVariable(Environment.EnvironmentVariable.PROCESSOR_ARCHITEW6432)
              if (w6432 != null && w6432.contains("64")) {
                return X86_64
              }
            }

            OperatingSystem.isLinux() -> {
              // Trying the obvious by querying HOSTTYPE, this works on debian and ubuntu
              val hostType = Environment.instance.getVariable(Environment.EnvironmentVariable.HOST_TYPE)
              return architectureFromString(hostType)
            }
          }
        }

        // An x86_64 jvm running on an ARM chip will be translated to ARM using Rosetta on Mac or
        // WOW64 on Windows. In order to determine if running translated we need to use jna
        // (see [computeIsRosetta] and [computeIsWindowsArm64]).
        // Note: we check architecture first, as jna fails in an arm jvm.
        if (jvmArchitecture == X86_64) {
          if (
            (OperatingSystem.isMac() && Environment.instance.isRosetta) ||
              (OperatingSystem.isWindows() && Environment.instance.isWindowsArm64)
          ) {
            return X86_ON_ARM
          }
        }

        return jvmArchitecture
      }
  }
}

/** Identifies the operating system based on the [Environment], which may be faked in tests. */
object OperatingSystem {
  val name: String
    get() = Environment.instance.getSystemProperty(Environment.SystemProperty.OS_NAME) ?: "unknown"

  fun isMac(): Boolean = name.startsWith("mac", ignoreCase = true)

  fun isLinux(): Boolean = name.startsWith("linux", ignoreCase = true)

  fun isWindows(): Boolean = name.startsWith("win", ignoreCase = true)
}

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
  }
}

private fun ensureInitialized() {
  if (!Environment.initialized) {
    // TODO(b/283002867): log info to let test environments know that the environment was
    // not initialized.
    Environment.initialize()
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

/** Extracts the CpuArchitecture based on the provided architecture string. */
fun architectureFromString(cpuArchName: String?): CpuArchitecture =
  when {
    cpuArchName.isNullOrEmpty() -> CpuArchitecture.UNKNOWN
    cpuArchName.equals("x86_64", ignoreCase = true) -> CpuArchitecture.X86_64
    cpuArchName.equals("ia64", ignoreCase = true) -> CpuArchitecture.X86_64
    cpuArchName.equals("amd64", ignoreCase = true) -> CpuArchitecture.X86_64
    cpuArchName.equals("x86", ignoreCase = true) -> CpuArchitecture.X86
    cpuArchName.equals("aarch64", ignoreCase = true) -> CpuArchitecture.ARM
    cpuArchName.equals("arm64", ignoreCase = true) -> CpuArchitecture.ARM
    // Any variation of iX86 is x86 (i386, i486, etc.)
    cpuArchName.length == 4 && cpuArchName[0] == 'i' && cpuArchName.endsWith("86") -> CpuArchitecture.X86
    else -> CpuArchitecture.UNKNOWN
  }
