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

package com.android.build.gradle.internal.test.report

import com.android.utils.ILogger
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

private const val SUPPORTED_DETAIL_SCHEMA_VERSION = 1
internal const val SUPPORTED_ENVIRONMENT_SCHEMA_VERSION = 1

/**
 * Per-test JSON sidecar record read from [SUITE_DETAILS_DIR_NAME].
 *
 * @property schemaVersion Sidecar format version; defaults to [SUPPORTED_DETAIL_SCHEMA_VERSION] when omitted.
 * @property suiteType Optional suite type identifier (for example, [SUITE_TYPE_BACKUP_RESTORE]).
 * @property sourceLocation Optional [SourceLocationInfo] of the test case.
 * @property summaryAttributes Keyed [SummaryAttributeRecord] entries rendered in suite columns and detail cards.
 * @property callout Optional [CalloutInfo] with a failure headline, message, 1-based failed step, and [CalloutInfo.likelyCause].
 * @property actors [ActorRecord] entries for devices or participants referenced by [StepRecord.on].
 * @property steps Ordered [StepRecord] execution steps recorded for the test case.
 * @property logs [LogRecord] file references relative to the results directory.
 * @property artifacts [ArtifactRecord] metadata maps or file attachments relative to the results directory.
 */
internal data class TestDetailRecord(
  val schemaVersion: Int? = null,
  val suiteType: String? = null,
  val sourceLocation: SourceLocationInfo? = null,
  val summaryAttributes: List<SummaryAttributeRecord>? = null,
  val callout: CalloutInfo? = null,
  val actors: List<ActorRecord>? = null,
  val steps: List<StepRecord>? = null,
  val logs: List<LogRecord>? = null,
  val artifacts: List<ArtifactRecord>? = null,
)

/**
 * Summary attribute entry in a [TestDetailRecord].
 *
 * @property key Machine-readable attribute identifier.
 * @property label Optional human-readable column or chip label.
 * @property value Raw attribute value used for filtering.
 * @property displayValue Optional formatted text displayed instead of [value].
 * @property filterable Whether the attribute creates a filterable suite table column.
 */
internal data class SummaryAttributeRecord(
  val key: String? = null,
  val label: String? = null,
  val value: String? = null,
  val displayValue: String? = null,
  val filterable: Boolean? = null,
)

/**
 * Device or participant entry in a [TestDetailRecord].
 *
 * @property id Unique actor identifier within the test record, referenced by [StepRecord.on].
 * @property role Optional role identifier (for example, `"source"` or `"target"`).
 * @property name Human-readable actor display name; falls back to [id] when blank.
 * @property api Android API level when applicable.
 * @property serial Device serial identifier when available.
 * @property image System image or device build label.
 * @property playServices Google Play services version when applicable.
 */
internal data class ActorRecord(
  val id: String? = null,
  val role: String? = null,
  val name: String? = null,
  val api: Int? = null,
  val serial: String? = null,
  val image: String? = null,
  val playServices: String? = null,
)

/**
 * Execution timeline step in a [TestDetailRecord].
 *
 * @property n 1-based step sequence number matched by [CalloutInfo.failedStep].
 * @property name Human-readable step description.
 * @property on Optional actor identifier referencing [ActorRecord.id].
 * @property shared `true` when this step ran in shared class setup rather than the test method.
 * @property call Optional source call site label (for example, `"BackupTest.kt:48"`).
 * @property durationMs Step execution duration in milliseconds.
 * @property status Step outcome string (for example, `"PASSED"`, `"FAILED"`, `"SKIPPED"`).
 */
internal data class StepRecord(
  val n: Int? = null,
  val name: String? = null,
  val on: String? = null,
  val shared: Boolean? = null,
  val call: String? = null,
  val durationMs: Long? = null,
  val status: String? = null,
)

/**
 * Captured log file entry in a [TestDetailRecord].
 *
 * @property title Display label for the log; falls back to the file name when blank.
 * @property path Path to the log file relative to the test results directory.
 * @property truncated `true` when the test runner truncated the log file during capture.
 */
internal data class LogRecord(
  val title: String? = null,
  val path: String? = null,
  val truncated: Boolean? = null,
)

/**
 * Structured metadata card or file attachment entry in a [TestDetailRecord].
 *
 * @property name Display title for the artifact.
 * @property values Optional key-value summary entries rendered inline.
 * @property path Optional file path relative to the test results directory.
 */
internal data class ArtifactRecord(
  val name: String? = null,
  val values: Map<String, String>? = null,
  val path: String? = null,
)

/**
 * Environment metadata sidecar ([SUITE_ENVIRONMENT_FILE_NAME] or [SUITE_ENVIRONMENT_FILE_PREFIX]).
 *
 * @property schemaVersion Sidecar format version; defaults to [SUPPORTED_ENVIRONMENT_SCHEMA_VERSION] when omitted.
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
 * @property backupRules Extracted [BackupRulesRecord] when present.
 */
