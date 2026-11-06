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

package com.android.build.gradle.internal.test.report

/**
 * Root model serialized into `data.js` for the HTML test report.
 *
 * @property projectName The name of the project.
 * @property timestamp The timestamp of when the report was generated.
 * @property numberOfModules The total count of modules.
 * @property numberOfPackages The total count of packages.
 * @property numberOfClasses The total count of classes.
 * @property variants A list of all variant names included in this report.
 * @property testSuites A list of all unique test suite names.
 * @property targets A list of all unique test target names.
 * @property modules [Module] entries in the project.
 * @property relativeRootDir Relative path from the report output directory to the root project directory.
 * @property suiteDescriptors [SuiteDescriptor] metadata and summary column definitions keyed by test suite name.
 * @property suiteEnvironments [SuiteEnvironmentInfo] app and build environment metadata included in this report.
 */
data class RootReport(
  val projectName: String,
  val timestamp: String,
  val numberOfModules: Int,
  val numberOfPackages: Int,
  val numberOfClasses: Int,
  val variants: List<String>,
  val testSuites: List<String>,
  val targets: List<String>,
  val modules: List<Module>,
  val relativeRootDir: String,
  val suiteDescriptors: Map<String, SuiteDescriptor> = emptyMap(),
  val suiteEnvironments: List<SuiteEnvironmentInfo> = emptyList(),
)

/**
 * Adapter configuration and summary table columns for a test suite.
 *
 * @property adapterId Identifier of the [SuiteReportAdapter] handling this suite.
 * @property suiteType Optional suite type identifier reported by the test engine or library.
 * @property title Optional human-readable display title for the suite.
 * @property likelyCauseHeading Optional heading shown above [CalloutInfo.likelyCause] in failure callouts.
 * @property summaryColumns [SummaryColumnDef] columns displayed and filtered in single-suite list views.
 * @property docsUrl Optional documentation URL for the test suite.
 */
data class SuiteDescriptor(
  val adapterId: String,
  val suiteType: String? = null,
  val title: String? = null,
  val likelyCauseHeading: String? = null,
  val summaryColumns: List<SummaryColumnDef> = emptyList(),
  val docsUrl: String? = null,
)

/**
 * Summary attribute column definition for a test suite's list view.
 *
 * @property key Attribute key matching [VariantTestResult.summaryAttributes].
 * @property label Human-readable column header label.
 * @property filterable Whether the report UI exposes a filter menu for this column.
 * @property sortable Whether this column supports sorting in the report UI.
 */
data class SummaryColumnDef(
  val key: String,
  val label: String,
  val filterable: Boolean = false,
  val sortable: Boolean = true,
)

/**
 * App and build environment metadata for one test suite execution.
 *
 * @property envId Unique environment identifier within the report (referenced by [VariantTestResult.envId]).
 * @property modulePath Gradle module path (for example, `":app"`).
 * @property variantName Tested variant name (for example, `"debug"`).
 * @property suiteName Test suite name (for example, `"backupTest"`).
 * @property targetName Target name (for example, `"default"`).
 * @property suiteType Optional suite type identifier configured on the test suite task.
 * @property applicationId Tested application package ID.
 * @property applicationName Tested application label or name.
 * @property versionName Tested application version name.
 * @property versionCode Tested application version code.
 * @property targetSdkVersion Target SDK API level from the merged manifest when present.
 * @property agpVersion Android Gradle Plugin version.
 * @property gradleVersion Gradle version.
 * @property testLibraryVersion Configured test library version when applicable.
 * @property runner Test runner identifier (for example, `"junit-jupiter"`).
 * @property backupRules Extracted [BackupRulesInfo] summary when present for backup test suites.
 */
data class SuiteEnvironmentInfo(
  val envId: String,
  val modulePath: String,
  val variantName: String,
  val suiteName: String,
  val targetName: String,
  val suiteType: String? = null,
  val applicationId: String? = null,
  val applicationName: String? = null,
  val versionName: String? = null,
  val versionCode: Int? = null,
  val targetSdkVersion: Int? = null,
  val agpVersion: String? = null,
  val gradleVersion: String? = null,
  val testLibraryVersion: String? = null,
  val runner: String? = null,
  val backupRules: BackupRulesInfo? = null,
)

