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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.CompileSdkReleaseSpec
import com.android.build.api.dsl.CompileSdkVersion
import com.android.build.api.dsl.MinCompileSdkSpec
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject
import org.gradle.api.Action

abstract class MinCompileSdkSpecImpl @Inject constructor(val dslService: DslServices) : MinCompileSdkSpec {

  abstract var _version: CompileSdkVersion?

  override var version: CompileSdkVersion?
    get() = throw UnsupportedOperationException("Reading version is not supported for MinCompileSdkSpec")
    set(value) {
      _version = value
    }

  override fun release(version: Int, action: (CompileSdkReleaseSpec.() -> Unit)): CompileSdkVersion {
    val compileSdkRelease = createCompileSdkReleaseSpec()
    action.invoke(compileSdkRelease)

    return CompileSdkVersionImpl(
      apiLevel = version,
      minorApiLevel = compileSdkRelease.minorApiLevel,
      sdkExtension = compileSdkRelease.sdkExtension,
    )
  }

  override fun release(version: Int): CompileSdkVersion {
    return CompileSdkVersionImpl(apiLevel = version)
  }

  fun release(version: Int, action: Action<CompileSdkReleaseSpec>): CompileSdkVersion {
    val compileSdkRelease = createCompileSdkReleaseSpec()
    action.execute(compileSdkRelease)

    return CompileSdkVersionImpl(
      apiLevel = version,
      minorApiLevel = compileSdkRelease.minorApiLevel,
      sdkExtension = compileSdkRelease.sdkExtension,
    )
  }

  private fun createCompileSdkReleaseSpec(): CompileSdkReleaseSpec {
    return dslService.newDecoratedInstance(CompileSdkReleaseSpecImpl::class.java, dslService)
  }
}
