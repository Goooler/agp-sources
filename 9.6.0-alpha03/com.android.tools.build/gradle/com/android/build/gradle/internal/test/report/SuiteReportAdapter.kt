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

/**
 * Holds input context provided to a [SuiteReportAdapter] for a single test case.
 *
 * @property rawProperties Raw `<property>` entries from the `<testsuite>` and `<testcase>` XML elements.
 * @property detailRecord Parsed [TestDetailRecord] sidecar when present.
 * @property environment Parsed [SuiteEnvironmentRecord] for the suite run when present.
 */
internal data class SuiteAdapterContext(
  val rawProperties: Map<String, String>,
  val detailRecord: TestDetailRecord? = null,
  val environment: SuiteEnvironmentRecord? = null,
)

/** Suite type declared by the detail record, falling back to the suite environment. */
internal val SuiteAdapterContext.declaredSuiteType: String?
  get() = detailRecord?.suiteType?.takeIf { it.isNotBlank() } ?: environment?.suiteType?.takeIf { it.isNotBlank() }

/**
 * Pairs the adapted [VariantTestResult] and [SuiteDescriptor] produced by a [SuiteReportAdapter].
 *
 * @property result Enriched [VariantTestResult] for the test case. Its `status` is a placeholder; [XMLReportAggregator] replaces it with
 *   the JUnit XML outcome.
 * @property suiteDescriptor Suite-level [SuiteDescriptor] contributed by this test case.
 */
internal data class AdaptedTestCase(
  val result: VariantTestResult,
  val suiteDescriptor: SuiteDescriptor,
)

/**
 * Enriches test suite results and descriptors in the aggregated HTML test report.
 *
 * Specialized adapters ([ScreenshotReportAdapter], [BackupRestoreReportAdapter]) customize suite descriptors, columns, or suite-specific
 * fields, while [DefaultSuiteReportAdapter] handles unit tests, custom suites, and third-party test libraries via [TestDetailRecord]
 * sidecars and standard JUnit XML elements.
 */
internal interface SuiteReportAdapter {
  /** Unique machine-readable identifier for this adapter. */
  val adapterId: String

  /** Returns `true` when this adapter handles [context]. */
  fun matches(context: SuiteAdapterContext): Boolean

  /** Transforms [context] into an [AdaptedTestCase]. */
  fun adapt(context: SuiteAdapterContext): AdaptedTestCase
}

/**
 * Resolves the matching [SuiteReportAdapter] for each test case.
 *
 * @property adapters Ordered candidate adapters checked before [fallbackAdapter].
 * @property fallbackAdapter Adapter returned when no entry in [adapters] matches.
 */
internal class SuiteReportAdapterRegistry(
  private val adapters: List<SuiteReportAdapter> = listOf(ScreenshotReportAdapter, BackupRestoreReportAdapter),
  private val fallbackAdapter: SuiteReportAdapter = DefaultSuiteReportAdapter,
) {
  /** Returns the first adapter in [adapters] matching [context], or [fallbackAdapter]. */
  fun resolve(context: SuiteAdapterContext): SuiteReportAdapter {
    return adapters.firstOrNull { it.matches(context) } ?: fallbackAdapter
  }
}

/** Adapts Compose Preview Screenshot test results for the HTML report. */
internal object ScreenshotReportAdapter : SuiteReportAdapter {
  override val adapterId: String = ADAPTER_ID_SCREENSHOT

  private const val KEY_DIFF_PERCENT = "PreviewScreenshot.diffPercent"
  private const val KEY_PREVIEW_NAME = "PreviewScreenshot.previewName"
  private const val KEY_METHOD_NAME = "PreviewScreenshot.methodName"
  private const val KEY_REF_IMAGE_PATH = "PreviewScreenshot.refImagePath"
  private const val KEY_NEW_IMAGE_PATH = "PreviewScreenshot.newImagePath"
  private const val KEY_DIFF_IMAGE_PATH = "PreviewScreenshot.diffImagePath"

  override fun matches(context: SuiteAdapterContext): Boolean {
    return context.rawProperties.keys.any { it.startsWith(SCREENSHOT_PROPERTY_PREFIX) }
  }

