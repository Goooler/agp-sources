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

package com.android.build.api.variant.impl

import com.android.build.api.dsl.MinCompileSdkSpec
import com.android.build.api.variant.AarMetadata
import com.android.build.gradle.internal.dsl.CompileSdkDelegate
import com.android.build.gradle.internal.dsl.MinCompileSdkSpecImpl
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject
import org.gradle.api.provider.Property

abstract class AarMetadataImpl @Inject constructor(private val dslServices: DslServices) : AarMetadata {

  internal val compileSdkDelegate =
    CompileSdkDelegate(
      getCompileSdk = { minCompileSdkVersion.orNull },
      setCompileSdk = {
        if (minCompileSdkVersion.orNull != it) {
          minCompileSdkVersion.set(it)
        }
        if (minCompileSdk.orNull != it?.apiLevel) {
          minCompileSdk.set(it?.apiLevel)
        }
        if (minCompileSdkExtension.orNull != it?.sdkExtension) {
          minCompileSdkExtension.set(it?.sdkExtension)
        }
        if (minCompileSdkMinor.orNull != it?.minorApiLevel) {
          minCompileSdkMinor.set(it?.minorApiLevel)
        }
      },
      issueReporter = dslServices.issueReporter,
      dslServices = dslServices,
    )

  abstract val minCompileSdkMinor: Property<Int>

  override fun minCompileSdk(action: MinCompileSdkSpec.() -> Unit) {
    val spec = dslServices.newInstance(MinCompileSdkSpecImpl::class.java, dslServices)
    action.invoke(spec)
    compileSdkDelegate.compileSdk { version = spec._version }
  }
}
