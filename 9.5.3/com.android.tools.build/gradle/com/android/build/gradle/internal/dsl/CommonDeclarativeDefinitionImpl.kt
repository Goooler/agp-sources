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

package com.android.build.gradle.internal.dsl

import com.android.SdkConstants
import com.android.build.api.dsl.CommonDeclarativeDefinition
import com.android.build.api.dsl.DefaultConfig
import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.core.LibraryRequest
import com.android.builder.core.ToolsRevisionUtils
import java.io.File
import org.gradle.api.Incubating

abstract class CommonDeclarativeDefinitionImpl<
  BuildTypeT : com.android.build.api.dsl.BuildType,
  DefaultConfigT : DefaultConfig,
  ProductFlavorT : com.android.build.api.dsl.ProductFlavor,
>(protected val dslServices: DslServices) : CommonDeclarativeDefinition {

  override val flavorDimensions: MutableList<String> = mutableListOf()

  override var resourcePrefix: String? = null

  override var ndkVersion: String = SdkConstants.NDK_DEFAULT_VERSION

  override var ndkPath: String? = null

  override var buildToolsVersion: String = ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION.toString()

  override fun useLibrary(name: String) {
    useLibrary(name, true)
  }

  override fun useLibrary(name: String, required: Boolean) {
    libraryRequests.add(LibraryRequest(name, required))
  }

  override var compileSdk: Int? = null

  override var namespace: String? = null

  override fun getDefaultProguardFile(name: String): File {
    return ProguardFiles.getDefaultProguardFile(name, dslServices.buildDirectory)
  }

  override var enableKotlin: Boolean = true

  @get:Incubating override val experimentalProperties: MutableMap<String, Any> = mutableMapOf()

  protected val libraryRequests: MutableList<LibraryRequest> = mutableListOf()
}