internal data class SuiteEnvironmentRecord(
  val schemaVersion: Int? = SUPPORTED_ENVIRONMENT_SCHEMA_VERSION,
  val modulePath: String? = null,
  val variantName: String? = null,
  val suiteName: String? = null,
  val targetName: String? = null,
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
  val backupRules: BackupRulesRecord? = null,
)

/**
 * Extracted Android manifest backup configuration stored in [SuiteEnvironmentRecord].
 *
 * @property allowBackup Value of `android:allowBackup` in the merged manifest.
 * @property fullBackupContent Resource reference from `android:fullBackupContent`.
 * @property fullBackupContentFile File name of the `fullBackupContent` XML resource.
 * @property fullBackupContentXml Raw XML content of the `fullBackupContent` file.
 * @property dataExtractionRules Resource reference from `android:dataExtractionRules`.
 * @property dataExtractionRulesFile File name of the `dataExtractionRules` XML resource.
 * @property dataExtractionRulesXml Raw XML content of the `dataExtractionRules` file.
 */
internal data class BackupRulesRecord(
  val allowBackup: Boolean? = null,
  val fullBackupContent: String? = null,
  val fullBackupContentFile: String? = null,
  val fullBackupContentXml: String? = null,
  val dataExtractionRules: String? = null,
  val dataExtractionRulesFile: String? = null,
  val dataExtractionRulesXml: String? = null,
)

/**
 * Unique logical key identifying a test suite execution environment.
 *
 * Keys sort by module, suite, variant, and then target so that the environments of one suite stay together in the report.
 *
 * @property modulePath Gradle module path.
 * @property variantName Tested variant name.
 * @property suiteName Test suite name.
 * @property targetName Target name.
 */
internal data class SuiteEnvironmentKey(
  val modulePath: String,
  val variantName: String,
  val suiteName: String,
  val targetName: String,
) : Comparable<SuiteEnvironmentKey> {
  override fun compareTo(other: SuiteEnvironmentKey): Int {
    return compareValuesBy(this, other, { it.modulePath }, { it.suiteName }, { it.variantName }, { it.targetName })
  }
}

/** Resolves safe report paths and reads JSON sidecars for test suites. */
internal object TestDetailRecordReader {

  /** Resolves [relativePath] within [baseDir], rejecting escapes and symbolic links. */
  private fun resolveSafeChildFile(baseDir: File, relativePath: String, logger: ILogger): File? {
    if (relativePath.isBlank() || relativePath.indexOf('\u0000') >= 0) {
      logger.warning("Rejected invalid empty or null-containing report path.")
      return null
    }

    val relPath: Path =
      try {
        Path.of(relativePath)
      } catch (_: InvalidPathException) {
        logger.warning("Rejected malformed report path '$relativePath'.")
        return null
      }

    if (relPath.isAbsolute) {
      logger.warning("Rejected absolute report path '$relativePath'; paths must be relative to the results directory.")
      return null
    }

    val normalizedBase = baseDir.toPath().toAbsolutePath().normalize()
    val resolved = normalizedBase.resolve(relPath).normalize()
    if (!resolved.startsWith(normalizedBase) || resolved == normalizedBase) {
      logger.warning("Rejected report path '$relativePath' escaping results directory '${baseDir.path}'.")
      return null
    }

    if (Files.isSymbolicLink(resolved)) {
      logger.warning("Rejected symbolic link report path '$relativePath'.")
      return null
    }

    if (Files.exists(resolved)) {
      try {
        val realBase = baseDir.toPath().toRealPath()
        val realResolved = resolved.toRealPath()
        if (!realResolved.startsWith(realBase) || realResolved == realBase) {
          logger.warning("Rejected report path '$relativePath' resolving outside results directory '${baseDir.path}'.")
          return null
        }
      } catch (_: IOException) {
        logger.warning("Unable to verify canonical path for '$relativePath'.")
        return null
      }
    }

    return resolved.toFile()
  }

