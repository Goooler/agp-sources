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

package com.android.build.gradle.internal.testsuites.backup

import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.test.recordBackupTestRun
import com.android.build.gradle.tasks.TestSuiteTestTask
import com.google.wireless.android.sdk.stats.TestRun
import com.google.wireless.android.sdk.stats.TestRun.BackupTestRun.BackupFailureReason
import com.google.wireless.android.sdk.stats.TestRun.BackupTestRun.BackupTransportMode
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestKeyValueDataEvent
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestMetadataEvent
import org.gradle.api.tasks.testing.TestMetadataListener
import org.gradle.api.tasks.testing.TestResult

/**
 * Records each run of a backup test suite in analytics, capturing the transport mode, storage domain count, and failure reason reported by
 * the backup library, as well as runs that fail before starting because no device is available.
 *
 * For multi-test runs, records the transport mode used by most tests, the largest storage domain count, and the reason of the first failed
 * test for which the backup library reported an error. Errors from which a test recovers (such as a restore expected to fail) are ignored.
 */
internal class BackupTestAnalyticsListener(
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
