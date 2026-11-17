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

import java.math.RoundingMode

/**
 * Renders a [RootReport] as Markdown, the content of `report.md`.
 *
 * The report is meant to be read by coding agents and in terminals, so it stays small: a YAML front matter block with the totals, a summary
 * table per module, test suite and variant, failing tests with their stack traces, screenshot failures with links to their images, and
 * passed tests as counts per class rather than one row per test. Each unique stack trace is printed once, and long ones are truncated.
 */
class MarkdownReportRenderer {

  /** Returns the Markdown for [report]. */
  fun render(report: RootReport): String {
    val executions = collectExecutions(report)
    val markdown = buildString {
      appendFrontMatter(report, executions)
      appendLine("# Test report: ${report.projectName}")
      appendLine()
      if (executions.isEmpty()) {
        appendLine("No test results were found.")
        return@buildString
      }
      val screenshotFailures = executions.mapNotNull { run -> classifyScreenshotFailure(run)?.let { run to it } }
      val screenshotFailureRuns = screenshotFailures.map { it.first }.toSet()
      appendSummary(executions)
      appendFailures(executions.filter { it.status == STATUS_FAIL && it !in screenshotFailureRuns })
      appendScreenshotFailures(screenshotFailures, report.relativeRootDir)
      appendPassed(executions)
    }
    // Each section ends with a blank line; end the file with a single newline instead.
    return markdown.trimEnd('\n') + "\n"
  }

  private fun StringBuilder.appendFrontMatter(report: RootReport, executions: List<Execution>) {
    appendLine("---")
    appendLine("project: \"${escapeYaml(report.projectName)}\"")
    appendLine("total: ${executions.size}")
    appendLine("passed: ${executions.count { it.status == STATUS_PASS }}")
    appendLine("failed: ${executions.count { it.status == STATUS_FAIL }}")
    appendLine("skipped: ${executions.count { it.status == STATUS_SKIPPED }}")
    appendLine("---")
    appendLine()
  }

  private fun StringBuilder.appendSummary(executions: List<Execution>) {
    appendLine("## Summary")
    appendLine()
    appendLine("| Module | Suite | Variant | Total | Passed | Failed | Skipped |")
    appendLine("| :--- | :--- | :--- | ---: | ---: | ---: | ---: |")
    executions
      .groupBy { Triple(it.module, it.suite, it.variant) }
      .toSortedMap(compareBy({ it.first }, { it.second }, { it.third }))
      .forEach { (key, group) ->
        val (module, suite, variant) = key
        appendLine(
          "| ${cell(module)} | ${cell(suite)} | ${cell(variant)} | ${group.size} | ${group.count { it.status == STATUS_PASS }} | " +
            "${group.count { it.status == STATUS_FAIL }} | ${group.count { it.status == STATUS_SKIPPED }} |"
        )
      }
    appendLine()
  }

  /**
   * Appends one entry per failing test. Runs of the same test that failed with the same stack trace, for example in several variants, share
   * an entry, and a stack trace that was already printed for another test is referenced instead of printed again. Each entry's title starts
   * with the module, so tests with the same class name in different modules can be told apart.
   */
  private fun StringBuilder.appendFailures(failures: List<Execution>) {
    if (failures.isEmpty()) return
    appendLine("## Failures (${failures.size})")
    appendLine()
    val firstTestWithStackTrace = mutableMapOf<String, String>()
    failures
      .groupBy { FailureKey(it.module, it.className, it.testName, it.stackTrace) }
      .entries
      .sortedWith(compareBy({ it.key.module }, { it.key.className }, { it.key.testName }, { it.key.stackTrace.orEmpty() }))
      .forEach { (key, runs) ->
        val title = testTitle(key.module, key.className, key.testName)
        val stackTrace = key.stackTrace
        appendLine("### $title")
        appendLine()
        appendLine("Failed in: ${runs.map(::describeRun).distinct().sorted().joinToString(", ")}.")
        appendLine()
        when {
          stackTrace == null -> appendLine("No stack trace was recorded.")
          stackTrace in firstTestWithStackTrace -> appendLine("Same stack trace as ${firstTestWithStackTrace.getValue(stackTrace)}.")
          else -> {
            firstTestWithStackTrace[stackTrace] = title
            appendCodeBlock(truncateStackTrace(stackTrace))
          }
        }
        appendLine()
      }
  }

