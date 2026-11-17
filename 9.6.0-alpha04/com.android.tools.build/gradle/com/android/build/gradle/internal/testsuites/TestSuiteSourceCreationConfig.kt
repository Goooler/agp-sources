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

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.FD_AIDL
import com.android.SdkConstants.FD_ASSETS
import com.android.SdkConstants.FD_JAVA
import com.android.SdkConstants.FD_JAVA_RES
import com.android.SdkConstants.FD_RES
import com.android.build.api.dsl.AgpTestSuite
import com.android.build.api.dsl.AgpTestSuiteDependencies
import com.android.build.api.variant.TestSuiteSourceSet
import com.android.build.gradle.internal.dsl.AgpTestSuiteImpl
import com.android.build.gradle.internal.dsl.TestSuiteAssetsSpecImpl
import com.android.build.gradle.internal.dsl.TestSuiteHostJarSpecImpl
import com.android.build.gradle.internal.dsl.TestSuiteTestApkSpecImpl
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.errors.IssueReporter
import java.io.File
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

internal val DEFAULT_RESERVED_SUITE_NAMES: Set<String> = setOf("main", "test", "unitTest", "androidTest", "testFixtures")

/** Source folders and files under `src/<suite>/` that single-source `hostJar` or `testApk` suites read. */
private val SINGLE_SOURCE_SUITE_ENTRIES: Set<String> =
  setOf(FD_JAVA, "kotlin", FD_RES, FD_JAVA_RES, FD_ASSETS, FD_AIDL, ANDROID_MANIFEST_XML)

private fun TestSuiteSourceCreationConfig.mixedSourceDirName(): String? =
  when (this) {
    is TestSuiteHostJarSpecImpl -> "test"
    is TestSuiteTestApkSpecImpl -> "androidTest"
    is TestSuiteAssetsSpecImpl -> "assetsTest"
    else -> null
  }

private fun File.isHiddenEntry(): Boolean = isHidden || name.startsWith(".")

private fun isMisplacedMixedSuiteEntry(entry: File, expectedDirNames: Set<String>, hasAssets: Boolean): Boolean {
  if (entry.isHiddenEntry()) return false
  if (entry.isDirectory && entry.name in expectedDirNames) return false
  val isDocFile =
    entry.isFile &&
      (entry.name == "OWNERS" || entry.name.equals("README", ignoreCase = true) || entry.name.startsWith("README.", ignoreCase = true))
  val matchesSingleSourceLayout = entry.name in SINGLE_SOURCE_SUITE_ENTRIES || (hasAssets && !isDocFile)
  if (!matchesSingleSourceLayout) return false
  return entry.isFile || entry.walkTopDown().onEnter { !it.isHiddenEntry() }.any { it.isFile && !it.isHiddenEntry() }
}

/**
 * Validates test suite names, generated mixed source set names (`<suite>Test`, `<suite>AndroidTest`, `<suite>Assets`), and mixed source
 * directory layout.
 *
 * @throws InvalidUserDataException if a reserved name or source set name collision is detected.
 */
internal fun validateTestSuiteSourceSetNames(
  suites: NamedDomainObjectContainer<AgpTestSuite>,
  issueReporter: IssueReporter,
  reservedNames: Set<String> = DEFAULT_RESERVED_SUITE_NAMES,
  projectDirectory: File? = null,
) {
  if (suites.isEmpty()) return
  val suiteNames = suites.names
  val mixedSourceSetToSuite = mutableMapOf<String, String>()

  for (suite in suites) {
    if (suite.name in reservedNames) {
      throw InvalidUserDataException(
        "Test suite name '${suite.name}' is reserved for Android source sets or built-in test components. " +
          "Choose a distinct name for the test suite."
      )
    }

    if (suite !is AgpTestSuiteImpl) continue
    val sources = suite.getSourceContainers()
    if (sources.size <= 1) continue

    for (source in sources) {
      val sourceSetName = source.sourceSetName(isMixed = true)
      if (sourceSetName in reservedNames) {
        throw InvalidUserDataException(
          "Source set '$sourceSetName' of mixed test suite '${suite.name}' collides with reserved source set '$sourceSetName'. " +
            "Rename test suite '${suite.name}' to avoid source set and task name collisions."
        )
      }
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

    if (projectDirectory != null) {
      val expectedDirNames = sources.mapNotNullTo(linkedSetOf()) { it.mixedSourceDirName() }
      val hasAssets = sources.any { it is TestSuiteAssetsSpecImpl }
      val misplacedEntries =
        File(projectDirectory, "src/${suite.name}")
          .listFiles()
          .orEmpty()
          .filter { entry -> isMisplacedMixedSuiteEntry(entry, expectedDirNames, hasAssets) }
          .map { it.name }
          .sorted()
      if (misplacedEntries.isNotEmpty()) {
        val expectedDirs = expectedDirNames.map { "'src/${suite.name}/$it/'" }
        issueReporter.reportError(
          IssueReporter.Type.GENERIC,
          "Mixed test suite '${suite.name}' declares multiple source types, so files directly under 'src/${suite.name}/' " +
            "(found: ${misplacedEntries.joinToString()}) are ignored. Move them into ${expectedDirs.joinToString(" or ")}.",
        )
      }
    }
  }
}
