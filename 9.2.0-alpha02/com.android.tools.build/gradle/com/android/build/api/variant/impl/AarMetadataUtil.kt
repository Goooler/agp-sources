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

import com.android.build.api.dsl.CompileSdkVersion
import com.android.build.api.variant.AarMetadata
import com.android.build.gradle.internal.dsl.CompileSdkVersionImpl
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.AarMetadataTask.Companion.DEFAULT_MIN_AGP_VERSION
import com.android.build.gradle.internal.tasks.AarMetadataTask.Companion.DEFAULT_MIN_COMPILE_SDK_EXTENSION
import com.android.build.gradle.internal.tasks.AarMetadataTask.Companion.DEFAULT_MIN_COMPILE_SDK_MINOR
import com.android.build.gradle.internal.tasks.AarMetadataTask.Companion.DEFAULT_MIN_COMPILE_SDK_VERSION
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.utils.parseTargetHash
import com.android.build.gradle.options.BooleanOption

object AarMetadataUtil {

  fun createAndInitialize(
    dslServices: DslServices,
    services: TaskCreationServices,
    global: GlobalTaskCreationConfig,
    minCompileSdkFromDsl: CompileSdkVersion?,
    minAgpVersionFromDsl: String?,
  ): AarMetadata {
    val minCompileSdkVersion =
      minCompileSdkFromDsl
        ?: parseTargetHash(global.compileSdkHashString).takeIf {
          services.projectOptions[BooleanOption.DEFAULT_MIN_COMPILE_SDK_IN_AAR_METADATA]
        }
        ?: CompileSdkVersionImpl(apiLevel = DEFAULT_MIN_COMPILE_SDK_VERSION, sdkExtension = DEFAULT_MIN_COMPILE_SDK_EXTENSION)
    return dslServices.newInstance(AarMetadataImpl::class.java, dslServices).also {
      it.minAgpVersion.set(minAgpVersionFromDsl ?: DEFAULT_MIN_AGP_VERSION)
      it.minCompileSdk.set(minCompileSdkVersion.apiLevel ?: DEFAULT_MIN_COMPILE_SDK_VERSION)
      it.minCompileSdkMinor.set(minCompileSdkVersion.minorApiLevel ?: DEFAULT_MIN_COMPILE_SDK_MINOR)
      it.minCompileSdkExtension.set(minCompileSdkVersion.sdkExtension ?: DEFAULT_MIN_COMPILE_SDK_EXTENSION)
      it.minCompileSdkVersion.convention(
        it.minCompileSdk
          .zip(it.minCompileSdkMinor) { api, minor -> api to minor }
          .zip(it.minCompileSdkExtension) { majorMinor, extension ->
            CompileSdkVersionImpl(
              apiLevel = majorMinor.first,
              minorApiLevel = majorMinor.second.takeIf { it >= 0 },
              sdkExtension = extension,
            )
          }
      )
    }
  }
}
