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
import java.util.Locale

private const val SUPPORTED_DETAIL_SCHEMA_VERSION = 1

/**
 * Per-test detail record reconstructed from `androidx.report.*` JUnit report entries and file attachments.
 *
 * @property schemaVersion Entry schema version; defaults to [SUPPORTED_DETAIL_SCHEMA_VERSION] when omitted.
 * @property suiteType Optional suite type identifier reported by the test engine or library.
 * @property sourceLocation Optional [SourceLocationInfo] of the test case.
 * @property summaryAttributes Keyed [SummaryAttributeRecord] entries rendered in suite columns and detail cards.
 * @property callout Optional [CalloutInfo] with a failure headline, message, 1-based failed step, and [CalloutInfo.likelyCause].
 * @property actors [ActorInfo] entries for devices or participants referenced by [StepInfo.on].
 * @property steps Ordered [StepInfo] execution steps recorded for the test case.
 * @property logs [LogRecord] file references relative to the results directory.
 * @property artifacts [ArtifactRecord] metadata maps or file attachments relative to the results directory.
 */
internal data class TestDetailRecord(
  val schemaVersion: Int? = null,
  val suiteType: String? = null,
  val sourceLocation: SourceLocationInfo? = null,
  val summaryAttributes: List<SummaryAttributeRecord>? = null,
  val callout: CalloutInfo? = null,
  val actors: List<ActorInfo>? = null,
  val steps: List<StepInfo>? = null,
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
 * Captured log file entry in a [TestDetailRecord].
 *
 * @property title Explicit custom display label for the log when reported; adapters or aggregation supply a fallback when `null`.
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
 * Suite execution environment metadata reconstructed from `<testsuite><properties>`.
 *
 * @property modulePath Gradle module path (for example, `":app"`).
 * @property variantName Tested variant name (for example, `"debug"`).
 * @property suiteName Test suite name (for example, `"customSuite"`).
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
 * @property extraProperties Suite-specific `<testsuite><properties>` entries matched via [SuiteReportAdapter.suiteEnvironmentPropertyKeys].
 */
internal data class SuiteEnvironmentRecord(
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
  val extraProperties: Map<String, String> = emptyMap(),
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
    return compareValuesBy(
      this,
      other,
      { it.modulePath },
      { it.suiteName },
      { it.variantName },
      { it.targetName },
    )
  }
}

/** Parses `androidx.report.*` JUnit report entries and suite environment properties into structured report records. */
internal object TestDetailRecordReader {

  private val ACTOR_FIELDS = setOf("role", "name", "api", "serial", "image", "playServices")
  private val STEP_FIELDS = setOf("name", "on", "shared", "call", "durationMs", "status")
  private val ARTIFACT_VALUES_SPLIT_REGEX = Regex(",\\s*(?=[^,=]+=)")

  /** Reconstructs a [SuiteEnvironmentRecord] from `<testsuite><properties>` when present. */
  fun parseSuiteEnvironmentFromProperties(
    suiteProperties: Map<String, String>,
    modulePath: String?,
    variantName: String?,
    suiteName: String?,
    targetName: String?,
    extraEnvironmentKeys: Set<String> = emptySet(),
  ): SuiteEnvironmentRecord? {
    val mod = modulePath?.takeIf { it.isNotBlank() } ?: return null
    val variant = variantName?.takeIf { it.isNotBlank() } ?: return null
    val suite = suiteName?.takeIf { it.isNotBlank() } ?: return null
    if (suiteProperties.none { (k, v) -> (k in BASE_SUITE_ENVIRONMENT_KEYS || k in extraEnvironmentKeys) && v.isNotBlank() }) {
      return null
    }

    fun nonBlank(key: String): String? = suiteProperties[key]?.takeIf { it.isNotBlank() }

    val extraProperties = LinkedHashMap<String, String>()
    for (key in extraEnvironmentKeys) {
      nonBlank(key)?.let { extraProperties[key] = it }
    }

    return SuiteEnvironmentRecord(
      modulePath = mod,
      variantName = variant,
      suiteName = suite,
      targetName = targetName?.takeIf { it.isNotBlank() } ?: UNKNOWN_TARGET,
      suiteType = nonBlank(KEY_SUITE_TYPE),
      applicationId = nonBlank(KEY_APPLICATION_ID),
      applicationName = nonBlank(KEY_APPLICATION_NAME),
      versionName = nonBlank(KEY_VERSION_NAME),
      versionCode = nonBlank(KEY_VERSION_CODE)?.trim()?.toIntOrNull(),
      targetSdkVersion = nonBlank(KEY_TARGET_SDK_VERSION)?.trim()?.toIntOrNull(),
      agpVersion = nonBlank(KEY_AGP_VERSION),
      gradleVersion = nonBlank(KEY_GRADLE_VERSION),
      testLibraryVersion = nonBlank(KEY_TEST_LIBRARY_VERSION),
      runner = nonBlank(KEY_RUNNER),
      extraProperties = extraProperties,
    )
  }

