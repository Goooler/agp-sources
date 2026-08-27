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

import com.android.build.api.dsl.AgpTestSuiteInputParameters
import com.android.build.api.dsl.BackupTestSuite
import com.android.build.api.dsl.TestSuiteHostJarSpec
import com.android.build.api.dsl.TestSuiteTestApkSpec
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.DependencyHandler

/**
 * Concrete implementation of [BackupTestSuite] representing Automated Backup and Restore test suites.
 *
 * This class captures user-facing DSL configurations declared inside the `testOptions.backupTests { ... }` block, buffering configuration
 * actions for the host JVM test sources ([hostJar]) and on-device instrumentation test sources ([testApk]) until the underlying
 * [BackupAgpTestSuiteImpl] is instantiated.
 */
abstract class BackupTestSuiteImpl
@Inject
constructor(
  val name: String,
  dslServices: DslServices,
) : BackupTestSuite {

  abstract override var backupTestLibraryVersion: String?

  internal val hostJarActions = mutableListOf<TestSuiteHostJarSpec.() -> Unit>()
  internal val testApkActions = mutableListOf<TestSuiteTestApkSpec.() -> Unit>()
  internal var hostJarHandler: ((TestSuiteHostJarSpec.() -> Unit) -> Unit)? = null
  internal var testApkHandler: ((TestSuiteTestApkSpec.() -> Unit) -> Unit)? = null

  /**
   * Configures additional host-side (JVM) test dependencies and options.
   *
   * @param action Lambda to configure the [TestSuiteHostJarSpec].
   */
  override fun hostJar(action: TestSuiteHostJarSpec.() -> Unit) {
    val handler = hostJarHandler
    if (handler != null) {
      handler(action)
    } else {
      hostJarActions.add(action)
    }
  }

  /**
   * Configures additional host-side (JVM) test dependencies and options (Groovy DSL and Gradle Action support).
   *
   * @param action Action to configure the [TestSuiteHostJarSpec].
   */
  fun hostJar(action: Action<TestSuiteHostJarSpec>) {
    hostJar { action.execute(this) }
  }

  /**
   * Configures additional on-device (APK) test dependencies and options.
   *
   * @param action Lambda to configure the [TestSuiteTestApkSpec].
   */
  override fun testApk(action: TestSuiteTestApkSpec.() -> Unit) {
    val handler = testApkHandler
    if (handler != null) {
      handler(action)
    } else {
      testApkActions.add(action)
    }
  }

  /**
   * Configures additional on-device (APK) test dependencies and options (Groovy DSL and Gradle Action support).
   *
   * @param action Action to configure the [TestSuiteTestApkSpec].
   */
  fun testApk(action: Action<TestSuiteTestApkSpec>) {
    testApk { action.execute(this) }
  }
}

/**
 * Internal AGP test suite implementation for Automated Backup and Restore testing.
 *
 * Extends [AgpTestSuiteImpl] to integrate with AGP's task creation pipeline and test execution engine:
 * 1. Automatically registers the `"default"` test target.
 * 2. Sets [requiresUpdateTask] to `false` (backup tests are assertion-based, requiring no baseline image updates).
 * 3. Configures the JUnit 5 platform engine (`junit-jupiter`) and injects required engine dependencies.
 * 4. Wires all 6 required input parameters: [AgpTestSuiteInputParameters.TEST_CLASSES], [AgpTestSuiteInputParameters.TEST_CLASSPATH],
 *    [AgpTestSuiteInputParameters.MAIN_CLASSES], [AgpTestSuiteInputParameters.MAIN_CLASSPATH], [AgpTestSuiteInputParameters.TEST_APKS], and
 *    [AgpTestSuiteInputParameters.TESTED_APKS].
 * 5. Automatically injects `androidx.test.backup:backup-host` onto the host test execution classpath and `androidx.test.backup:backup` onto
 *    the on-device test APK classpath using [BackupTestSuite.backupTestLibraryVersion] with a default fallback to [DEFAULT_BACKUP_VERSION].
 * 6. Delegates custom user-provided [hostJar] and [testApk] configuration blocks to the underlying source containers.
 */
open class BackupAgpTestSuiteImpl
@Inject
constructor(
  private val backupSuite: BackupTestSuiteImpl,
  dslServices: DslServices,
  private val dependencyHandler: DependencyHandler,
) : AgpTestSuiteImpl(backupSuite.name, dslServices, true) {

  override val targetVariants: MutableList<String>
    get() = backupSuite.targetVariants

  init {
    // Automatically register a "default" target for test execution
    targets.register("default")
    requiresUpdateTask = false

    // Automatically populate default engines, engine dependencies, and inputs
    useJunitEngine {
      includeEngines.add(BACKUP_TEST_ENGINE_ID)
      inputs.addAll(
        listOf(
          AgpTestSuiteInputParameters.TEST_CLASSES,
          AgpTestSuiteInputParameters.TEST_CLASSPATH,
          AgpTestSuiteInputParameters.MAIN_CLASSES,
          AgpTestSuiteInputParameters.MAIN_CLASSPATH,
          AgpTestSuiteInputParameters.TEST_APKS,
          AgpTestSuiteInputParameters.TESTED_APKS,
        )
      )

      // Automatically inject engine dependencies for the JUnit Jupiter test engine
      enginesDependencies.add(dependencyHandler.create("org.junit.jupiter:junit-jupiter-engine:5.10.0"))
      enginesDependencies.add(dependencyHandler.create("org.junit.platform:junit-platform-launcher:1.10.0"))
    }

    // Lazy Configuration Listener: Automatically map and inject compile-time dependencies
    // directly on the standard Gradle configurations generated for the custom test suite!
    try {
      dslServices.configurations.configureEach { config ->
        if (config.name.startsWith(name, ignoreCase = true)) {
          config.withDependencies { deps ->
            val resolvedVersion = backupSuite.backupTestLibraryVersion ?: DEFAULT_BACKUP_VERSION

            if (config.name.contains("TestApk", ignoreCase = true)) {
              // Contains on-device test helper actions (e.g. PutStorageAction, VerifyStorageAction) and BackupRestoreTestRunner.
              deps.add(dependencyHandler.create("androidx.test.backup:backup:$resolvedVersion"))
            } else if (config.name.contains("HostJar", ignoreCase = true)) {
              // Hosts the JUnit 5 test extension and BackupRestoreDevice orchestration control loops.
              // Transitively pulls adblib, adblib-tools, and common published on GMaven via backup-host's POM.
              deps.add(dependencyHandler.create("androidx.test.backup:backup-host:$resolvedVersion"))
            }
          }
        }
      }
    } catch (_: UnsupportedOperationException) {
      // In Declarative Gradle Schema or restricted evaluation contexts, configurations may not be accessible.
    }

    hostJar {
      backupSuite.hostJarHandler = { action -> action(this) }
      backupSuite.hostJarActions.forEach { it(this) }
      backupSuite.hostJarActions.clear()
    }

    testApk {
      backupSuite.testApkHandler = { action -> action(this) }
      backupSuite.testApkActions.forEach { it(this) }
      backupSuite.testApkActions.clear()
    }
  }

  companion object {
    /** The JUnit Platform engine identifier for Jupiter tests. */
    const val BACKUP_TEST_ENGINE_ID = "junit-jupiter"

    /** Default fallback version for the AndroidX backup testing framework artifacts. */
    const val DEFAULT_BACKUP_VERSION = "1.0.0-alpha01"
  }
}
