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

package com.android.builder.model.v2.ide

// TODO(b/575647801): remove once MINIMUM_MODEL_CONSUMER excludes Studio versions that read this.
/**
 * Information for a test suite in a variant, such as the JUnit engines configuration and the test targets.
 *
 * Only reachable through [TestSuiteArtifact.testInfo]. Since model producer version 25.0, [Variant.testSuiteArtifacts] is always empty, so
 * this is never returned. The JUnit engine information is available from [TestSuite.junitEngineInfo], and the test targets from
 * [BasicVariant.testSuiteVariantTargets].
 *
 * @since 8.12
 */
@Deprecated("Unused since model producer version 25.0; use TestSuite.junitEngineInfo and BasicVariant.testSuiteVariantTargets")
interface TestSuiteTestInfo {
  /** Information for the junit engines configured for running the test suite. */
  val junitInfo: JUnitEngineInfo

  /**
   * Information for the test suite's targets.
   *
   * @return map of [TestSuiteTarget] indexed by their [TestSuiteTarget.name]
   */
  val targets: Map<String, TestSuiteTarget>
}
