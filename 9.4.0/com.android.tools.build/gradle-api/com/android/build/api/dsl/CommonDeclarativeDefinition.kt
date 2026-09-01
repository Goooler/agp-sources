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

package com.android.build.api.dsl

import java.io.File
import org.gradle.api.Incubating

@Incubating
/** @suppress */
interface CommonDeclarativeDefinition {
  /** Specifies the names of product flavor dimensions for this project. */
  @get:Incubating val flavorDimensions: MutableList<String>

  /** Specifies this project's resource prefix to Android Studio for editor features. */
  @get:Incubating @set:Incubating var resourcePrefix: String?

  /** Requires the specified NDK version to be used. */
  @get:Incubating @set:Incubating var ndkVersion: String

  /** Requires the specified path to NDK be used. */
  @get:Incubating @set:Incubating var ndkPath: String?

  /** Specifies the version of the SDK Build Tools to use. */
  @get:Incubating @set:Incubating var buildToolsVersion: String

  /** Includes the specified library to the classpath. */
  @Incubating fun useLibrary(name: String)

  @Incubating fun useLibrary(name: String, required: Boolean)

  /** Specifies the API level to compile your project against. */
  @get:Incubating @set:Incubating var compileSdk: Int?

  /** The namespace of the generated R and BuildConfig classes. */
  @get:Incubating @set:Incubating var namespace: String?

  @Incubating fun getDefaultProguardFile(name: String): File

  /** Enables the compilation of Kotlin sources. */
  @get:Incubating @set:Incubating var enableKotlin: Boolean

  /** Additional per module experimental properties. */
  @get:Incubating val experimentalProperties: MutableMap<String, Any>
}
