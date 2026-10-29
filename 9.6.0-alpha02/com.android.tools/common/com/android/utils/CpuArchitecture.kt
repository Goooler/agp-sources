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
        Environment.ensureInitialized()
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
