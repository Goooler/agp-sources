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

package com.android.build.api.dsl

import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition

/**
 * Specifies the minimum compileSdk this Gradle project imposes on projects that depend on it, both within the same build and when
 * publishing AARs.
 *
 * This defaults to the compile SDK of your project. If you set this value to lower than your compileSdk, you should take care to only use
 * Android APIs from that lower API level to not create problems (such as with dexing and resource references) to projects that depend on
 * your library.
 */
interface MinCompileSdkSpec {
  /** The min compile SDK version set for this project. */
  @get:HiddenInDefinition @set:HiddenInDefinition var version: CompileSdkVersion?

  /**
   * To set min compile SDK version with a released API level, use this function to compute the [CompileSdkVersion] and assign it to
   * [MinCompileSdkSpec.version] property. You can also set minor API level and SDK extension level via [CompileSdkReleaseSpec] block.
   */
  @HiddenInDefinition fun release(version: Int, action: (CompileSdkReleaseSpec.() -> Unit)): CompileSdkVersion

  /**
   * To set min compile SDK version with a released API level, use this function to compute the [CompileSdkVersion] and assign it to
   * [MinCompileSdkSpec.version] property.
   */
  @HiddenInDefinition fun release(version: Int): CompileSdkVersion
}