/**
 * Summary of an application's manifest backup configuration and rule files.
 *
 * @property allowBackup Value of `android:allowBackup` in the merged manifest.
 * @property fullBackupContent Resource reference from `android:fullBackupContent`.
 * @property fullBackupContentFile File name of the `fullBackupContent` XML resource.
 * @property fullBackupContentArtifactPath Report-relative path to the copied `fullBackupContent` XML file.
 * @property dataExtractionRules Resource reference from `android:dataExtractionRules`.
 * @property dataExtractionRulesFile File name of the `dataExtractionRules` XML resource.
 * @property dataExtractionRulesArtifactPath Report-relative path to the copied `dataExtractionRules` XML file.
 */
data class BackupRulesInfo(
  val allowBackup: Boolean? = null,
  val fullBackupContent: String? = null,
  val fullBackupContentFile: String? = null,
  val fullBackupContentArtifactPath: String? = null,
  val dataExtractionRules: String? = null,
  val dataExtractionRulesFile: String? = null,
  val dataExtractionRulesArtifactPath: String? = null,
)

/**
 * Gradle module entry in the test report.
 *
 * @property name The path of the module (e.g., ":app").
 * @property testSuiteSummaries Summaries of the test suites within this module.
 * @property packages A list of Java/Kotlin packages containing tests.
 */
data class Module(val name: String, val testSuiteSummaries: List<TestSuiteSummary>, val packages: List<Package>)

/**
 * Java or Kotlin package containing test classes in the report.
 *
 * @property name The package name (e.g., "com.example.mytapp").
 * @property testSuiteSummaries Summaries of the test suites within this package.
 * @property classes A list of test classes within this package.
 */
data class Package(val name: String, val testSuiteSummaries: List<TestSuiteSummary>, val classes: List<ClassType>)

/**
 * Test class entry containing test methods in the report.
 *
 * @property name The simple name of the class (e.g., "ExampleUnitTest").
 * @property testSuiteSummaries Summaries of the test suites within this class.
 * @property testCases A list of test methods (test cases) in this class.
 */
data class ClassType(val name: String, val testSuiteSummaries: List<TestSuiteSummary>, val testCases: List<TestCase>)

/**
 * Execution result of a test case for one variant.
 *
 * @property status The result status (e.g., "pass", "fail", "skipped").
 * @property stackTraceId Stack trace identifier referencing [Target.commonStackTraces] when failed.
 * @property diffPercent The percentage difference in the screenshot test.
 * @property previewName The name of the preview used in the screenshot test.
 * @property methodName The method name of the screenshot test.
 * @property refImagePath The path to the reference image.
 * @property newImagePath The path to the newly generated image.
 * @property diffImagePath The path to the image showing the differences.
 * @property durationMs Execution duration in milliseconds.
 * @property suiteType Optional suite type identifier.
 * @property adapterId Identifier of the [SuiteReportAdapter] that processed this result.
 * @property detailId Identifier of the lazy [LazyTestDetail] script (`details/<detailId>.js`) when written.
 * @property envId Identifier of the matching [SuiteEnvironmentInfo] when available.
 * @property properties Non-internal `<property>` entries reported for the test case.
 * @property summaryAttributes Keyed [SummaryAttributeValue] entries for list columns, filtering, and summary cards.
 * @property sourceLocation [SourceLocationInfo] file and line metadata for the test case.
 * @property callout Structured [CalloutInfo] failure callout and likely cause diagnosis.
 * @property actors [ActorInfo] entries for devices or participants that ran the test case.
 * @property steps Recorded [StepInfo] lifecycle steps for the test case.
 * @property artifacts Structured [ArtifactInfo] summaries and report-relative file links.
 * @property logMeta [LogMetaInfo] entries for per-test logs whose text content lives in `details/<detailId>.js`.
 */
data class VariantTestResult(
  val status: String,
  val stackTraceId: String? = null,
  val diffPercent: String? = null,
  val previewName: String? = null,
  val methodName: String? = null,
  val refImagePath: String? = null,
  val newImagePath: String? = null,
  val diffImagePath: String? = null,
  val durationMs: Long? = null,
  val suiteType: String? = null,
  val adapterId: String? = null,
  val detailId: String? = null,
  val envId: String? = null,
  val properties: Map<String, String>? = null,
  val summaryAttributes: Map<String, SummaryAttributeValue>? = null,
  val sourceLocation: SourceLocationInfo? = null,
  val callout: CalloutInfo? = null,
  val actors: List<ActorInfo>? = null,
  val steps: List<StepInfo>? = null,
  val artifacts: List<ArtifactInfo>? = null,
  val logMeta: List<LogMetaInfo>? = null,
)