  /**
   * Appends a table of screenshot comparison failures with links to their images. Image paths in the model are relative to the root
   * project, so they're prefixed with [relativeRootDir] to make them relative to the report.
   */
  private fun StringBuilder.appendScreenshotFailures(failures: List<Pair<Execution, ScreenshotFailure>>, relativeRootDir: String) {
    if (failures.isEmpty()) return
    appendLine("## Screenshot failures (${failures.size})")
    appendLine()
    appendLine("| Test | Variant | Result | Diff | Reference | Actual | Diff image |")
    appendLine("| :--- | :--- | :--- | ---: | :--- | :--- | :--- |")
    failures.sortedWith(compareBy({ it.first.module }, { it.first.className }, { it.first.testName }, { it.first.variant })).forEach {
      (run, failure) ->
      val result = run.result
      // A missing baseline has no reference to compare with, so any reference or diff path is stale.
      val isMismatch = failure == ScreenshotFailure.MISMATCH
      val diff = if (isMismatch) result.diffPercent?.let(::formatDiffPercent).orEmpty() else ""
      val reference = image("Reference", result.refImagePath.takeIf { isMismatch }, relativeRootDir)
      val actual = image("Actual", result.newImagePath, relativeRootDir)
      val diffImage = image("Diff", result.diffImagePath.takeIf { isMismatch }, relativeRootDir)
      val test = cell(testTitle(run.module, run.className, run.testName))
      appendLine("| $test | ${cell(run.variant)} | ${failure.label} | $diff | $reference | $actual | $diffImage |")
    }
    appendLine()
  }

  private fun StringBuilder.appendPassed(executions: List<Execution>) {
    val passedByClass = executions.filter { it.status == STATUS_PASS }.groupingBy { it.className }.eachCount().toSortedMap()
    if (passedByClass.isEmpty()) return
    appendLine("## Passed (${passedByClass.values.sum()})")
    appendLine()
    appendLine("| Class | Passed |")
    appendLine("| :--- | ---: |")
    passedByClass.forEach { (className, count) -> appendLine("| ${cell(className)} | $count |") }
    appendLine()
  }

  /** One run of a test case: a test case on one target, in one test suite and variant. */
  internal data class Execution(
    val module: String,
    val className: String,
    val testName: String,
    val target: String,
    val suite: String,
    val variant: String,
    val status: String,
    val result: VariantTestResult,
    val stackTrace: String?,
  )

  /** Groups runs of the same failing test that share a stack trace. */
  private data class FailureKey(val module: String, val className: String, val testName: String, val stackTrace: String?)

  /** How a screenshot test failed, as reported by the screenshot test engine. */
  internal enum class ScreenshotFailure(val label: String) {
    /** The rendered image differs from the reference image. */
    MISMATCH("Mismatch"),
    /** There is no reference image to compare with. */
    MISSING_BASELINE("Missing baseline"),
  }

