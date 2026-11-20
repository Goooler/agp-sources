/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.sdklib.util

import kotlin.jvm.optionals.getOrNull
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

/** Mechanism for calling the [ProcessHandle.of] method that allows tests to inject their own substitute. */
object ProcessHandleProvider {

  private var factory: Factory? = null

  fun getProcessHandle(pid: Long): ProcessHandle? {
    return factory?.getProcessHandle(pid) ?: ProcessHandle.of(pid).getOrNull()
  }

  @TestOnly
  fun overrideForTest(override: Factory?) {
    factory = override
  }

  @VisibleForTesting
  interface Factory {
    fun getProcessHandle(pid: Long): ProcessHandle?
  }
}