/**
 * Raw value, formatted display value, and optional label for a summary attribute.
 *
 * @property value Raw machine-readable attribute value used for filtering and grouping.
 * @property displayValue Human-readable value rendered in summary chips and cells.
 * @property label Optional human-readable attribute label when not covered by [SuiteDescriptor.summaryColumns].
 */
data class SummaryAttributeValue(
  val value: String,
  val displayValue: String = value,
  val label: String? = null,
)

/**
 * Source location of a test case.
 *
 * @property file Source file path or file name.
 * @property line 1-based line number in [file].
 * @property className Fully qualified test class name.
 * @property methodName Test method name.
 */
data class SourceLocationInfo(
  val file: String? = null,
  val line: Int? = null,
  val className: String? = null,
  val methodName: String? = null,
)

/**
 * Structured failure callout for a test case.
 *
 * @property kind Machine-readable failure category or error code.
 * @property failedStep 1-based step index matching [StepInfo.n] when failure occurred inside a step.
 * @property title Short failure headline.
 * @property message Detailed failure explanation.
 * @property likelyCause Optional diagnosis describing the probable root cause.
 */
data class CalloutInfo(
  val kind: String? = null,
  val failedStep: Int? = null,
  val title: String? = null,
  val message: String? = null,
  val likelyCause: String? = null,
)

/**
 * Device or actor participating in a test case.
 *
 * @property id Unique actor identifier within the test case (referenced by [StepInfo.on]).
 * @property role Optional role description (for example, `"source"` or `"target"`).
 * @property name Display name of the device or actor.
 * @property api Android API level when the actor is an Android device.
 * @property serial Device serial identifier when available.
 * @property image System image or build fingerprint label.
 * @property playServices Google Play services version string when applicable.
 */
data class ActorInfo(
  val id: String,
  val role: String? = null,
  val name: String,
  val api: Int? = null,
  val serial: String? = null,
  val image: String? = null,
  val playServices: String? = null,
)

/**
 * Recorded step in a test case's execution timeline.
 *
 * @property n Optional 1-based step sequence number.
 * @property name Human-readable step name.
 * @property on Optional actor identifier matching [ActorInfo.id].
 * @property shared Whether this step ran in shared class setup rather than the test body.
 * @property call Optional code call site label (for example, `"File:42"`).
 * @property durationMs Step execution duration in milliseconds.
 * @property status Step execution status (for example, `"PASSED"`, `"FAILED"`, `"SKIPPED"`).
 */
data class StepInfo(
  val n: Int? = null,
  val name: String,
  val on: String? = null,
  val shared: Boolean = false,
  val call: String? = null,
  val durationMs: Long? = null,
  val status: String,
)

/**
 * Artifact summary values or report-relative file link for a [VariantTestResult].
 *
 * @property name Display title for the artifact.
 * @property values Optional key-value summary entries rendered inline.
 * @property path Optional report-relative path to the copied artifact file under `artifacts/`.
 */
data class ArtifactInfo(
  val name: String,
  val values: Map<String, String>? = null,
  val path: String? = null,
)

/**
 * Metadata for a log file whose content loads lazily from [LazyTestDetail] (`details/<detailId>.js`).
 *
 * @property title Display label for the log tab or section.
 * @property lineCount Number of lines in the inline log preview.
 * @property artifactPath Report-relative path to the full copied log file under `artifacts/`.
 */
data class LogMetaInfo(
  val title: String,
  val lineCount: Int,
  val artifactPath: String? = null,
)

/**
 * Lazy per-test payload written to `details/<detailId>.js`.
 *
 * @property logs Captured [LazyLogEntry] previews for this test case.
 * @property systemOut Captured standard output text, capped at [MAX_STREAM_CHARS] UTF-16 code units.
 * @property systemOutTruncated Whether [systemOut] exceeded [MAX_STREAM_CHARS] and was truncated.
 * @property systemErr Captured standard error text, capped at [MAX_STREAM_CHARS] UTF-16 code units.
 * @property systemErrTruncated Whether [systemErr] exceeded [MAX_STREAM_CHARS] and was truncated.
 */
