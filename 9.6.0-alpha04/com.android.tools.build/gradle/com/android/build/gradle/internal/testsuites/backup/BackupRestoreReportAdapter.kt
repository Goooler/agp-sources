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

import com.android.build.gradle.internal.test.report.ActorInfo
import com.android.build.gradle.internal.test.report.AdaptedSuiteEnvironment
import com.android.build.gradle.internal.test.report.AdaptedTestCase
import com.android.build.gradle.internal.test.report.EnvironmentConfigFile
import com.android.build.gradle.internal.test.report.GeneratedTextFileSpec
import com.android.build.gradle.internal.test.report.LazyEnvironmentFile
import com.android.build.gradle.internal.test.report.LogRecord
import com.android.build.gradle.internal.test.report.PathSafetyUtils
import com.android.build.gradle.internal.test.report.STATUS_PASS
import com.android.build.gradle.internal.test.report.SuiteAdapterContext
import com.android.build.gradle.internal.test.report.SuiteDescriptor
import com.android.build.gradle.internal.test.report.SuiteEnvironmentRecord
import com.android.build.gradle.internal.test.report.SuiteReportAdapter
import com.android.build.gradle.internal.test.report.SummaryAttributeValue
import com.android.build.gradle.internal.test.report.SummaryColumnDef
import com.android.build.gradle.internal.test.report.VariantTestResult
import com.android.build.gradle.internal.test.report.declaredSuiteType
import com.android.build.gradle.internal.test.report.extractDetailRecordFields
import com.android.build.gradle.internal.test.report.filterUserProperties
import java.util.Locale

internal const val ADAPTER_ID_BACKUP_RESTORE = "backupRestore"
internal const val SUITE_TYPE_BACKUP_RESTORE = "androidx.test.backup"
internal const val BACKUP_RESTORE_PROPERTY_PREFIX = "BackupRestore."
internal const val BACKUP_RESTORE_DOCS_URL = "https://developer.android.com/identity/data/testing"

/** Adapts AndroidX Backup and Restore test results and manifest rule files for the HTML report. */
internal object BackupRestoreReportAdapter : SuiteReportAdapter {
  override val adapterId: String = ADAPTER_ID_BACKUP_RESTORE
  override val suiteOwnedPropertyPrefixes: Set<String> = setOf(BACKUP_RESTORE_PROPERTY_PREFIX)
  override val suiteEnvironmentPropertyKeys: Set<String> = BACKUP_SUITE_ENVIRONMENT_KEYS
  override val keepsFailureStreams: Boolean = true

  private const val KEY_TRANSPORT_MODE = "BackupRestore.transportMode"
  private const val TITLE = "Backup & Restore"
  private const val LIKELY_CAUSE_HEADING = "Backup rule that left the data out · Likely cause"

  internal val CANONICAL_COLUMNS =
    listOf(
      SummaryColumnDef(
        key = "transport",
        label = "Transport",
        filterable = true,
        sortable = true,
        tooltip = "Android backup transport mode used by the test",
      ),
      SummaryColumnDef(
        key = "devices",
        label = "Devices",
        filterable = true,
        sortable = true,
        tooltip = "Source and target Android devices used by the test",
      ),
    )

  override fun matches(context: SuiteAdapterContext): Boolean {
    val declaredSuiteType = context.declaredSuiteType
    if (declaredSuiteType != null) {
      return declaredSuiteType == SUITE_TYPE_BACKUP_RESTORE
    }
    return context.rawProperties.keys.any { it.startsWith(BACKUP_RESTORE_PROPERTY_PREFIX) }
  }