  /**
   * Reconstructs a [TestDetailRecord] from `androidx.report.*` entries in [rawProperties] and [attachments] extracted from `<system-out>`.
   *
   * Returns `null` when [rawProperties] has no `androidx.report.*` entries, so tests that only publish files keep the classic view.
   */
  fun parseDetailRecordFromEntries(
    rawProperties: Map<String, String>,
    attachments: List<String> = emptyList(),
    classname: String? = null,
    testcaseName: String? = null,
    logger: ILogger,
    warnedSchemaVersions: MutableSet<Int>? = null,
  ): TestDetailRecord? {
    if (rawProperties.keys.none { it.startsWith(INTERNAL_REPORT_KEY_PREFIX) }) {
      return null
    }
    val nonBlankAttachments = attachments.map { it.trim() }.filter { it.isNotEmpty() }

    val rawSchema = rawProperties[KEY_REPORT_SCHEMA_VERSION]?.trim()?.takeIf { it.isNotEmpty() }
    val version =
      if (rawSchema == null) {
        SUPPORTED_DETAIL_SCHEMA_VERSION
      } else {
        rawSchema.toIntOrNull()
          ?: run {
            logger.warning("Invalid test detail schema version '$rawSchema'; falling back to basic view.")
            return null
          }
      }

    if (version != SUPPORTED_DETAIL_SCHEMA_VERSION) {
      if (warnedSchemaVersions == null || warnedSchemaVersions.add(version)) {
        logger.warning(
          "Unsupported test detail schema version $version (supported: $SUPPORTED_DETAIL_SCHEMA_VERSION). " +
            "Upgrade Android Gradle Plugin to view full test details; falling back to basic view."
        )
      }
      return null
    }

    val suiteType = rawProperties[KEY_REPORT_SUITE_TYPE]?.takeIf { it.isNotBlank() }

    val sourceLocation =
      rawProperties[KEY_REPORT_SOURCE]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { src ->
          val colon = src.lastIndexOf(':')
          val parsedLine = if (colon > 0) src.substring(colon + 1).trim().toIntOrNull()?.takeIf { it > 0 } else null
          val file = if (parsedLine != null) src.substring(0, colon).takeIf { it.isNotBlank() } else src
          SourceLocationInfo(
            file = file,
            line = parsedLine,
            className = classname?.takeIf { it.isNotBlank() },
            methodName = normalizeMethodName(testcaseName, classname),
          )
        }

    val summaryAttributes = ArrayList<SummaryAttributeRecord>()
    for ((key, rawVal) in rawProperties) {
      if (!key.startsWith(REPORT_ATTR_PREFIX)) continue
      val attrKey = key.removePrefix(REPORT_ATTR_PREFIX)
      if (attrKey.isBlank() || '.' in attrKey) continue
      val value = rawVal.takeIf { it.isNotBlank() } ?: continue
      val prefix = "$REPORT_ATTR_PREFIX$attrKey"
      summaryAttributes +=
        SummaryAttributeRecord(
          key = attrKey,
          label = rawProperties["$prefix.label"]?.takeIf { it.isNotBlank() } ?: defaultSummaryAttributeLabel(attrKey),
          value = value,
          displayValue = rawProperties["$prefix.displayValue"]?.takeIf { it.isNotBlank() } ?: value,
          filterable = rawProperties["$prefix.filterable"]?.trim()?.equals("true", ignoreCase = true) == true,
        )
    }

    val actorIds = LinkedHashSet<String>()
    for (key in rawProperties.keys) {
      if (!key.startsWith(REPORT_ACTOR_PREFIX)) continue
      val rest = key.removePrefix(REPORT_ACTOR_PREFIX)
      val dot = rest.lastIndexOf('.')
      if (dot <= 0) continue
      val id = rest.substring(0, dot).trim()
      val field = rest.substring(dot + 1)
      if (id.isNotEmpty() && field in ACTOR_FIELDS) {
        actorIds += id
      }
    }
    val actors = actorIds.map { id ->
      val p = "$REPORT_ACTOR_PREFIX$id"
      ActorInfo(
        id = id,
        role = rawProperties["$p.role"]?.takeIf { it.isNotBlank() },
        name = rawProperties["$p.name"]?.takeIf { it.isNotBlank() } ?: id,
        api = rawProperties["$p.api"]?.trim()?.toIntOrNull(),
        serial = rawProperties["$p.serial"]?.takeIf { it.isNotBlank() },
        image = rawProperties["$p.image"]?.takeIf { it.isNotBlank() },
        playServices = rawProperties["$p.playServices"]?.takeIf { it.isNotBlank() },
      )
    }

    val stepKeys = LinkedHashSet<String>()
    for (key in rawProperties.keys) {
      if (!key.startsWith(REPORT_STEP_PREFIX)) continue
      val rest = key.removePrefix(REPORT_STEP_PREFIX)
      val dot = rest.lastIndexOf('.')
      if (dot <= 0) continue
      val stepKey = rest.substring(0, dot).trim()
      val field = rest.substring(dot + 1)
      if (stepKey.isNotEmpty() && field in STEP_FIELDS) {
        stepKeys += stepKey
      }
    }
    val orderedStepKeys = orderStepKeys(stepKeys)
    val steps = orderedStepKeys.mapNotNull { stepKey ->
      val p = "$REPORT_STEP_PREFIX$stepKey"
      val name = rawProperties["$p.name"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val status = rawProperties["$p.status"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      StepInfo(
        n = stepKey.toIntOrNull(),
        name = name,
        on = rawProperties["$p.on"]?.takeIf { it.isNotBlank() },
        shared = rawProperties["$p.shared"]?.trim()?.equals("true", ignoreCase = true) == true,
        call = rawProperties["$p.call"]?.takeIf { it.isNotBlank() },
        durationMs = rawProperties["$p.durationMs"]?.trim()?.toLongOrNull(),
        status = status,
      )
    }

    val callout =
      CalloutInfo(
          kind = rawProperties["${REPORT_CALLOUT_PREFIX}kind"]?.takeIf { it.isNotBlank() },
          failedStep = rawProperties["${REPORT_CALLOUT_PREFIX}failedStep"]?.trim()?.toIntOrNull(),
          title = rawProperties["${REPORT_CALLOUT_PREFIX}title"]?.takeIf { it.isNotBlank() },
          message = rawProperties["${REPORT_CALLOUT_PREFIX}message"]?.takeIf { it.isNotBlank() },
          likelyCause = rawProperties["${REPORT_CALLOUT_PREFIX}likelyCause"]?.takeIf { it.isNotBlank() },
        )
        .takeIf {
          it.kind != null || it.failedStep != null || it.title != null || it.message != null || it.likelyCause != null
        }

    val artifactIndices = LinkedHashSet<String>()
    for (key in rawProperties.keys) {
      if (!key.startsWith(REPORT_ARTIFACT_PREFIX)) continue
      val rest = key.removePrefix(REPORT_ARTIFACT_PREFIX)
      val dot = rest.lastIndexOf('.')
      if (dot <= 0) continue
      val idx = rest.substring(0, dot).trim()
      val field = rest.substring(dot + 1)
      if (idx.isNotEmpty() && (field == "name" || field == "values")) {
        artifactIndices += idx
      }
    }
    val artifacts = ArrayList<ArtifactRecord>()
    for (idx in artifactIndices.sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }) {
      val p = "$REPORT_ARTIFACT_PREFIX$idx"
      val name = rawProperties["$p.name"]?.takeIf { it.isNotBlank() } ?: continue
      val parsedValues = parseArtifactValues(rawProperties["$p.values"])
      artifacts += ArtifactRecord(name = name, values = parsedValues, path = null)
    }

    val sanitizedFileFields = LinkedHashMap<Pair<String, String>, String>()
    for ((k, v) in rawProperties) {
      if (!k.startsWith(REPORT_FILE_PREFIX)) continue
      val rest = k.substring(REPORT_FILE_PREFIX.length)
      val dot = rest.lastIndexOf('.')
      if (dot <= 0) continue
      val rawFileName = rest.substring(0, dot)
      val field = rest.substring(dot + 1)
      if (field == "name" || field == "truncated" || field == "values") {
        val sanitized = PathSafetyUtils.sanitizeFileName(rawFileName, "attachment.bin")
        sanitizedFileFields.putIfAbsent(sanitized to field, v)
      }
    }
    fun lookupFileProperty(fileName: String, field: String): String? =
      rawProperties["$REPORT_FILE_PREFIX$fileName.$field"] ?: sanitizedFileFields[fileName to field]

    val logs = ArrayList<LogRecord>()
    for (attachmentPath in nonBlankAttachments) {
      val fileName = attachmentPath.substringAfterLast('/').substringAfterLast('\\')
      if (fileName.isBlank()) continue
      val lower = fileName.lowercase(Locale.ROOT)
      val customName = lookupFileProperty(fileName, "name")?.takeIf { it.isNotBlank() }
      val truncated = lookupFileProperty(fileName, "truncated")?.trim()?.equals("true", ignoreCase = true) == true
      if (lower.endsWith(".txt") || lower.endsWith(".log")) {
        logs += LogRecord(title = customName, path = attachmentPath, truncated = truncated)
      } else {
        val fileValues = parseArtifactValues(lookupFileProperty(fileName, "values"))
        artifacts += ArtifactRecord(name = customName ?: fileName, values = fileValues, path = attachmentPath)
      }
    }

    return TestDetailRecord(
      schemaVersion = version,
      suiteType = suiteType,
      sourceLocation = sourceLocation,
      summaryAttributes = summaryAttributes.takeIf { it.isNotEmpty() },
      callout = callout,
      actors = actors.takeIf { it.isNotEmpty() },
      steps = steps.takeIf { it.isNotEmpty() },
      logs = logs.takeIf { it.isNotEmpty() },
      artifacts = artifacts.takeIf { it.isNotEmpty() },
    )
  }

  private fun parseArtifactValues(rawValues: String?): Map<String, String>? =
    rawValues
      ?.takeIf { it.isNotBlank() }
      ?.split(ARTIFACT_VALUES_SPLIT_REGEX)
      ?.mapNotNull { token ->
        val eq = token.indexOf('=')
        if (eq > 0) {
          val k = token.substring(0, eq).trim()
          val v = token.substring(eq + 1)
          if (k.isNotEmpty()) k to v else null
        } else {
          null
        }
      }
      ?.toMap(LinkedHashMap())
      ?.takeIf { it.isNotEmpty() }

  private fun defaultSummaryAttributeLabel(key: String): String =
    key.replace('_', ' ').replaceFirstChar {
      if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
    }

  private fun normalizeMethodName(testcaseName: String?, classname: String?): String? {
    val trimmed = testcaseName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (trimmed in SYNTHETIC_TEST_NAMES || trimmed == classname) return null
    return when {
      trimmed.endsWith("()") -> trimmed.removeSuffix("()").takeIf { it.isNotEmpty() }
      '(' in trimmed && ' ' !in trimmed -> trimmed.substringBefore('(').takeIf { it.isNotEmpty() }
      else -> trimmed
    }
  }

  /** Orders numbered steps by number. Otherwise keeps the order the steps were published in, which is the order they ran in. */
  private fun orderStepKeys(stepKeys: Set<String>): List<String> =
    if (stepKeys.all { it.toIntOrNull() != null }) stepKeys.sortedBy { it.toInt() } else stepKeys.toList()
}
