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

import com.android.build.gradle.internal.testsuites.backup.BackupRestoreReportAdapter
import com.android.build.gradle.internal.testsuites.screenshot.ScreenshotReportAdapter

/** Text file generated during report aggregation (for example, extracted configuration XML under `artifacts/<envId>/`). */
internal data class GeneratedTextFileSpec(val relativePath: String, val content: String)

/**
 * Suite-specific environment attributes, configuration file references, and lazy payload entries produced by
 * [SuiteReportAdapter.adaptEnvironment].
 */
internal data class AdaptedSuiteEnvironment(
  val attributes: Map<String, String>? = null,
  val configFiles: List<EnvironmentConfigFile>? = null,
  val generatedFiles: List<GeneratedTextFileSpec> = emptyList(),
  val lazyFiles: List<LazyEnvironmentFile> = emptyList(),
)

/**
 * Holds input context provided to a [SuiteReportAdapter] for a single test case.
 *
 * @property rawProperties Raw `<property>` entries from the `<testcase>` XML element (plus inherited suite failure entries when
 *   applicable).
 * @property detailRecord Parsed [TestDetailRecord] when present.
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
 * Enriches test suite results, logs, summary columns, and suite environments in the aggregated HTML test report.
 *
 * Built-in test suite adapters ([ScreenshotReportAdapter], [BackupRestoreReportAdapter]) live in their respective `testsuites/` packages,
 * while [DefaultSuiteReportAdapter] handles unit tests, instrumentation tests, custom suites, and third-party test libraries via
 * [TestDetailRecord] report entries and standard JUnit XML elements.
 */
internal interface SuiteReportAdapter {
  /** Unique machine-readable identifier for this adapter. */
  val adapterId: String

  /** `<property>` key prefixes owned by this suite type (propagated on synthetic class-level setup failures). */
  val suiteOwnedPropertyPrefixes: Set<String>
    get() = emptySet()

  /** `<testsuite><properties>` keys captured into [SuiteEnvironmentRecord.extraProperties] for [adaptEnvironment]. */
  val suiteEnvironmentPropertyKeys: Set<String>
    get() = emptySet()

  /** Whether failing tests without report entries keep their `<system-out>` and `<system-err>` for the detail view. */
  val keepsFailureStreams: Boolean
    get() = false

  /** Returns `true` when this adapter handles [context]. */
  fun matches(context: SuiteAdapterContext): Boolean

  /** Transforms [context] into an [AdaptedTestCase]. */
  fun adapt(context: SuiteAdapterContext): AdaptedTestCase

  /** Enriches or orders per-test [logs] using resolved [actors] before artifact collection. */
  fun enrichLogs(logs: List<LogRecord>, actors: List<ActorInfo>?): List<LogRecord> = logs

  /** Orders merged [SummaryColumnDef] entries for a suite descriptor across test cases. */
  fun orderSummaryColumns(columns: Collection<SummaryColumnDef>): List<SummaryColumnDef> = columns.toList()

  /** Adapts suite-level [record] properties into [SuiteEnvironmentInfo] attributes, config files, and lazy `details/<envId>.js` entries. */
  fun adaptEnvironment(envId: String, record: SuiteEnvironmentRecord): AdaptedSuiteEnvironment = AdaptedSuiteEnvironment()
}

/**
 * Resolves the matching [SuiteReportAdapter] for test cases and suite environments.
 *
 * @property adapters Ordered candidate adapters checked before [fallbackAdapter].
 * @property fallbackAdapter Adapter returned when no entry in [adapters] matches.
 */
internal class SuiteReportAdapterRegistry(
  private val adapters: List<SuiteReportAdapter> = listOf(ScreenshotReportAdapter, BackupRestoreReportAdapter),
  private val fallbackAdapter: SuiteReportAdapter = DefaultSuiteReportAdapter,
) {
  private val allAdapters: List<SuiteReportAdapter> = adapters + fallbackAdapter

  val allSuiteOwnedPropertyPrefixes: Set<String> = allAdapters.flatMapTo(LinkedHashSet()) { it.suiteOwnedPropertyPrefixes }

  val allSuiteEnvironmentPropertyKeys: Set<String> = allAdapters.flatMapTo(LinkedHashSet()) { it.suiteEnvironmentPropertyKeys }

  /** Returns the first adapter in [adapters] matching [context], or [fallbackAdapter]. */
  fun resolve(context: SuiteAdapterContext): SuiteReportAdapter = adapters.firstOrNull { it.matches(context) } ?: fallbackAdapter

  /** Looks up a registered adapter by [adapterId], falling back to [fallbackAdapter]. */
  fun findById(adapterId: String?): SuiteReportAdapter = allAdapters.firstOrNull { it.adapterId == adapterId } ?: fallbackAdapter
}

/** Adapts unit tests, custom test suites, and third-party libraries via [TestDetailRecord] report entries. */
internal object DefaultSuiteReportAdapter : SuiteReportAdapter {
  override val adapterId: String = ADAPTER_ID_DEFAULT

  override fun matches(context: SuiteAdapterContext): Boolean = true

  override fun adapt(context: SuiteAdapterContext): AdaptedTestCase {
    val common = extractDetailRecordFields(context.detailRecord)
    val filteredProps = filterUserProperties(context.rawProperties)
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

internal data class ExtractedDetailFields(
  val summaryAttributes: Map<String, SummaryAttributeValue>?,
  val dynamicColumns: List<SummaryColumnDef>,
  val sourceLocation: SourceLocationInfo?,
  val callout: CalloutInfo?,
  val actors: List<ActorInfo>?,
  val steps: List<StepInfo>?,
)

internal fun filterUserProperties(
  rawProperties: Map<String, String>,
  extraPredicate: (String) -> Boolean = { true },
): Map<String, String>? {
  if (rawProperties.isEmpty()) return null
  val filtered = LinkedHashMap<String, String>()
  for ((key, value) in rawProperties) {
    if (key.startsWith(INTERNAL_REPORT_KEY_PREFIX)) continue
    if (!extraPredicate(key)) continue
    filtered[key] = value
  }
  return filtered.takeIf { it.isNotEmpty() }
}

internal fun extractDetailRecordFields(record: TestDetailRecord?): ExtractedDetailFields {
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
        val id = actor.id.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        ActorInfo(
          id = id,
          role = actor.role?.takeIf { it.isNotBlank() },
          name = actor.name.takeIf { it.isNotBlank() } ?: id,
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
        val name = step.name.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val status = step.status.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        StepInfo(
          n = step.n,
          name = name,
          on = step.on?.takeIf { it.isNotBlank() },
          shared = step.shared,
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