  override fun adapt(context: SuiteAdapterContext): AdaptedTestCase {
    val common = extractDetailRecordFields(context.detailRecord)

    val mergedAttributes = LinkedHashMap<String, SummaryAttributeValue>()
    common.summaryAttributes?.forEach { (key, attr) ->
      mergedAttributes[key] =
        when (key) {
          "transport" -> {
            val normalizedDisplay = if (attr.displayValue == attr.value) formatTransportMode(attr.value) else attr.displayValue
            attr.copy(displayValue = normalizedDisplay, label = attr.label ?: "Transport")
          }
          "devices" -> attr.copy(label = attr.label ?: "Devices")
          else -> attr
        }
    }
    if ("transport" !in mergedAttributes) {
      context.rawProperties[KEY_TRANSPORT_MODE]
        ?.takeIf { it.isNotBlank() }
        ?.let { mode ->
          mergedAttributes["transport"] = SummaryAttributeValue(value = mode, displayValue = formatTransportMode(mode), label = "Transport")
        }
    }

    val columnsByKey = LinkedHashMap<String, SummaryColumnDef>()
    for (col in CANONICAL_COLUMNS) {
      if (col.key in mergedAttributes) {
        columnsByKey[col.key] = col
      }
    }
    for (col in common.dynamicColumns) {
      columnsByKey.putIfAbsent(col.key, col)
    }

    // Without a detail record, the raw BackupRestore.* properties are the only diagnostics, so keep them except for the
    // transport mode, which already appears as a summary column.
    val hasDetailRecord = context.detailRecord != null
    val filteredProps =
      filterUserProperties(context.rawProperties) { key ->
        when {
          key == KEY_TRANSPORT_MODE -> "transport" !in mergedAttributes
          key.startsWith(BACKUP_RESTORE_PROPERTY_PREFIX) -> !hasDetailRecord
          else -> true
        }
      }

    val descriptor =
      SuiteDescriptor(
        adapterId = ADAPTER_ID_BACKUP_RESTORE,
        suiteType = SUITE_TYPE_BACKUP_RESTORE,
        title = TITLE,
        likelyCauseHeading = LIKELY_CAUSE_HEADING,
        summaryColumns = columnsByKey.values.toList(),
        docsUrl = BACKUP_RESTORE_DOCS_URL,
      )

    return AdaptedTestCase(
      result =
        VariantTestResult(
          status = STATUS_PASS,
          adapterId = ADAPTER_ID_BACKUP_RESTORE,
          suiteType = SUITE_TYPE_BACKUP_RESTORE,
          properties = filteredProps,
          summaryAttributes = mergedAttributes.takeIf { it.isNotEmpty() },
          sourceLocation = common.sourceLocation,
          callout = common.callout,
          actors = common.actors,
          steps = common.steps,
        ),
      suiteDescriptor = descriptor,
    )
  }

  override fun enrichLogs(logs: List<LogRecord>, actors: List<ActorInfo>?): List<LogRecord> {
    if (logs.isEmpty()) return logs
    return logs
      .map { log ->
        val fileName = log.path?.substringAfterLast('/')?.substringAfterLast('\\').orEmpty()
        val lower = fileName.lowercase(Locale.ROOT)
        val resolvedTitle =
          when {
            !log.title.isNullOrBlank() -> log.title
            fileName.equals("host-log.txt", ignoreCase = true) -> "Host log"
            lower.startsWith("logcat-") && lower.endsWith(".txt") -> {
              val suffix = fileName.substring("logcat-".length, fileName.length - 4)
              val matchedActor = actors?.firstOrNull {
                it.serial == suffix || it.id == suffix || (it.serial != null && PathSafetyUtils.sanitizeFileName(it.serial, "") == suffix)
              }
              "Logcat (${matchedActor?.id ?: suffix})"
            }
            else -> fileName.ifBlank { null }
          }
        log.copy(title = resolvedTitle)
      }
      .sortedBy { log ->
        val fileName = log.path?.substringAfterLast('/')?.substringAfterLast('\\')
        if (fileName?.equals("host-log.txt", ignoreCase = true) == true) 0 else 1
      }
  }

  override fun orderSummaryColumns(columns: Collection<SummaryColumnDef>): List<SummaryColumnDef> = columns.sortedBy {
    when (it.key) {
      "transport" -> 0
      "devices" -> 1
      else -> 2
    }
  }