data class LazyTestDetail(
  val logs: List<LazyLogEntry> = emptyList(),
  val systemOut: String? = null,
  val systemOutTruncated: Boolean = false,
  val systemErr: String? = null,
  val systemErrTruncated: Boolean = false,
)

/**
 * Lazy per-environment payload written once to `details/<envId>.js`.
 *
 * @property backupRules Extracted [LazyBackupRuleEntry] XML files for this suite environment.
 */
data class LazyEnvironmentDetail(val backupRules: List<LazyBackupRuleEntry> = emptyList())

/**
 * Log entry stored in `details/<detailId>.js`.
 *
 * @property title Display title for the log tab or section.
 * @property content Inline UTF-8 log preview text, capped at [MAX_INLINE_LOG_BYTES] bytes.
 * @property lineCount Number of lines in [content].
 * @property capturedTruncated Whether the test runner truncated the log file during capture.
 * @property previewTruncated Whether report aggregation truncated [content] at [MAX_INLINE_LOG_BYTES] bytes.
 * @property artifactPath Report-relative path to the full copied log file under `artifacts/`.
 */
data class LazyLogEntry(
  val title: String,
  val content: String,
  val lineCount: Int,
  val capturedTruncated: Boolean = false,
  val previewTruncated: Boolean = false,
  val artifactPath: String? = null,
)

/**
 * Backup rule XML entry stored in [LazyEnvironmentDetail] (`details/<envId>.js`).
 *
 * @property label File name of the XML resource (for example, `"backup_rules.xml"`).
 * @property attribute Manifest attribute or resource reference (for example, `"@xml/backup_rules"`).
 * @property artifactPath Report-relative path to the copied XML file under `artifacts/`.
 * @property content Raw XML text content of the backup rules file.
 */
data class LazyBackupRuleEntry(
  val label: String,
  val attribute: String? = null,
  val artifactPath: String? = null,
  val content: String,
)

/**
 * Variant test results for a single test suite within a test case.
 *
 * @property testSuiteName The name of the test suite.
 * @property variantResults Variant results keyed by variant name.
 */
data class TestSuiteTestResult(val testSuiteName: String, val variantResults: Map<String, VariantTestResult>)

/**
 * Target device or platform execution results under a test case.
 *
 * @property name The name of the target device/platform.
 * @property testSuiteSummaries Summaries of the test suites for this target.
 * @property testSuiteResults A list of results grouped by test suite.
 * @property commonStackTraces A list of unique stack traces encountered for this target.
 */
data class Target(
  val name: String,
  val testSuiteSummaries: List<TestSuiteSummary>,
  val testSuiteResults: List<TestSuiteTestResult>,
  val commonStackTraces: List<StackTraceGroup>,
)

/**
 * Test method results across suites, variants, and targets.
 *
 * @property name The name of the test case.
 * @property targets [Target] entries where this test case ran.
 */
data class TestCase(val name: String, val targets: List<Target>)

/**
 * Deduplicated stack trace and its occurrences across suites and variants.
 *
 * @property id A unique identifier for this stack trace.
 * @property stackTrace The actual stack trace text.
 * @property occurrences Variant names grouped by test suite name where this stack trace occurred.
 */
data class StackTraceGroup(val id: String, val stackTrace: String, val occurrences: Map<String, List<String>>)

/**
 * Pass, fail, and skip summary for a test suite.
 *
 * @property name The name of the test suite.
 * @property variantSummaries A list of summaries per variant.
 */
data class TestSuiteSummary(val name: String, val variantSummaries: List<VariantSummary>)

/**
 * Aggregated test counts and duration for one variant.
 *
 * @property name The name of the variant.
 * @property passed The number of passed tests.
 * @property failed The number of failed tests.
 * @property skipped The number of skipped tests.
 * @property total The total number of tests.
 * @property rate The percentage of passed tests (`passed / (passed + failed)`).
 * @property durationMs Total execution duration in milliseconds across tests in this summary.
 */
data class VariantSummary(
  val name: String,
  val passed: Int,
  val failed: Int,
  val skipped: Int,
  val total: Int,
  val rate: Double,
  val durationMs: Long = 0L,
)