  /**
   * Resolves [relativePath] within [baseDir], preferring hashed sidecars under [SUITE_DETAILS_DIR_NAME].
   *
   * When [xmlHash] is present and [relativePath] starts with [SUITE_DETAILS_DIR_NAME], checks `<SUITE_DETAILS_DIR_NAME>/<xmlHash>/...`
   * first before falling back to [relativePath].
   */
  fun resolveSidecarFile(
    baseDir: File,
    relativePath: String,
    xmlHash: String?,
    logger: ILogger,
  ): File? {
    val directFile = resolveSafeChildFile(baseDir, relativePath, logger) ?: return null
    if (!xmlHash.isNullOrEmpty() && xmlHash.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }) {
      val relNorm = Path.of(relativePath).normalize()
      if (relNorm.nameCount >= 2 && relNorm.getName(0).toString() == SUITE_DETAILS_DIR_NAME) {
        val hashedRel = Path.of(SUITE_DETAILS_DIR_NAME, xmlHash).resolve(relNorm.subpath(1, relNorm.nameCount)).toString()
        val hashedCandidate = resolveSafeChildFile(baseDir, hashedRel, logger)
        if (hashedCandidate?.isFile == true) {
          return hashedCandidate
        }
      }
    }
    return directFile
  }

  /** Reads and validates a [TestDetailRecord] sidecar file from [baseDir]. */
  fun readDetailRecord(
    baseDir: File,
    detailPath: String,
    gson: Gson,
    logger: ILogger,
    xmlHash: String? = null,
    warnedSchemaVersions: MutableSet<Int>? = null,
  ): TestDetailRecord? {
    val file = resolveSidecarFile(baseDir, detailPath, xmlHash, logger) ?: return null
    if (!file.isFile) {
      logger.warning("Test detail record '$detailPath' was not found in '${baseDir.path}'; falling back to basic view.")
      return null
    }

    val record =
      try {
        file.bufferedReader(Charsets.UTF_8).use { reader -> gson.fromJson(reader, TestDetailRecord::class.java) }
      } catch (e: JsonParseException) {
        logger.warning("Malformed JSON in test detail record '$detailPath': ${e.message}")
        return null
      } catch (e: IOException) {
        logger.warning("Failed to read test detail record '$detailPath': ${e.message}")
        return null
      }

    if (record == null) {
      logger.warning("Empty test detail record '$detailPath'; falling back to basic view.")
      return null
    }

    val version = record.schemaVersion ?: SUPPORTED_DETAIL_SCHEMA_VERSION
    if (version != SUPPORTED_DETAIL_SCHEMA_VERSION) {
      if (warnedSchemaVersions == null || warnedSchemaVersions.add(version)) {
        logger.warning(
          "Unsupported test detail schema version $version (supported: $SUPPORTED_DETAIL_SCHEMA_VERSION). " +
            "Upgrade Android Gradle Plugin to view full test details; falling back to basic view."
        )
      }
      return null
    }

    return record.copy(
      summaryAttributes = sanitizeList(record.summaryAttributes),
      actors = sanitizeList(record.actors),
      steps = sanitizeList(record.steps),
      logs = sanitizeList(record.logs),
      artifacts = sanitizeList(record.artifacts)?.map { it.copy(values = sanitizeStringMap(it.values)) },
    )
  }

  /** Reads and validates a [SuiteEnvironmentRecord] from [file]. */
  fun readSuiteEnvironment(
    file: File,
    gson: Gson,
    logger: ILogger,
    warnedSchemaVersions: MutableSet<Int>? = null,
  ): SuiteEnvironmentRecord? {
    if (!file.isFile) return null
    val record =
      try {
        file.bufferedReader(Charsets.UTF_8).use { reader -> gson.fromJson(reader, SuiteEnvironmentRecord::class.java) }
      } catch (e: JsonParseException) {
        logger.warning("Malformed JSON in suite environment file '${file.name}': ${e.message}")
        return null
      } catch (e: IOException) {
        logger.warning("Failed to read suite environment file '${file.name}': ${e.message}")
        return null
      } ?: return null

    val version = record.schemaVersion ?: SUPPORTED_ENVIRONMENT_SCHEMA_VERSION
    if (version != SUPPORTED_ENVIRONMENT_SCHEMA_VERSION) {
      if (warnedSchemaVersions == null || warnedSchemaVersions.add(version)) {
        logger.warning(
          "Unsupported suite environment schema version $version in '${file.name}' " +
            "(supported: $SUPPORTED_ENVIRONMENT_SCHEMA_VERSION). " +
            "Upgrade Android Gradle Plugin to view environment metadata; skipping."
        )
      }
      return null
    }

    if (record.modulePath.isNullOrBlank() || record.variantName.isNullOrBlank() || record.suiteName.isNullOrBlank()) {
      logger.warning("Incomplete suite environment metadata in '${file.name}'; skipping.")
      return null
    }

    return record
  }

  /** Sanitizes [rawName] to `[A-Za-z0-9._-]`, returning [fallback] when blank, `.`, or `..`. */
  fun sanitizeFileName(rawName: String?, fallback: String): String {
    if (rawName.isNullOrBlank()) return fallback
    val baseName = rawName.substringAfterLast('/').substringAfterLast('\\')
    val cleaned =
      buildString(baseName.length) {
        for (ch in baseName) {
          if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '.' || ch == '_' || ch == '-') {
            append(ch)
          } else {
            append('_')
          }
        }
      }
    return if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") fallback else cleaned
  }

  private fun <T : Any> sanitizeList(list: List<T?>?): List<T>? = list?.filterNotNull()

  private fun sanitizeStringMap(map: Map<String, String?>?): Map<String, String>? =
    map?.mapNotNull { (k, v) -> if (k.isNotBlank() && v != null) k to v else null }?.toMap()?.takeIf { it.isNotEmpty() }
}
