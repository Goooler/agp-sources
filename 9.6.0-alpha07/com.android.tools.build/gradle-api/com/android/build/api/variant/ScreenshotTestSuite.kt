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

package com.android.build.api.variant

import org.gradle.api.Incubating
import org.gradle.api.provider.Provider

/**
 * Model for screenshot test suites in the Variant API.
 *
 * This object is accessible on subtypes of [Variant] that implement [HasTestSuites], via [HasTestSuites.suites].
 */
@Incubating
interface ScreenshotTestSuite : TestSuite {

  /**
   * The version of the `com.android.tools.screenshot:screenshot-validation-junit-engine` artifact to use for layout rendering and
   * screenshot validation.
   */
  @get:Incubating val engineVersion: Provider<String>

  /**
   * The maximum allowed percentage difference between the reference image and the rendered image for a screenshot test to pass.
   *
   * Specified as a floating-point value between `0.0f` (exact match required) and `1.0f` (100% difference allowed). For example, a value of
   * `0.0001f` represents a 0.01% difference threshold.
   *
   * Default value is `0.0f` (exact match required).
   */
  @get:Incubating val imageDifferenceThreshold: Provider<Float>
}
