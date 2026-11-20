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
package com.android.build.gradle.internal.core

import com.android.build.api.dsl.AarMetadata
import com.android.build.api.dsl.CompileSdkVersion
import com.android.build.gradle.internal.dsl.AarMetadataImpl

/** Used to merge multiple instances of [AarMetadata] together. */
class MergedAarMetadata : MergedOptions<AarMetadata> {

  var minCompileSdkVersion: CompileSdkVersion? = null
  var minAgpVersion: String? = null

  override fun reset() {
    minCompileSdkVersion = null
    minAgpVersion = null
  }

  override fun append(option: AarMetadata) {
    if (option !is AarMetadataImpl) throw IllegalArgumentException("${AarMetadataImpl::class.java.name} expected.")
    option.minCompileSdkVersion?.let { minCompileSdkVersion = it }
    option.minAgpVersion?.let { minAgpVersion = it }
  }

  fun append(option: MergedAarMetadata) {
    option.minCompileSdkVersion?.let { minCompileSdkVersion = it }
    option.minAgpVersion?.let { minAgpVersion = it }
  }
}