  override fun adapt(context: SuiteAdapterContext): AdaptedTestCase {
    val common = extractDetailRecordFields(context.detailRecord)
    val filteredProps = filterUserProperties(context.rawProperties) { key -> !key.startsWith(SCREENSHOT_PROPERTY_PREFIX) }
    val suiteType = context.declaredSuiteType
    val descriptor =
      SuiteDescriptor(
        adapterId = ADAPTER_ID_SCREENSHOT,
        suiteType = suiteType,
        summaryColumns = common.dynamicColumns,
      )

    return AdaptedTestCase(
      result =
        VariantTestResult(
          status = STATUS_PASS,
          adapterId = ADAPTER_ID_SCREENSHOT,
          suiteType = suiteType,
          diffPercent = context.rawProperties[KEY_DIFF_PERCENT],
          previewName = context.rawProperties[KEY_PREVIEW_NAME],
          methodName = context.rawProperties[KEY_METHOD_NAME],
          refImagePath = context.rawProperties[KEY_REF_IMAGE_PATH],
          newImagePath = context.rawProperties[KEY_NEW_IMAGE_PATH],
          diffImagePath = context.rawProperties[KEY_DIFF_IMAGE_PATH],
          properties = filteredProps,
          summaryAttributes = common.summaryAttributes,
          sourceLocation = common.sourceLocation,
          callout = common.callout,
          actors = common.actors,
          steps = common.steps,
        ),
      suiteDescriptor = descriptor,
    )
  }
}

/** Adapts AndroidX Backup and Restore test results for the HTML report. */
internal object BackupRestoreReportAdapter : SuiteReportAdapter {
  override val adapterId: String = ADAPTER_ID_BACKUP_RESTORE

  private const val KEY_TRANSPORT_MODE = "BackupRestore.transportMode"
  private const val TITLE = "Backup & Restore"
  private const val LIKELY_CAUSE_HEADING = "Backup rule that left the data out · Likely cause"

  private val CANONICAL_COLUMNS =
    listOf(
      SummaryColumnDef(key = "transport", label = "Transport", filterable = true, sortable = true),
      SummaryColumnDef(key = "devices", label = "Devices", filterable = true, sortable = true),
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

  private fun formatTransportMode(mode: String): String =
    when (mode) {
      "LOCAL" -> "Local"
      "DEVICE_TO_DEVICE" -> "Device to device"
      "CLOUD_ENCRYPTED" -> "Cloud (encrypted)"
      "CLOUD_UNENCRYPTED" -> "Cloud (unencrypted)"
      else -> mode
    }
}

/** Adapts unit tests, custom test suites, and third-party libraries via [TestDetailRecord] sidecars. */
internal object DefaultSuiteReportAdapter : SuiteReportAdapter {
  override val adapterId: String = ADAPTER_ID_DEFAULT

  override fun matches(context: SuiteAdapterContext): Boolean = true

