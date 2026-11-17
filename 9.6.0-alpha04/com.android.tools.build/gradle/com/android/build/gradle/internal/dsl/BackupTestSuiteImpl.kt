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
import com.android.build.api.dsl.TestTaskContext
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.testsuites.backup.BackupTestSuiteTaskConfigurator
import com.android.build.gradle.tasks.TestSuiteTestTask
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.ProviderFactory

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
 * 4. Wires the 6 required input parameters: [AgpTestSuiteInputParameters.TEST_CLASSES], [AgpTestSuiteInputParameters.TEST_CLASSPATH],
 *    [AgpTestSuiteInputParameters.MAIN_CLASSES], [AgpTestSuiteInputParameters.MAIN_CLASSPATH], [AgpTestSuiteInputParameters.TEST_APKS], and
 *    [AgpTestSuiteInputParameters.TESTED_APKS], along with the configured backup test library version input property
 *    ([TEST_LIBRARY_VERSION_PROPERTY]).
 * 5. Automatically injects `androidx.test.backup:backup-host` onto the host test execution classpath and `androidx.test.backup:backup` onto
 *    the on-device test APK classpath using [BackupTestSuite.backupTestLibraryVersion] with a default fallback to [DEFAULT_BACKUP_VERSION].
 * 6. Delegates task configuration (JVM property forwarding, manifest backup rule extraction, and analytics collection) to
 *    [BackupTestSuiteTaskConfigurator].
 * 7. Delegates custom user-provided [hostJar] and [testApk] configuration blocks to the underlying source containers.
 */
open class BackupAgpTestSuiteImpl
@Inject
constructor(
  private val backupSuite: BackupTestSuiteImpl,
  dslServices: DslServices,
  private val dependencyHandler: DependencyHandler,
  private val providers: ProviderFactory,
) : AgpTestSuiteImpl(backupSuite.name, dslServices, false) {

  override val targetVariants: MutableList<String>
    get() = backupSuite.targetVariants

  /** Without [targetVariants], the suite tests the variants of the tested build type, like device tests do. */
  override fun targetsVariant(variant: ComponentIdentity, testBuildType: String?): Boolean =
    if (targetVariants.isEmpty()) testBuildType != null && variant.buildType == testBuildType
    else super.targetsVariant(variant, testBuildType)

  private val backupTaskConfigurator: BackupTestSuiteTaskConfigurator

  init {
    // Automatically register a "default" target for test execution
    targets.register("default")
    requiresUpdateTask = false

    // This suite is created before the user's DSL block runs, so the version must be read lazily.
    val libraryVersion = providers.provider { backupSuite.backupTestLibraryVersion?.takeIf { it.isNotBlank() } ?: DEFAULT_BACKUP_VERSION }

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
      addInputProperty(TEST_LIBRARY_VERSION_PROPERTY, libraryVersion)

      // Automatically inject engine dependencies for the JUnit Jupiter test engine
      enginesDependencies.add(dependencyHandler.create("org.junit.jupiter:junit-jupiter-engine:5.13.3"))
      enginesDependencies.add(dependencyHandler.create("org.junit.platform:junit-platform-launcher:1.13.3"))
    }

    hostJar {
      dependencies {
        // Hosts the JUnit 5 test extension and BackupRestoreDevice orchestration control loops.
        // Transitively pulls adblib, adblib-tools, and common published on GMaven via backup-host's POM.
        implementation.add(libraryVersion.map { dependencyHandler.create("androidx.test.backup:backup-host:$it") })
      }
      backupSuite.hostJarHandler = { action -> action(this) }
      backupSuite.hostJarActions.forEach { it(this) }
      backupSuite.hostJarActions.clear()
    }

    testApk {
      dependencies {
        // Contains on-device test helper actions (e.g. PutStorageAction, VerifyStorageAction) and BackupRestoreTestRunner.
        implementation.add(libraryVersion.map { dependencyHandler.create("androidx.test.backup:backup:$it") })
      }
      backupSuite.testApkHandler = { action -> action(this) }
      backupSuite.testApkActions.forEach { it(this) }
      backupSuite.testApkActions.clear()
    }

    backupTaskConfigurator = BackupTestSuiteTaskConfigurator(dslServices, providers, libraryVersion)
    configureTestTasks { context: TestTaskContext -> backupTaskConfigurator.configureTask(this, context) }
  }

  companion object {
    /** The JUnit Platform engine identifier for Jupiter tests. */
    const val BACKUP_TEST_ENGINE_ID = "junit-jupiter"

    /** Default fallback version for the AndroidX backup testing framework artifacts. */
    const val DEFAULT_BACKUP_VERSION = "1.0.0-alpha03"

    /** Engine input property recording the configured backup test library version for suite environment reporting. */
    const val TEST_LIBRARY_VERSION_PROPERTY = TestSuiteTestTask.KEY_INPUT_TEST_LIBRARY_VERSION
  }
}
