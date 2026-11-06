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
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.test.recordBackupTestRun
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.TestSuiteTestTask
import com.google.wireless.android.sdk.stats.TestRun
import com.google.wireless.android.sdk.stats.TestRun.BackupTestRun.BackupFailureReason
import com.google.wireless.android.sdk.stats.TestRun.BackupTestRun.BackupTransportMode
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestKeyValueDataEvent
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestMetadataEvent
import org.gradle.api.tasks.testing.TestMetadataListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.process.CommandLineArgumentProvider

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
  private val providers: ProviderFactory,
) : AgpTestSuiteImpl(backupSuite.name, dslServices, false) {

  override val targetVariants: MutableList<String>
    get() = backupSuite.targetVariants

  /** Without [targetVariants], the suite tests the variants of the tested build type, like device tests do. */
  override fun targetsVariant(variant: ComponentIdentity, testBuildType: String?): Boolean =
    if (targetVariants.isEmpty()) testBuildType != null && variant.buildType == testBuildType
    else super.targetsVariant(variant, testBuildType)

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
      enginesDependencies.add(dependencyHandler.create("org.junit.jupiter:junit-jupiter-engine:5.13.3"))
      enginesDependencies.add(dependencyHandler.create("org.junit.platform:junit-platform-launcher:1.13.3"))
    }

    // This suite is created before the user's DSL block runs, so the version must be read lazily.
    val libraryVersion = providers.provider { backupSuite.backupTestLibraryVersion?.takeIf { it.isNotBlank() } ?: DEFAULT_BACKUP_VERSION }

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

    val analyticsServiceProvider =
      try {
        getBuildService(dslServices.buildServiceRegistry, AnalyticsService::class.java)
      } catch (_: Exception) {
        null
      }

    val deviceProperties = providers.systemPropertiesPrefixedBy(DEVICE_PROPERTY_PREFIX)

    val invocationType =
      if (dslServices.projectOptions[BooleanOption.IDE_INVOKED_FROM_IDE]) TestRun.TestInvocationType.ANDROID_STUDIO_THROUGH_GRADLE_TEST
      else TestRun.TestInvocationType.GRADLE_TEST

    configureTestTasks { context: TestTaskContext ->
      val task = this
      analyticsServiceProvider?.let { task.usesService(it) }
      val listener = BackupTestListener(analyticsServiceProvider, libraryVersion.get(), invocationType)
      task.addTestListener(listener)
      task.addTestMetadataListener(listener)
      (task as? TestSuiteTestTask)?.addNoDeviceListener(listener)
      // Gradle doesn't pass the build's system properties to test JVMs, so forward the ones that select devices.
      task.jvmArgumentProviders.add(DevicePropertiesArgumentProvider(deviceProperties))
      // The backup library tells why a backup, restore or check failed in the exception message, which Gradle's default SHORT
      // format leaves out of the console.
      task.testLogging { it.exceptionFormat = TestExceptionFormat.FULL }
      // Fail the run when JUnit finds tests that it can't run, such as a @Test method that returns a value, instead of skipping them with
      // a logged warning.
      task.systemProperty("junit.platform.discovery.issue.severity.critical", "WARNING")
      // Only the host tests run, so the device helpers of the test APK mustn't keep Gradle from skipping a suite without tests.
      task.testDefinitionDirs.setFrom()
      // Gradle skips a suite without tests (NO-SOURCE) without saying so in the console. It evaluates onlyIf before that check.
      val suiteName = context.suiteName
      task.onlyIf { suiteTask ->
        if ((suiteTask as Test).testClassesDirs.asFileTree.isEmpty) {
          suiteTask.logger.warn(
            "Backup test suite '$suiteName' has no tests. Add JUnit 5 tests to src/$suiteName/test/kotlin/ or src/$suiteName/test/java/."
          )
        }
        true
      }
    }
  }

  /**
   * Passes the backup library's device selection system properties, such as `androidx.test.backup.device.serial`, to the test JVM.
   *
   * The properties are read when the test task runs, so selecting other devices doesn't invalidate the configuration cache.
   */
  internal class DevicePropertiesArgumentProvider(@get:Input val deviceProperties: Provider<Map<String, String>>) :
    CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = deviceProperties.get().map { (name, value) -> "-D$name=$value" }
  }

  /**
   * Records each run of the suite in the analytics, with the transport, storage domain count and failure reason that the backup library
   * reports for its backups and restores, and runs that can't start because no device is available.
   *
   * For a run of several tests, it records the transport that most tests used, the largest storage domain count, and the reason of the
   * first failed test that the backup library reported an error for. Errors that a test recovers from, like a restore that it expects to
   * fail, don't count.
   */
  internal class BackupTestListener(
    private val analyticsServiceProvider: Provider<AnalyticsService>?,
    private val backupTestLibraryVersion: String,
    private val invocationType: TestRun.TestInvocationType,
  ) : TestListener, TestMetadataListener, TestSuiteTestTask.NoDeviceListener {
    private val transportModes = mutableListOf<BackupTransportMode>()
    private var storageDomainCount: Int? = null
    /** The reason of the last error that each test or test class reported, until a test takes it. */
    private val errorReasons = mutableMapOf<TestDescriptor, BackupFailureReason>()
    private var failureReason: BackupFailureReason? = null

    override fun onMetadata(testDescriptor: TestDescriptor, event: TestMetadataEvent) {
      // The backup library publishes these JUnit report entries for each backup and restore.
      val entries = (event as? TestKeyValueDataEvent)?.values ?: return
      entries["BackupRestore.transportMode"]?.let { transportModes += transportModeOf(it) }
      entries["BackupRestore.storageDomainCount"]?.toIntOrNull()?.let { storageDomainCount = maxOf(it, storageDomainCount ?: it) }
      // A backup and restore that succeeds after an error shows that the test recovered from the error.
      if (entries["BackupRestore.status"] == "SUCCESS") errorReasons.remove(testDescriptor)
      entries["BackupRestore.errorCode"]?.let { errorReasons[testDescriptor] = failureReasonOf(it) }
    }

    override fun beforeSuite(suite: TestDescriptor) {}

    override fun afterSuite(suite: TestDescriptor, result: TestResult) {
      if (suite.parent == null) {
        val service = analyticsServiceProvider?.orNull
        if (service != null) {
          val totalDuration = if (result.endTime >= result.startTime) result.endTime - result.startTime else null
          val failed = result.resultType == TestResult.ResultType.FAILURE
          recordBackupTestRun(
            testCount = result.testCount.toInt(),
            passedTestCount = result.successfulTestCount.toInt(),
            failedTestCount = result.failedTestCount.toInt(),
            analyticsService = service,
            backupTestLibraryVersion = backupTestLibraryVersion,
            totalRunTimeMs = totalDuration,
            infrastructureCrashed = (failed && result.testCount == 0L),
            invocationType = invocationType,
            transportMode = transportModes.groupingBy { it }.eachCount().maxByOrNull { (_, count) -> count }?.key,
            failureReason = if (failed) failureReason ?: BackupFailureReason.UNKNOWN_FAILURE_REASON else BackupFailureReason.NO_FAILURE,
            storageDomainCount = storageDomainCount,
          )
        }
      }
    }

    override fun beforeTest(testDescriptor: TestDescriptor) {}

    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
      // A controller that a test class constructor or a @BeforeAll method receives reports its errors for the class, so the errors that
      // the class reported while a test ran belong to that test. Gradle reports a failed @BeforeAll or @AfterAll method as a test too.
      val reasons = generateSequence(testDescriptor) { it.parent }.mapNotNull { errorReasons.remove(it) }.toList()
      if (failureReason == null && result.resultType == TestResult.ResultType.FAILURE) {
        failureReason = reasons.firstOrNull()
      }
    }

    override fun onNoDevice() {
      val service = analyticsServiceProvider?.orNull ?: return
      recordBackupTestRun(
        testCount = 0,
        passedTestCount = 0,
        failedTestCount = 0,
        analyticsService = service,
        backupTestLibraryVersion = backupTestLibraryVersion,
        infrastructureCrashed = true,
        invocationType = invocationType,
        failureReason = BackupFailureReason.NO_DEVICE_AVAILABLE,
      )
    }

    private companion object {
      /** Maps every mode of the local transport, like `LOCAL`, to `LOCAL_TRANSPORT`, and every cloud mode to `GMS_TRANSPORT`. */
      fun transportModeOf(name: String): BackupTransportMode =
        when {
          name.startsWith("LOCAL") -> BackupTransportMode.LOCAL_TRANSPORT
          name.startsWith("CLOUD_") -> BackupTransportMode.GMS_TRANSPORT
          name == "DEVICE_TO_DEVICE" -> BackupTransportMode.D2D_TRANSPORT
          else -> BackupTransportMode.UNKNOWN_TRANSPORT_MODE
        }

      fun failureReasonOf(errorCode: String): BackupFailureReason =
        when (errorCode) {
          "NO_ONLINE_DEVICE",
          "NO_MATCHING_DEVICE" -> BackupFailureReason.NO_DEVICE_AVAILABLE
          "UNSUPPORTED_API_LEVEL" -> BackupFailureReason.API_LEVEL_UNSUPPORTED
          "GMSCORE_OUTDATED_OR_MISSING" -> BackupFailureReason.GMS_CORE_OUTDATED
          "BMGR_INIT_FAILED",
          "BACKUP_FAILED",
          "RESTORE_FAILED" -> BackupFailureReason.BMGR_FAILURE
          "SEEDING_FAILED",
          "CLEAR_DATA_FAILED" -> BackupFailureReason.SETUP_PRECONDITION_FAILURE
          "VERIFICATION_FAILED" -> BackupFailureReason.ASSERTION_FAILURE
          "RESTORE_POLL_TIMEOUT" -> BackupFailureReason.TIMEOUT
          else -> BackupFailureReason.UNKNOWN_FAILURE_REASON
        }
    }
  }

  companion object {
    /** The JUnit Platform engine identifier for Jupiter tests. */
    const val BACKUP_TEST_ENGINE_ID = "junit-jupiter"

    /** Default fallback version for the AndroidX backup testing framework artifacts. */
    const val DEFAULT_BACKUP_VERSION = "1.0.0-alpha03"

    /** Prefix of the system properties that the backup library reads to select devices. */
    private const val DEVICE_PROPERTY_PREFIX = "androidx.test.backup.device."
  }
}
