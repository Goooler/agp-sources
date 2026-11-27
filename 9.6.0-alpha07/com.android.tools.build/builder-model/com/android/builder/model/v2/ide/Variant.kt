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
package com.android.builder.model.v2.ide

import com.android.builder.model.v2.AndroidModel
import com.android.builder.model.v2.models.BaseTestSuiteSourceIdentity
import com.android.builder.model.v2.models.VariantDependencies
import java.io.File

/** A build Variant. */
interface Variant : AndroidModel {
  /** The name of the variant. */
  val name: String

  /** The display name for the variant. */
  val displayName: String

  /** The main artifact for this variant. */
  val mainArtifact: AndroidArtifact

  /** The AndroidTest artifact for this variant, if applicable. */
  @Deprecated("Contained in deviceTestArtifacts") val androidTestArtifact: AndroidArtifact?

  /** The device test components for this variant, e.g., androidTest */
  val deviceTestArtifacts: Map<String, AndroidArtifact>

  /** The Unit Test artifact for this variant, if applicable. */
  @Deprecated("Contained in hostTestArtifacts") val unitTestArtifact: JavaArtifact?

  /** The host test components for this variant, e.g., unitTest */
  val hostTestArtifacts: Map<String, JavaArtifact>

  // TODO(b/575647801): remove once MINIMUM_MODEL_CONSUMER excludes Studio versions that read this.
  /**
   * The test suite artifacts for this variant, keyed by test suite name.
   *
   * Always empty since model producer version 25.0; use [testSuiteHostArtifacts] and [testSuiteDeviceArtifacts] instead. Kept for
   * compatibility with older versions of Android Studio.
   */
  @Deprecated("Always empty; use testSuiteHostArtifacts and testSuiteDeviceArtifacts")
  val testSuiteArtifacts: Map<String, TestSuiteArtifact>

  /**
   * The host jar artifacts of the test suites applied to this variant, keyed by the source set identifier (for example
   * `debugMixedSuiteTest` for the `mixedSuiteTest` source set on variant `debug`).
   *
   * Uses the same identifier as [BaseTestSuiteSourceIdentity.name] in [VariantDependencies.testSuiteArtifacts] so dependencies can be
   * looked up directly without string construction.
   */
  val testSuiteHostArtifacts: Map<String, TestSuiteHostArtifact>

  /**
   * The test APK artifacts of the test suites applied to this variant, keyed by the source set identifier (for example
   * `debugMixedSuiteAndroidTest` for the `mixedSuiteAndroidTest` source set on variant `debug`).
   *
   * Uses the same identifier as [BaseTestSuiteSourceIdentity.name] in [VariantDependencies.testSuiteArtifacts] so dependencies can be
   * looked up directly without string construction.
   */
  val testSuiteDeviceArtifacts: Map<String, TestSuiteDeviceArtifact>

  /** The TestFixtures artifact for this variant, if applicable. */
  val testFixturesArtifact: AndroidArtifact?

  /**
   * For standalone test plugins: information about the tested project.
   *
   * For other plugin types, this is null
   */
  val testedTargetVariant: TestedTargetVariant?

  /** True when "android.experimental.self-instrumenting" experimental flag is enabled. */
  val runTestInSeparateProcess: Boolean

  /**
   * Whether the variant is instant app compatible.
   *
   * Only application modules and dynamic feature modules will set this property.
   */
  val isInstantAppCompatible: Boolean

  /** Desugared methods supported by D8 and core library desugaring. */
  val desugaredMethods: List<File>

  /** Experimental flags set on a variant. */
  val experimentalProperties: Map<String, String>
}
