/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.api.dsl.AarMetadata
import com.android.build.api.dsl.CompileSdkVersion
import com.android.build.api.dsl.MinCompileSdkSpec
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject
import org.gradle.api.Action

/** DSL object for configuring AAR metadata. */
abstract class AarMetadataImpl @Inject constructor(val dslServices: DslServices) : AarMetadata {

  protected abstract var _compileSdk: CompileSdkVersion

  private val compileSdkDelegate =
    CompileSdkDelegate(
      getCompileSdk = { _compileSdk },
      setCompileSdk = { it?.let { _compileSdk = it } },
      issueReporter = dslServices.issueReporter,
      dslServices = dslServices,
    )

  val minCompileSdkVersion: CompileSdkVersion
    get() = _compileSdk

  override var minCompileSdk: Int?
    get() = compileSdkDelegate.compileSdk
    set(value) {
      compileSdkDelegate.compileSdk = value
    }

  override var minCompileSdkExtension: Int?
    get() = compileSdkDelegate.compileSdkExtension
    set(value) {
      compileSdkDelegate.compileSdkExtension = value
    }

  override fun minCompileSdk(action: MinCompileSdkSpec.() -> Unit) {
    val spec = dslServices.newInstance(MinCompileSdkSpecImpl::class.java, dslServices)
    spec.action()
    compileSdkDelegate.compileSdk { version = spec._version }
  }

  open fun minCompileSdk(action: Action<MinCompileSdkSpec>) {
    val spec = dslServices.newInstance(MinCompileSdkSpecImpl::class.java, dslServices)
    action.execute(spec)
    compileSdkDelegate.compileSdk { version = spec._version }
  }

  // TODO(b/421964815): remove the support for groovy space assignment (e.g `minCompileSdk 24`).
  @Deprecated("To be removed after Gradle drops space assignment support. Use `minCompileSdk {}` instead.")
  open fun minCompileSdk(version: Int) {
    minCompileSdk = version
  }

  // TODO(b/421964815): remove the support for groovy space assignment (e.g `minCompileSdkExtension 2`).
  @Deprecated("To be removed after Gradle drops space assignment support. Use `minCompileSdk {}` instead.")
  open fun minCompileSdkExtension(extension: Int) {
    minCompileSdkExtension = extension
  }
}
