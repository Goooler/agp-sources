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

import com.android.build.api.dsl.TestTaskContext
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.TestSuiteTestTask
import com.google.wireless.android.sdk.stats.TestRun
import java.io.File
import java.io.Serializable
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.process.CommandLineArgumentProvider

/**
 * Passes backup device-selection and report system properties to the test JVM.
 *
 * Reads `androidx.test.backup.device.*` and `androidx.test.backup.report.*` at execution time so changing them does not invalidate the
 * configuration cache.
 */
internal class BackupPropertiesArgumentProvider(
  @get:Input val deviceProperties: Provider<Map<String, String>>,
  @get:Input val reportProperties: Provider<Map<String, String>>,
) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> = (deviceProperties.get() + reportProperties.get()).map { (name, value) -> "-D$name=$value" }
}

/**
 * Single-entry memoizer for [BackupManifestRulesExtractor.extract] shared across the task's `@Input` map providers so merged manifests and
 * resource XML directories are parsed once per input snapshot rather than re-parsed on every provider query.
 */
internal class BackupRulesMemoizer(
  private val extractor: (File?, List<File>) -> ExtractedBackupRules? = BackupManifestRulesExtractor::extract
) : Serializable {
  private data class FileStamp(val path: String, val lastModified: Long, val length: Long)

  private data class CacheKey(
    val manifest: FileStamp,
    val resDirPaths: List<String>,
    val ruleXmlFiles: List<FileStamp>,
  )

  @Transient private var lastKey: CacheKey? = null
  @Transient private var lastValue: ExtractedBackupRules? = null

  @Synchronized
  fun get(manifestPath: String, resLocations: Set<FileSystemLocation>): ExtractedBackupRules {
    val manifestFile = manifestPath.takeIf { it.isNotEmpty() }?.let(::File)
    val existingResDirs = resLocations.map { it.asFile }.filter { it.exists() }
    val ruleXmlFiles = existingResDirs.flatMap { resDir ->
      resDir
        .listFiles()
        ?.filter { it.isDirectory && (it.name == "xml" || it.name.startsWith("xml-")) }
        .orEmpty()
        .sortedBy { it.name }
        .flatMap { xmlDir ->
          xmlDir
            .listFiles()
            ?.filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
            .orEmpty()
            .sortedBy { it.name }
            .map { FileStamp(it.absolutePath, it.lastModified(), it.length()) }
        }
    }
    val key =
      CacheKey(
        manifest =
          FileStamp(
            path = manifestPath,
            lastModified = manifestFile?.takeIf { it.isFile }?.lastModified() ?: -1L,
            length = manifestFile?.takeIf { it.isFile }?.length() ?: -1L,
          ),
        resDirPaths = existingResDirs.map { it.absolutePath },
        ruleXmlFiles = ruleXmlFiles,
      )
    if (key == lastKey) {
      lastValue?.let {
        return it
      }
    }
    val computed = extractor(manifestFile, existingResDirs) ?: ExtractedBackupRules()
    lastKey = key
    lastValue = computed
    return computed
  }
}

/**
 * Configures [Test] and [TestSuiteTestTask] instances for Automated Backup and Restore test suites.
 *
 * Keeps Backup-specific JVM property forwarding, manifest backup rule extraction, and analytics collection isolated from the DSL layer and
 * the generic [TestSuiteTestTask] runner.
 */
internal class BackupTestSuiteTaskConfigurator(
  dslServices: DslServices,
  providers: ProviderFactory,
  private val libraryVersion: Provider<String>,
) {
  private val analyticsServiceProvider: Provider<AnalyticsService>? =
    try {
      getBuildService(dslServices.buildServiceRegistry, AnalyticsService::class.java)
    } catch (_: Exception) {
      null
    }

  private val deviceProperties: Provider<Map<String, String>> = providers.systemPropertiesPrefixedBy(DEVICE_PROPERTY_PREFIX)
  private val reportProperties: Provider<Map<String, String>> = providers.systemPropertiesPrefixedBy(REPORT_PROPERTY_PREFIX)

  private val invocationType: TestRun.TestInvocationType =
    if (dslServices.projectOptions[BooleanOption.IDE_INVOKED_FROM_IDE]) TestRun.TestInvocationType.ANDROID_STUDIO_THROUGH_GRADLE_TEST
    else TestRun.TestInvocationType.GRADLE_TEST

  fun configureTask(task: Test, context: TestTaskContext) {
    analyticsServiceProvider?.let { task.usesService(it) }
    val listener = BackupTestAnalyticsListener(analyticsServiceProvider, libraryVersion.get(), invocationType)
    task.addTestListener(listener)
    task.addTestMetadataListener(listener)

    (task as? TestSuiteTestTask)?.let { suiteTask ->
      suiteTask.suiteType.setDisallowChanges(SUITE_TYPE_BACKUP_RESTORE)
      suiteTask.addNoDeviceListener(listener)

      val manifestPathProvider = suiteTask.testedVariantMergedManifest.map { it.asFile.absolutePath }.orElse("")
      val resDirsProvider = suiteTask.testedVariantResDirectories.elements
      val rulesMemoizer = BackupRulesMemoizer()
      val extractedRulesProvider =
        manifestPathProvider.zip(resDirsProvider) { manifestPath, resLocations ->
          rulesMemoizer.get(manifestPath, resLocations)
        }
      suiteTask.additionalEngineInputProperties.putAll(
        extractedRulesProvider.map { BackupManifestRulesExtractor.toEngineInputProperties(it) }
      )
      suiteTask.suiteReportProperties.putAll(extractedRulesProvider.map { BackupManifestRulesExtractor.toSuiteReportProperties(it) })
    }

    // Gradle doesn't pass the build's system properties to test JVMs, so forward the ones that configure devices and reporting.
    task.jvmArgumentProviders.add(BackupPropertiesArgumentProvider(deviceProperties, reportProperties))
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

  private companion object {
    /** Prefix of the system properties that the backup library reads to select devices. */
    const val DEVICE_PROPERTY_PREFIX = "androidx.test.backup.device."

    /** Prefix of the system properties that configure backup test report capture. */
    const val REPORT_PROPERTY_PREFIX = "androidx.test.backup.report."
  }
}