  internal companion object {

    /** Identifies a test across modules, for example `:app > com.example.MathTest > divides`. */
    fun testTitle(module: String, className: String, testName: String): String = "$module > $className > $testName"

    /**
     * Classifies a failed screenshot test by the exception the screenshot test engine threw, or returns null if [run] isn't a screenshot
     * comparison failure (for example, a rendering error).
     *
     * The image properties alone can't tell the cases apart: the engine always publishes `refImagePath`, and a diff image from an earlier
     * run may still exist.
     */
    fun classifyScreenshotFailure(run: Execution): ScreenshotFailure? {
      val result = run.result
      if (run.status != STATUS_FAIL) return null
      if (result.refImagePath == null && result.newImagePath == null && result.diffImagePath == null) return null
      val exceptionLine = run.stackTrace?.lineSequence()?.firstOrNull().orEmpty()
      return when {
        exceptionLine.contains("ImageComparisonAssertionError") -> ScreenshotFailure.MISMATCH
        exceptionLine.contains("ScreenshotImageNotFoundException") && exceptionLine.contains("Reference image file does not exist") ->
          ScreenshotFailure.MISSING_BASELINE
        else -> null
      }
    }

    /**
     * Returns a Markdown image for [path], or an empty string if there's no path. Relative paths are resolved against [relativeRootDir];
     * paths with spaces or parentheses are wrapped in angle brackets so the link still parses.
     */
    fun image(label: String, path: String?, relativeRootDir: String): String {
      if (path.isNullOrBlank()) return ""
      val normalized = path.replace('\\', '/')
      val isAbsolute = normalized.startsWith("/") || Regex("^[A-Za-z]:/").containsMatchIn(normalized)
      val link = if (isAbsolute) normalized else relativeRootDir + normalized
      val target = if (link.any { it.isWhitespace() || it == '(' || it == ')' }) "<$link>" else link
      return "![$label]($target)"
    }

    /**
     * Formats a screenshot diff for display. The screenshot test engine reports the fraction of pixels that differ (`0.0482` for 4.82%), so
     * it's multiplied by 100. The value is parsed as a decimal rather than a double, so there are no binary rounding artifacts. A value
     * that isn't a finite number, such as `NaN`, is returned unchanged.
     */
    fun formatDiffPercent(diffFraction: String): String =
      diffFraction.toBigDecimalOrNull()?.movePointRight(2)?.setScale(2, RoundingMode.HALF_EVEN)?.let { "${it.toPlainString()}%" }
        ?: diffFraction

    /** Stack traces longer than this many lines are truncated. */
    const val MAX_STACK_TRACE_LINES = 40

    /** Number of leading lines kept from a truncated stack trace, besides its `Caused by:` lines. */
    const val KEPT_STACK_TRACE_LINES = 25

    /**
     * Returns [stackTrace] unchanged if it has at most [MAX_STACK_TRACE_LINES] lines. Otherwise, keeps the first [KEPT_STACK_TRACE_LINES]
     * lines and every later `Caused by:` line, so the root cause is still visible.
     */
    fun truncateStackTrace(stackTrace: String): String {
      val lines = stackTrace.lines()
      if (lines.size <= MAX_STACK_TRACE_LINES) return stackTrace
      val causes = lines.drop(KEPT_STACK_TRACE_LINES).filter { it.trimStart().startsWith("Caused by:") }
      val omitted = lines.size - KEPT_STACK_TRACE_LINES - causes.size
      return (lines.take(KEPT_STACK_TRACE_LINES) + "\t... $omitted lines omitted" + causes).joinToString("\n")
    }

    /** Describes where [run] ran, for example `unitTest / debug` or `connected / debug on Pixel_8`. */
    fun describeRun(run: Execution): String =
      if (run.target == UNKNOWN_TARGET) "${run.suite} / ${run.variant}" else "${run.suite} / ${run.variant} on ${run.target}"

    /** Appends [text] as a fenced code block, with a fence longer than any run of backticks in [text]. */
    fun StringBuilder.appendCodeBlock(text: String) {
      val longestBacktickRun = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
      val fence = "`".repeat(maxOf(3, longestBacktickRun + 1))
      appendLine(fence)
      appendLine(text)
      appendLine(fence)
    }

    /** Flattens [report] into one [Execution] per test case, target, test suite and variant. */
    fun collectExecutions(report: RootReport): List<Execution> =
      report.modules.flatMap { module ->
        module.packages.flatMap { pkg ->
          pkg.classes.flatMap { clazz ->
            val className = if (pkg.name.isEmpty()) clazz.name else "${pkg.name}.${clazz.name}"
            clazz.testCases.flatMap { testCase ->
              testCase.targets.flatMap { target ->
                val stackTraces = target.commonStackTraces.associate { it.id to it.stackTrace }
                target.testSuiteResults.flatMap { suiteResult ->
                  suiteResult.variantResults.map { (variant, result) ->
                    Execution(
                      module = module.name,
                      className = className,
                      testName = testCase.name,
                      target = target.name,
                      suite = suiteResult.testSuiteName,
                      variant = variant,
                      status = result.status,
                      result = result,
                      stackTrace = result.stackTraceId?.let { stackTraces[it] },
                    )
                  }
                }
              }
            }
          }
        }
      }

    /** Escapes [text] for use in a Markdown table cell. */
    fun cell(text: String): String = text.replace("|", "\\|").replace(Regex("\\s*\\R\\s*"), " ")

    /** Escapes [text] for use in a double-quoted YAML string. */
    fun escapeYaml(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"")
  }
}