  override fun adapt(context: SuiteAdapterContext): AdaptedTestCase {
    val common = extractDetailRecordFields(context.detailRecord)
    val filteredProps = filterUserProperties(context.rawProperties) { true }
    val suiteType = context.declaredSuiteType
    val descriptor =
      SuiteDescriptor(
        adapterId = ADAPTER_ID_DEFAULT,
        suiteType = suiteType,
        summaryColumns = common.dynamicColumns,
      )

    return AdaptedTestCase(
      result =
        VariantTestResult(
          status = STATUS_PASS,
          adapterId = ADAPTER_ID_DEFAULT,
          suiteType = suiteType,
          properties = filteredProps,
          summaryAttributes = common.summaryAttributes,
          sourceLocation = common.sourceLocation,
          callout = common.callout,
          actors = common.actors,
          steps = common.steps,
        ),
      suiteDescriptor = descriptor,
    )
  }
}

private data class ExtractedDetailFields(
  val summaryAttributes: Map<String, SummaryAttributeValue>?,
  val dynamicColumns: List<SummaryColumnDef>,
  val sourceLocation: SourceLocationInfo?,
  val callout: CalloutInfo?,
  val actors: List<ActorInfo>?,
  val steps: List<StepInfo>?,
)

private val SUITE_INJECTED_METADATA_KEYS = setOf(KEY_MODULE_PATH, KEY_TEST_SUITE_NAME, KEY_TESTED_VARIANT_NAME, KEY_TEST_TARGET)

private fun filterUserProperties(
  rawProperties: Map<String, String>,
  extraPredicate: (String) -> Boolean,
): Map<String, String>? {
  if (rawProperties.isEmpty()) return null
  val filtered = LinkedHashMap<String, String>()
  for ((key, value) in rawProperties) {
    if (key == KEY_DETAIL_PATH || key.startsWith(INTERNAL_REPORT_KEY_PREFIX)) continue
    if (key in SUITE_INJECTED_METADATA_KEYS) continue
    if (!extraPredicate(key)) continue
    filtered[key] = value
  }
  return filtered.takeIf { it.isNotEmpty() }
}

private fun extractDetailRecordFields(record: TestDetailRecord?): ExtractedDetailFields {
  if (record == null) {
    return ExtractedDetailFields(
      summaryAttributes = null,
      dynamicColumns = emptyList(),
      sourceLocation = null,
      callout = null,
      actors = null,
      steps = null,
    )
  }

  val attrMap = LinkedHashMap<String, SummaryAttributeValue>()
  val dynamicColumns = LinkedHashMap<String, SummaryColumnDef>()
  record.summaryAttributes?.forEach { attr ->
    val key = attr.key?.takeIf { it.isNotBlank() } ?: return@forEach
    val value = attr.value?.takeIf { it.isNotBlank() } ?: return@forEach
    val label = attr.label?.takeIf { it.isNotBlank() }
    attrMap[key] =
      SummaryAttributeValue(
        value = value,
        displayValue = attr.displayValue?.takeIf { it.isNotBlank() } ?: value,
        label = label,
      )
    if (attr.filterable == true) {
      dynamicColumns[key] = SummaryColumnDef(key = key, label = label ?: key, filterable = true, sortable = true)
    } else {
      dynamicColumns.remove(key)
    }
  }

  val sourceLocation =
    record.sourceLocation
      ?.let {
        SourceLocationInfo(
          file = it.file?.takeIf { s -> s.isNotBlank() },
          line = it.line,
          className = it.className?.takeIf { s -> s.isNotBlank() },
          methodName = it.methodName?.takeIf { s -> s.isNotBlank() },
        )
      }
      ?.takeIf { it.file != null || it.line != null || it.className != null || it.methodName != null }

  val callout =
    record.callout
      ?.let {
        CalloutInfo(
          kind = it.kind?.takeIf { s -> s.isNotBlank() },
          failedStep = it.failedStep,
          title = it.title?.takeIf { s -> s.isNotBlank() },
          message = it.message?.takeIf { s -> s.isNotBlank() },
          likelyCause = it.likelyCause?.takeIf { s -> s.isNotBlank() },
        )
      }
      ?.takeIf {
        it.kind != null || it.failedStep != null || it.title != null || it.message != null || it.likelyCause != null
      }

  val actors =
    record.actors
      ?.mapNotNull { actor ->
        val id = actor.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val name = actor.name?.takeIf { it.isNotBlank() } ?: id
        ActorInfo(
          id = id,
          role = actor.role?.takeIf { it.isNotBlank() },
          name = name,
          api = actor.api,
          serial = actor.serial?.takeIf { it.isNotBlank() },
          image = actor.image?.takeIf { it.isNotBlank() },
          playServices = actor.playServices?.takeIf { it.isNotBlank() },
        )
      }
      ?.takeIf { it.isNotEmpty() }

  val steps =
    record.steps
      ?.mapNotNull { step ->
        val name = step.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val status = step.status?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        StepInfo(
          n = step.n,
          name = name,
          on = step.on?.takeIf { it.isNotBlank() },
          shared = step.shared ?: false,
          call = step.call?.takeIf { it.isNotBlank() },
          durationMs = step.durationMs,
          status = status,
        )
      }
      ?.takeIf { it.isNotEmpty() }

  return ExtractedDetailFields(
    summaryAttributes = attrMap.takeIf { it.isNotEmpty() },
    dynamicColumns = dynamicColumns.values.toList(),
    sourceLocation = sourceLocation,
    callout = callout,
    actors = actors,
    steps = steps,
  )
}
