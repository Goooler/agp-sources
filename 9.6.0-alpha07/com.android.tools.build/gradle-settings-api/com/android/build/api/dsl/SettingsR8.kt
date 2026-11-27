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

import org.gradle.api.Incubating

/** DSL Options for the R8 compiler at the settings level. */
@Incubating
interface SettingsR8 {
  /**
   * The version of the R8 compiler to use, resolved from the project's configured repositories as `com.android.tools:r8:<version>`.
   *
   * If set, overrides the default R8 version shipped with the Android Gradle Plugin (and takes precedence over the
   * `android.r8.versionOverride` Gradle property). The specified version must not be older than the R8 version shipped with this version of
   * the Android Gradle Plugin.
   */
  @get:Incubating @set:Incubating var version: String?
}
