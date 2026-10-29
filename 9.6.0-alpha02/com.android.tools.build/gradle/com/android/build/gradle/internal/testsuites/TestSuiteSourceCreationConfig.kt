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

package com.android.build.gradle.internal.testsuites

import com.android.build.api.dsl.AgpTestSuite
import com.android.build.api.dsl.AgpTestSuiteDependencies
import com.android.build.api.variant.TestSuiteSourceSet
import com.android.build.gradle.internal.dsl.AgpTestSuiteImpl
import com.android.build.gradle.internal.services.VariantServices
import org.gradle.api.InvalidUserDataException
import org.gradle.api.NamedDomainObjectContainer

/**
 * Single set of related test sources that will be processed and packaged together.
 *
 * For instance, this can reference a host test folder, or a device test set of folders, or at the simplest level, an asset test folder.
 *
 * A test suite can have 1 to many instances of [TestSuiteSourceCreationConfig]. Each instance have individual dependencies, they will be
 * compiled and packaged (if needed) separately before being provided to the configured junit engines.
 */
interface TestSuiteSourceCreationConfig {

  val name: String

  val dependencies: AgpTestSuiteDependencies

  fun sourceSetName(isMixed: Boolean): String

  fun createTestSuiteSourceSet(
    variantServices: VariantServices,
    javaEnabled: Boolean,
    kotlinEnabled: Boolean,
    isMixed: Boolean,
  ): TestSuiteSourceSet
}

/**
 * Validates that no generated mixed test suite source set name (`<suite>Test`, `<suite>AndroidTest`, `<suite>Assets`) collides with another
 * test suite name or another mixed test suite's source set name.
 *
 * @throws InvalidUserDataException if a naming collision is detected.
 */
internal fun validateTestSuiteSourceSetNames(suites: NamedDomainObjectContainer<AgpTestSuite>) {
  if (suites.size <= 1) return
  val suiteNames = suites.names
  val mixedSourceSetToSuite = mutableMapOf<String, String>()

  for (suite in suites) {
    if (suite !is AgpTestSuiteImpl) continue
    val sources = suite.getSourceContainers()
    if (sources.size <= 1) continue
    for (source in sources) {
      val sourceSetName = source.sourceSetName(isMixed = true)
      if (sourceSetName in suiteNames) {
        throw InvalidUserDataException(
          "Test suite '$sourceSetName' collides with source set '$sourceSetName' of mixed test suite '${suite.name}'. " +
            "Please rename one of the test suites to avoid source set and task name collisions."
        )
      }

      val existingSuiteName = mixedSourceSetToSuite.putIfAbsent(sourceSetName, suite.name)
      if (existingSuiteName != null) {
        throw InvalidUserDataException(
          "Source set '$sourceSetName' of mixed test suite '${suite.name}' collides with source set '$sourceSetName' " +
            "of mixed test suite '$existingSuiteName'. Please rename one of the test suites to avoid source set and task name collisions."
        )
      }
    }
  }
}