  override fun adaptEnvironment(envId: String, record: SuiteEnvironmentRecord): AdaptedSuiteEnvironment {
    val extra = record.extraProperties
    if (extra.isEmpty()) return AdaptedSuiteEnvironment()

    val attributes = LinkedHashMap<String, String>()
    extra[KEY_ALLOW_BACKUP]?.trim()?.let { raw ->
      when {
        raw.equals("true", ignoreCase = true) -> attributes[KEY_ALLOW_BACKUP] = "true"
        raw.equals("false", ignoreCase = true) -> attributes[KEY_ALLOW_BACKUP] = "false"
      }
    }

    val configFiles = mutableListOf<EnvironmentConfigFile>()
    val generatedFiles = mutableListOf<GeneratedTextFileSpec>()
    val lazyFiles = mutableListOf<LazyEnvironmentFile>()
    val usedFileNames = HashSet<String>()

    fun registerRuleFile(
      key: String,
      referenceKey: String,
      fileNameKey: String,
      xmlContentKey: String,
      fallbackFileName: String,
    ) {
      val reference = extra[referenceKey]?.takeIf { it.isNotBlank() }
      val rawFileName = extra[fileNameKey]?.takeIf { it.isNotBlank() }
      val xmlContent = extra[xmlContentKey]?.takeIf { it.isNotBlank() }
      if (reference == null && rawFileName == null && xmlContent == null) return

      val isXmlRef = reference?.startsWith("@xml/") == true
      val safeFileName = PathSafetyUtils.sanitizeFileName(rawFileName, fallbackFileName)
      val displayLabel =
        rawFileName
          ?: if (xmlContent != null || isXmlRef) {
            safeFileName
          } else {
            key
          }
      val artifactPath =
        if (xmlContent != null) {
          val allocatedFileName = PathSafetyUtils.allocateUniqueFileName(rawFileName ?: fallbackFileName, fallbackFileName, usedFileNames)
          val path = "artifacts/$envId/$allocatedFileName"
          generatedFiles.add(GeneratedTextFileSpec(path, xmlContent))
          lazyFiles.add(
            LazyEnvironmentFile(
              key = key,
              label = displayLabel,
              reference = reference,
              artifactPath = path,
              content = xmlContent,
            )
          )
          path
        } else {
          null
        }

      configFiles.add(
        EnvironmentConfigFile(
          key = key,
          label = displayLabel,
          reference = reference,
          artifactPath = artifactPath,
        )
      )
    }

    registerRuleFile(
      key = KEY_FULL_BACKUP_CONTENT,
      referenceKey = KEY_FULL_BACKUP_CONTENT,
      fileNameKey = KEY_FULL_BACKUP_CONTENT_FILE,
      xmlContentKey = KEY_FULL_BACKUP_CONTENT_XML,
      fallbackFileName = "backup_rules.xml",
    )
    registerRuleFile(
      key = KEY_DATA_EXTRACTION_RULES,
      referenceKey = KEY_DATA_EXTRACTION_RULES,
      fileNameKey = KEY_DATA_EXTRACTION_RULES_FILE,
      xmlContentKey = KEY_DATA_EXTRACTION_RULES_XML,
      fallbackFileName = "data_extraction_rules.xml",
    )

    return AdaptedSuiteEnvironment(
      attributes = attributes.takeIf { it.isNotEmpty() },
      configFiles = configFiles.takeIf { it.isNotEmpty() },
      generatedFiles = generatedFiles,
      lazyFiles = lazyFiles,
    )
  }

  private fun formatTransportMode(mode: String): String =
    when (mode) {
      "LOCAL" -> "Local"
      "DEVICE_TO_DEVICE" -> "Device to device"
      "CLOUD_ENCRYPTED" -> "Cloud (encrypted)"
      "CLOUD_UNENCRYPTED" -> "Cloud (unencrypted)"
      else -> mode
    }
}
