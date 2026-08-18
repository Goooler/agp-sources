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

/**
 * A test suite to run Compose screenshot tests.
 *
 * A [ScreenshotTestSuite] configures the execution environment and inputs required
 * to render Compose previews and execute screenshot tests.
 *
 * Example usage:
 * ```
 * android {
 *     testOptions {
 *         screenshotTests {
 *             create("myScreenshotTest") {
 *                 engineVersion = "1.0.0"
 *                 targetVariants.add("debug")
 *                 dependencies {
 *                     implementation(project(":my-test-helpers"))
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
@Suppress("UnstableApiUsage")
@Incubating
interface ScreenshotTestSuite {

  /**
   * The version of the screenshot validation engine.
   *
   * Specifies the version of the `com.android.tools.screenshot:screenshot-validation-junit-engine` artifact (published on the Google Maven
   * repository) to use for layout rendering and screenshot validation.
   *
   * This property is mandatory. A valid version must be provided; otherwise, a Gradle sync error will be reported.
   */
  @get:Incubating @set:Incubating var engineVersion: String?

  /** Defines which variants this test suite targets. */
  @get:Incubating val targetVariants: MutableList<String>

  /**
   * Configures the dependencies for this test suite.
   *
   * Dependencies are added to the test suite's host (JVM) execution classpath. This enables the screenshot tests to access custom test
   * helpers and preview providers during rendering.
   */
  @Incubating fun dependencies(action: AgpTestSuiteDependencies.() -> Unit)
  }
