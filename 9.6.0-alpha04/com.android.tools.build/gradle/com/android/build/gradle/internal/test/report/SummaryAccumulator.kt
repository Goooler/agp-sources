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

import java.io.File

/** Raw execution data for a single test case on a suite, variant, and target before report rollup. */
internal data class TestCaseExecution(
  val testSuiteName: String,
  val variantName: String,
  val targetName: String,
  val status: String,
  val durationMs: Long?,
  val stackTrace: String?,
  val systemOut: String?,
  val systemOutTruncated: Boolean,
  val systemErr: String?,
  val systemErrTruncated: Boolean,
  val xmlDir: File?,
  val environmentKey: SuiteEnvironmentKey?,
  val adaptedResult: VariantTestResult,
  val rawLogs: List<LogRecord>?,
  val rawArtifacts: List<ArtifactRecord>?,
)

/** Prioritizes execution statuses (`Fail > Pass > Skipped`) across duplicate runs. */
internal fun shouldReplaceExecution(
  existing: TestCaseExecution?,
  candidate: TestCaseExecution,
): Boolean {
  return existing == null ||
    (existing.status != STATUS_FAIL && candidate.status == STATUS_FAIL) ||
    (existing.status == STATUS_SKIPPED && candidate.status == STATUS_PASS)
}

/** Sums [VariantSummary] entries by variant across [variants] and computes the pass rate. */
internal fun flattenVariantSummaries(
  summaries: List<VariantSummary>,
  variants: List<String>,
): List<VariantSummary> {
  return variants.map { variant ->
    var passed = 0
    var failed = 0
    var skipped = 0
    var total = 0
    var durationSum = 0L
    var hasDuration = false
    summaries
      .filter { it.name == variant }
      .forEach {
        passed += it.passed
        failed += it.failed
        skipped += it.skipped
        total += it.total
        if (it.durationMs != null) {
          durationSum += it.durationMs
          hasDuration = true
        }
      }
    buildVariantSummary(variant, passed, failed, skipped, total, if (hasDuration) durationSum else null)
  }
}

/** Merges child [TestSuiteSummary] lists by suite name across [variants]. */
internal fun mergeTestSuiteSummaries(
  childrenSuiteSummaries: List<TestSuiteSummary>,
  variants: List<String>,
): List<TestSuiteSummary> {
  return childrenSuiteSummaries
    .groupBy { it.name }
    .map { (suiteName, summaries) ->
      val variantSummaries = flattenVariantSummaries(summaries.flatMap { it.variantSummaries }, variants).filter { it.total > 0 }
      TestSuiteSummary(suiteName, variantSummaries)
    }
    .sortedBy { it.name }
}

/** Rolls up child node [TestSuiteSummary] lists into per-suite summaries plus the synthetic [AGGREGATED_TEST_SUITE_NAME] summary. */
internal fun rollupChildSuiteSummaries(childSummariesList: List<List<TestSuiteSummary>>): List<TestSuiteSummary> {
  val nonAggregatedChildren = childSummariesList.flatMap { list -> list.filter { it.name != AGGREGATED_TEST_SUITE_NAME } }
  val allVariants = nonAggregatedChildren.flatMap { ts -> ts.variantSummaries.map { v -> v.name } }.distinct().sorted()
  val merged = mergeTestSuiteSummaries(nonAggregatedChildren, allVariants).toMutableList()
  val aggregatedVariantSummaries = flattenVariantSummaries(merged.flatMap { it.variantSummaries }, allVariants)
  merged.add(TestSuiteSummary(AGGREGATED_TEST_SUITE_NAME, aggregatedVariantSummaries))
  return merged
}

/** Computes per-suite + [AGGREGATED_TEST_SUITE_NAME] summaries for a single target of a test case. */
internal fun summarizeTargetExecutions(targetExecutions: List<TestCaseExecution>): List<TestSuiteSummary> {
  val perSuite =
    targetExecutions
      .groupBy { it.testSuiteName }
      .map { (suiteName, suiteExecs) ->
        val winningExecs = deduplicateExecutions(suiteExecs) { it.variantName }
        summarizeWinningExecutions(suiteName, winningExecs)
      }
      .sortedBy { it.name }
      .toMutableList()
  val allVariants = targetExecutions.map { it.variantName }.distinct().sorted()
  perSuite.add(TestSuiteSummary(AGGREGATED_TEST_SUITE_NAME, flattenVariantSummaries(perSuite.flatMap { it.variantSummaries }, allVariants)))
  return perSuite
}

/** Computes per-suite summaries across all test cases of a class, deduplicating each `(variant, target)` pair per test case. */
internal fun summarizeClassExecutions(testCaseExecutionsList: Iterable<List<TestCaseExecution>>): List<TestSuiteSummary> {
  val executionsBySuite = mutableMapOf<String, MutableList<TestCaseExecution>>()
  for (tcExecs in testCaseExecutionsList) {
    tcExecs
      .groupBy { it.testSuiteName }
      .forEach { (suiteName, suiteExecs) ->
        val winning = deduplicateExecutions(suiteExecs) { it.variantName to it.targetName }
        executionsBySuite.getOrPut(suiteName) { mutableListOf() }.addAll(winning)
      }
  }
  return executionsBySuite.map { (suiteName, winningExecs) -> summarizeWinningExecutions(suiteName, winningExecs) }.sortedBy { it.name }
}

private fun <K> deduplicateExecutions(
  executions: List<TestCaseExecution>,
  keySelector: (TestCaseExecution) -> K,
): Collection<TestCaseExecution> {
  val winningByKey = LinkedHashMap<K, TestCaseExecution>()
  for (exec in executions) {
    val key = keySelector(exec)
    if (shouldReplaceExecution(winningByKey[key], exec)) {
      winningByKey[key] = exec
    }
  }
  return winningByKey.values
}

private fun summarizeWinningExecutions(suiteName: String, winningExecutions: Collection<TestCaseExecution>): TestSuiteSummary {
  val variants = winningExecutions.map { it.variantName }.distinct().sorted()
  val variantSummaries = variants.map { variant ->
    var passed = 0
    var failed = 0
    var skipped = 0
    var total = 0
    var durationSum = 0L
    var hasDuration = false
    for (exec in winningExecutions) {
      if (exec.variantName != variant) continue
      when (exec.status) {
        STATUS_PASS -> passed++
        STATUS_FAIL -> failed++
        STATUS_SKIPPED -> skipped++
      }
      total++
      if (exec.durationMs != null) {
        durationSum += exec.durationMs
        hasDuration = true
      }
    }
    buildVariantSummary(variant, passed, failed, skipped, total, if (hasDuration) durationSum else null)
  }
  return TestSuiteSummary(suiteName, variantSummaries)
}

private fun buildVariantSummary(
  name: String,
  passed: Int,
  failed: Int,
  skipped: Int,
  total: Int,
  durationMs: Long?,
): VariantSummary {
  val relevant = passed + failed
  val rate =
    when {
      relevant > 0 -> (passed.toDouble() / relevant) * 100.0
      total > 0 -> 0.0
      else -> 100.0
    }
  return VariantSummary(name, passed, failed, skipped, total, rate, durationMs)
}
