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

/** Identifies the operating system based on the [Environment], which may be faked in tests. */
object OperatingSystem {
  val name: String
    get() = Environment.instance.getSystemProperty(Environment.SystemProperty.OS_NAME) ?: "unknown"

  fun isMac(): Boolean = name.startsWith("mac", ignoreCase = true)

  fun isLinux(): Boolean = name.startsWith("linux", ignoreCase = true)

  fun isWindows(): Boolean = name.startsWith("win", ignoreCase = true)
}
