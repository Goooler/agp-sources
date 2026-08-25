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

import com.android.build.gradle.internal.LoggerWrapper
import com.google.common.annotations.VisibleForTesting
import com.google.gson.GsonBuilder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import org.gradle.internal.logging.ConsoleRenderer

/**
 * Aggregates test results from multiple XML report streams.
 *
 * The `getReport()` method converts this internal map structure into the final list-based [RootReport] data model for serialization.
 */
class XMLReportAggregator(private val files: List<File>, projectName: String) {

  private val logger = LoggerWrapper.getLogger(XMLReportAggregator::class.java)

  // Global set of all unique variant names encountered.
  private val rootReportBuilder = RootReportBuilder(projectName)

  /** The lazily evaluated and cached [RootReport] data model. */
  private val rootReport: RootReport by lazy {
    getInputFiles().forEach { file -> processXmlForAggregation(file) }
    getReport()
  }

  private val lazyTestCount: Int by lazy {
    rootReport.modules.sumOf { module ->
      module.testSuiteSummaries.filter { it.name == AGGREGATED_TEST_SUITE_NAME }.flatMap { it.variantSummaries }.sumOf { it.total }
    }
  }

  /** Generates the RootReport and writes it to the specified output directory along with the necessary JSON, JS, and HTML resources. */
  fun writeReport(outputDir: File) {
    val finalReport = generateReport()
    val gson = GsonBuilder().create()
    if (!outputDir.exists()) {
      outputDir.mkdirs()
    }

    File(outputDir, "data.js").bufferedWriter().use { writer ->
      writer.write("const TEST_DATA_SOURCE = ")
      gson.toJson(finalReport, writer)
    }

    val resources = listOf("index.html", "script.js", "styles.css")
    resources.forEach { fileName ->
      XMLReportAggregator::class.java.getResourceAsStream("/com/android/build/gradle/internal/test/report/renderer/$fileName")?.use {
        inputStream ->
        File(outputDir, fileName).outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
      }
    }

    // Log the final location of the report
    val reportLocation = ConsoleRenderer().asClickableFileUrl(File(outputDir, "index.html"))
    logger.quiet("Test report generated at: $reportLocation")
  }

  /** Returns the total test count from the aggregated report. */
  fun getTestCount(): Int = lazyTestCount

  /** Generates the final [RootReport] by returning the cached report property. */
  @VisibleForTesting fun generateReport(): RootReport = rootReport

  private fun getInputFiles(): List<File> = files

  /**
   * Processes all XML files in the given directory for aggregation.
   *
   * @param outputDir the directory containing XML report files
   */
  private fun processXmlForAggregation(outputDir: File) {
    if (!outputDir.exists()) {
      logger.warning("Test result output directory '${outputDir.absolutePath}' does not exist. Skipping aggregation for this directory.")
      return
    }

    outputDir
      .listFiles()
      ?.filter {
        it.isFile &&
          // We only want files with the "xml" extension
          it.extension == EXT_XML
      }
      ?.sortedBy { it.name }
      ?.forEach { xmlFile ->
        logger.verbose("Found XML file: ${xmlFile.name}")
        try {
          xmlFile.inputStream().use { inputStream -> processXmlStream(inputStream, xmlFile.name) }
        } catch (e: IOException) {
          logger.error(e, "Error reading file ${xmlFile.name}")
        } catch (e: Exception) {
          logger.error(e, "Error processing file ${xmlFile.name}")
        }
      }
  }

  /**
   * Parses a single XML stream and adds the results to the aggregated structure.
   *
   * @param inputStream the XML stream to parse
   * @param streamName optional name for the stream (used for logging)
   */
  private fun processXmlStream(inputStream: InputStream, streamName: String? = null) {
    var variantName: String? = null
    val factory = XMLInputFactory.newInstance()
    factory.setProperty(XMLInputFactory.IS_COALESCING, true)
    // Security: Disable DTDs and external entities to prevent XXE attacks
    try {
      factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    } catch (e: IllegalArgumentException) {
      logger.error(e, "Could not set some security properties on XMLInputFactory: ${e.message}")
    }

    try {
      inputStream.use { stream ->
        val reader = factory.createXMLStreamReader(stream)
        var modulePath: String? = null
        var testSuiteName: String? = null
        var testTarget: String? = null

        // Variables for the current test case being processed
        var currentClassname: String? = null
        var currentTestcaseName: String? = null
        var currentStatus = STATUS_PASS // Default status

        var currentDiffPercent: String? = null
        var currentPreviewName: String? = null
        var currentMethodName: String? = null
        var currentRefImagePath: String? = null
        var currentNewImagePath: String? = null
        var currentDiffImagePath: String? = null

        // Buffer to accumulate text content (stack trace)
        var failureBuffer: StringBuilder? = null

        while (reader.hasNext()) {
          when (reader.next()) {
            XMLStreamConstants.START_ELEMENT -> {
              when (reader.localName) {
                TAG_PROPERTY -> {
                  val name = reader.getAttributeValue(null, ATTR_NAME)
                  val value = reader.getAttributeValue(null, ATTR_VALUE)
                  if (name != null && value != null) {
                    if (currentTestcaseName != null) {
                      when (name) {
                        "PreviewScreenshot.diffPercent" -> currentDiffPercent = value
                        "PreviewScreenshot.previewName" -> currentPreviewName = value
                        "PreviewScreenshot.methodName" -> currentMethodName = value
                        "PreviewScreenshot.refImagePath" -> currentRefImagePath = value
                        "PreviewScreenshot.newImagePath" -> currentNewImagePath = value
                        "PreviewScreenshot.diffImagePath" -> currentDiffImagePath = value
                      }
                    } else {
                      when (name) {
                        KEY_MODULE_PATH -> modulePath = value
                        KEY_TEST_SUITE_NAME -> testSuiteName = value
                        KEY_TEST_TARGET -> testTarget = value
                        KEY_TESTED_VARIANT_NAME -> {
                          rootReportBuilder.addVariant(value)
                          variantName = value
                        }
                      }
                    }
                  }
                }
                TAG_TESTCASE -> {
                  currentClassname = reader.getAttributeValue(null, ATTR_CLASSNAME)
                  currentTestcaseName = reader.getAttributeValue(null, ATTR_NAME)
                  currentStatus = STATUS_PASS // Reset status for this new test
                  failureBuffer = null // Reset failure buffer
                  currentDiffPercent = null
                  currentPreviewName = null
                  currentMethodName = null
                  currentRefImagePath = null
                  currentNewImagePath = null
                  currentDiffImagePath = null
                }
                TAG_SKIPPED -> {
                  currentStatus = STATUS_SKIPPED
                }
                TAG_FAILURE,
                TAG_ERROR -> {
                  currentStatus = STATUS_FAIL
                  // Initialize buffer to capture stack trace text
                  failureBuffer = StringBuilder()
                }
              }
            }
            XMLStreamConstants.CHARACTERS -> {
              // If we are inside a failure/error tag, append text to buffer
              if (failureBuffer != null) {
                failureBuffer.append(reader.text)
              }
            }
            XMLStreamConstants.END_ELEMENT -> {
              if (reader.localName == TAG_TESTCASE) {
                if (
                  modulePath != null &&
                    testSuiteName != null &&
                    currentClassname != null &&
                    currentTestcaseName != null &&
                    variantName != null
                ) {

                  // Extract and clean stack trace
                  val stackTrace = failureBuffer?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                  val target = testTarget ?: UNKNOWN_TARGET
                  rootReportBuilder.addTarget(target)

                  addTestResult(
                    modulePath,
                    testSuiteName,
                    currentClassname,
                    currentTestcaseName,
                    variantName,
                    target,
                    currentStatus,
                    stackTrace,
                    currentDiffPercent,
                    currentPreviewName,
                    currentMethodName,
                    currentRefImagePath,
                    currentNewImagePath,
                    currentDiffImagePath,
                  )
                }
                // Clear testcase-specific data
                currentClassname = null
                currentTestcaseName = null
                failureBuffer = null
                currentDiffPercent = null
                currentPreviewName = null
                currentMethodName = null
                currentRefImagePath = null
                currentNewImagePath = null
                currentDiffImagePath = null
              }
            }
          }
        }
        reader.close()
      }
    } catch (e: XMLStreamException) {
      val location = streamName ?: "stream"
      logger.error(e, "Error parsing XML for variant '$variantName' in $location")
    } catch (e: Exception) {
      val location = streamName ?: "stream"
      logger.error(e, "An unexpected error occurred for variant '$variantName' in $location")
    }
  }

  /** Converts the internal aggregated structure into the final [RootReport] data model, ready for JSON serialization. */
  private fun getReport(): RootReport {
    return rootReportBuilder.build()
  }

  private class RootReportBuilder(private val projectName: String) {
    private val variants = HashSet<String>()
    private val targets = HashSet<String>()
    private val moduleBuilders = mutableMapOf<String, ModuleBuilder>()

    fun addVariant(variant: String) {
      variants.add(variant)
    }

    fun addTarget(target: String) {
      targets.add(target)
    }

    fun getOrAddModule(name: String) = moduleBuilders.getOrPut(name) { ModuleBuilder(name) }

    fun build(): RootReport {
      val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
      val zonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
      val formattedTimestamp = zonedDateTime.format(formatter)
      val modules = moduleBuilders.values.map { it.build() }.sortedBy { it.name }

      val numberOfModules = modules.size
      val numberOfPackages = modules.sumOf { it.packages.size }
      val numberOfClasses = modules.sumOf { module -> module.packages.sumOf { pkg -> pkg.classes.size } }

      val testSuites = modules.flatMap { it.testSuiteSummaries.map { ts -> ts.name } }.distinct().sorted()
      val targetsSorted = if (targets.isEmpty()) listOf(UNKNOWN_TARGET) else targets.sorted()

      return RootReport(
        projectName = projectName,
        timestamp = formattedTimestamp,
        numberOfModules = numberOfModules,
        numberOfPackages = numberOfPackages,
        numberOfClasses = numberOfClasses,
        variants = variants.sorted(),
        testSuites = testSuites,
        targets = targetsSorted,
        modules = modules,
      )
    }
  }

  private class ModuleBuilder(val name: String) {
    val packages = mutableMapOf<String, PackageBuilder>()

    fun getOrAddPackage(name: String) = packages.getOrPut(name) { PackageBuilder(name) }

    fun build(): Module {
      val pkgs = packages.values.map { it.build() }.sortedBy { it.name }
      val allVariants =
        pkgs
          .flatMap { p ->
            p.testSuiteSummaries.filter { it.name != AGGREGATED_TEST_SUITE_NAME }.flatMap { ts -> ts.variantSummaries.map { v -> v.name } }
          }
          .distinct()
          .sorted()
      val testSuiteSummaries =
        mergeTestSuiteSummaries(pkgs.flatMap { it.testSuiteSummaries.filter { it.name != AGGREGATED_TEST_SUITE_NAME } }, allVariants)
          .toMutableList()
      val aggregatedVariantSummaries = testSuiteSummaries.flatMap { it.variantSummaries }.let { flattenVariantSummaries(it, allVariants) }
      testSuiteSummaries.add(TestSuiteSummary(AGGREGATED_TEST_SUITE_NAME, aggregatedVariantSummaries))
      return Module(name = name, testSuiteSummaries = testSuiteSummaries, packages = pkgs)
    }
  }

  private class PackageBuilder(val name: String) {
    val classes = mutableMapOf<String, ClassBuilder>()

    fun getOrAddClass(name: String) = classes.getOrPut(name) { ClassBuilder(name) }

    fun build(): Package {
      val clzs = classes.values.map { it.build() }.sortedBy { it.name }
      val allVariants =
        clzs
          .flatMap { c ->
            c.testSuiteSummaries.filter { it.name != AGGREGATED_TEST_SUITE_NAME }.flatMap { ts -> ts.variantSummaries.map { v -> v.name } }
          }
          .distinct()
          .sorted()
      val testSuiteSummaries =
        mergeTestSuiteSummaries(clzs.flatMap { it.testSuiteSummaries.filter { it.name != AGGREGATED_TEST_SUITE_NAME } }, allVariants)
          .toMutableList()
      val aggregatedVariantSummaries = testSuiteSummaries.flatMap { it.variantSummaries }.let { flattenVariantSummaries(it, allVariants) }
      testSuiteSummaries.add(TestSuiteSummary(AGGREGATED_TEST_SUITE_NAME, aggregatedVariantSummaries))
      return Package(name = name, testSuiteSummaries = testSuiteSummaries, classes = clzs)
    }
  }

  private class ClassBuilder(val name: String) {
    val testCases = mutableMapOf<String, TestCaseBuilder>()

    fun getOrAddTestCase(name: String) = testCases.getOrPut(name) { TestCaseBuilder(name) }

    fun build(): ClassType {
      val testCasesList = testCases.values.map { it.build() }.sortedBy { it.name }
      val testSuiteSummaries = calculateTestSuiteSummaries(testCases.values).toMutableList()
      val allVariants =
        testCasesList
          .flatMap { tc ->
            tc.targets.flatMap { target ->
              target.testSuiteSummaries
                .filter { it.name != AGGREGATED_TEST_SUITE_NAME }
                .flatMap { ts -> ts.variantSummaries.map { v -> v.name } }
            }
          }
          .distinct()
          .sorted()
      val aggregatedVariantSummaries = testSuiteSummaries.flatMap { it.variantSummaries }.let { flattenVariantSummaries(it, allVariants) }
      testSuiteSummaries.add(TestSuiteSummary(AGGREGATED_TEST_SUITE_NAME, aggregatedVariantSummaries))
      return ClassType(name = name, testSuiteSummaries = testSuiteSummaries, testCases = testCasesList)
    }
  }

  private data class TestCaseExecution(
    val testSuiteName: String,
    val variantName: String,
    val targetName: String,
    val status: String,
    val stackTrace: String?,
    val diffPercent: String?,
    val previewName: String?,
    val methodName: String?,
    val refImagePath: String?,
    val newImagePath: String?,
    val diffImagePath: String?,
  )

  private class TestCaseBuilder(val name: String) {
    val executions = mutableListOf<TestCaseExecution>()

    fun addResult(
      testSuiteName: String,
      variantName: String,
      targetName: String,
      status: String,
      stackTrace: String?,
      diffPercent: String?,
      previewName: String?,
      methodName: String?,
      refImagePath: String?,
      newImagePath: String?,
      diffImagePath: String?,
    ) {
      executions.add(
        TestCaseExecution(
          testSuiteName,
          variantName,
          targetName,
          status,
          stackTrace,
          diffPercent,
          previewName,
          methodName,
          refImagePath,
          newImagePath,
          diffImagePath,
        )
      )
    }

    fun build(): TestCase {
      val targetsList =
        executions
          .groupBy { it.targetName }
          .map { (targetName, targetExecutions) ->
            val stackTraceGroups = mutableListOf<StackTraceGroup>()
            val stackTraceToId = mutableMapOf<String, String>()

            targetExecutions
              .filter { it.stackTrace != null }
              .groupBy { it.stackTrace!! }
              .forEach { (stackTrace, execs) ->
                val id = "st-${UUID.nameUUIDFromBytes(stackTrace.toByteArray(Charsets.UTF_8))}"
                stackTraceToId[stackTrace] = id
                val occurrences = execs.groupBy({ it.testSuiteName }, { it.variantName }).mapValues { it.value.distinct() }
                stackTraceGroups.add(StackTraceGroup(id, stackTrace, occurrences))
              }

            val testSuiteResults =
              targetExecutions
                .groupBy { it.testSuiteName }
                .map { (suiteName, suiteExecs) ->
                  val variantResults = mutableMapOf<String, VariantTestResult>()
                  suiteExecs.forEach { exec ->
                    val existing = variantResults[exec.variantName]
                    // Prioritize statuses: Fail > Pass > Skipped. This ensures flaky tests are surfaced
                    // to the user and that successful retries are favored over skips.
                    if (
                      existing == null ||
                        (existing.status != STATUS_FAIL && exec.status == STATUS_FAIL) ||
                        (existing.status == STATUS_SKIPPED && exec.status == STATUS_PASS)
                    ) {
                      variantResults[exec.variantName] =
                        VariantTestResult(
                          status = exec.status,
                          stackTraceId = exec.stackTrace?.let { stackTraceToId[it] },
                          diffPercent = exec.diffPercent,
                          previewName = exec.previewName,
                          methodName = exec.methodName,
                          refImagePath = exec.refImagePath,
                          newImagePath = exec.newImagePath,
                          diffImagePath = exec.diffImagePath,
                        )
                    }
                  }
                  TestSuiteTestResult(suiteName, variantResults)
                }

            val testSuiteSummaries = calculateTestCaseTestSuiteSummaries(targetExecutions).toMutableList()
            val allVariants = targetExecutions.map { it.variantName }.distinct().sorted()
            val aggregatedVariantSummaries =
              testSuiteSummaries.flatMap { it.variantSummaries }.let { flattenVariantSummaries(it, allVariants) }
            testSuiteSummaries.add(TestSuiteSummary(AGGREGATED_TEST_SUITE_NAME, aggregatedVariantSummaries))

            Target(
              name = targetName,
              testSuiteSummaries = testSuiteSummaries,
              testSuiteResults = testSuiteResults,
              commonStackTraces = stackTraceGroups,
            )
          }
          .sortedBy { it.name }

      return TestCase(name = name, targets = targetsList)
    }

    private fun calculateTestCaseTestSuiteSummaries(targetExecutions: List<TestCaseExecution>): List<TestSuiteSummary> {
      return targetExecutions
        .groupBy { it.testSuiteName }
        .map { (suiteName, suiteExecs) ->
          val variants = suiteExecs.map { it.variantName }.distinct().sorted()
          val variantSummaries =
            variants.map { variant ->
              val variantExecs = suiteExecs.filter { it.variantName == variant }

              // Deduplicate within the suite for the summary, prioritizing failures
              var finalStatus = STATUS_SKIPPED
              variantExecs.forEach { exec ->
                if (exec.status == STATUS_FAIL) finalStatus = STATUS_FAIL
                else if (exec.status == STATUS_PASS && finalStatus != STATUS_FAIL) finalStatus = STATUS_PASS
              }

              var vPassed = 0
              var vFailed = 0
              var vSkipped = 0
              when (finalStatus) {
                STATUS_PASS -> vPassed++
                STATUS_FAIL -> vFailed++
                STATUS_SKIPPED -> vSkipped++
              }

              val vRelevant = vPassed + vFailed
              val vRate = if (vRelevant > 0) (vPassed.toDouble() / vRelevant) * 100.0 else 0.0
              VariantSummary(variant, vPassed, vFailed, vSkipped, 1, vRate)
            }
          TestSuiteSummary(suiteName, variantSummaries)
        }
        .sortedBy { it.name }
    }
  }

  companion object {

    private fun flattenVariantSummaries(summaries: List<VariantSummary>, variants: List<String>): List<VariantSummary> {
      return variants.map { variant ->
        var vPassed = 0
        var vFailed = 0
        var vSkipped = 0
        var vTotal = 0
        summaries
          .filter { it.name == variant }
          .forEach {
            vPassed += it.passed
            vFailed += it.failed
            vSkipped += it.skipped
            vTotal += it.total
          }
        val vRelevant = vPassed + vFailed
        val vRate = if (vRelevant > 0) (vPassed.toDouble() / vRelevant) * 100.0 else if (vTotal > 0) 0.0 else 100.0
        VariantSummary(variant, vPassed, vFailed, vSkipped, vTotal, vRate)
      }
    }

    private fun mergeTestSuiteSummaries(childrenSuiteSummaries: List<TestSuiteSummary>, variants: List<String>): List<TestSuiteSummary> {
      return childrenSuiteSummaries
        .groupBy { it.name }
        .map { (suiteName, summaries) ->
          val variantSummaries =
            variants
              .map { variant ->
                var vPassed = 0
                var vFailed = 0
                var vSkipped = 0
                var vTotal = 0

                summaries.forEach { summary ->
                  summary.variantSummaries
                    .find { it.name == variant }
                    ?.let {
                      vPassed += it.passed
                      vFailed += it.failed
                      vSkipped += it.skipped
                      vTotal += it.total
                    }
                }

                val vRelevant = vPassed + vFailed
                val vRate = if (vRelevant > 0) (vPassed.toDouble() / vRelevant) * 100.0 else if (vTotal > 0) 0.0 else 100.0
                VariantSummary(variant, vPassed, vFailed, vSkipped, vTotal, vRate)
              }
              .filter { it.total > 0 }
          TestSuiteSummary(suiteName, variantSummaries)
        }
        .sortedBy { it.name }
    }

    private fun calculateTestSuiteSummaries(testCaseBuilders: Collection<TestCaseBuilder>): List<TestSuiteSummary> {
      val executionsBySuite = mutableMapOf<String, MutableList<TestCaseExecution>>()
      testCaseBuilders.forEach { tc ->
        val suiteToVariantExec = mutableMapOf<String, MutableMap<String, TestCaseExecution>>()
        tc.executions.forEach { exec ->
          val existingMap = suiteToVariantExec.getOrPut(exec.testSuiteName) { mutableMapOf() }
          val key = "${exec.variantName}_${exec.targetName}"
          val existing = existingMap[key]
          // Prioritize statuses: Fail > Pass > Skipped. This ensures flaky tests are surfaced
          // to the user and that successful retries are favored over skips.
          if (
            existing == null ||
              (existing.status != STATUS_FAIL && exec.status == STATUS_FAIL) ||
              (existing.status == STATUS_SKIPPED && exec.status == STATUS_PASS)
          ) {
            existingMap[key] = exec
          }
        }

        suiteToVariantExec.values.forEach { variantMap ->
          variantMap.values.forEach { exec -> executionsBySuite.getOrPut(exec.testSuiteName) { mutableListOf() }.add(exec) }
        }
      }

      return executionsBySuite
        .map { (suiteName, executions) ->
          val variants = executions.map { it.variantName }.distinct().sorted()
          val variantSummaries =
            variants.map { variant ->
              var vPassed = 0
              var vFailed = 0
              var vSkipped = 0
              var vTotal = 0

              executions
                .filter { it.variantName == variant }
                .forEach { exec ->
                  when (exec.status) {
                    STATUS_PASS -> vPassed++
                    STATUS_FAIL -> vFailed++
                    STATUS_SKIPPED -> vSkipped++
                  }
                  vTotal++
                }

              val vRelevant = vPassed + vFailed
              val vRate = if (vRelevant > 0) (vPassed.toDouble() / vRelevant) * 100.0 else if (vTotal > 0) 0.0 else 100.0
              VariantSummary(variant, vPassed, vFailed, vSkipped, vTotal, vRate)
            }

          TestSuiteSummary(suiteName, variantSummaries)
        }
        .sortedBy { it.name }
    }
  }

  /** Safely adds a test result to the nested structure. */
  private fun addTestResult(
    modulePath: String,
    testSuiteName: String,
    classname: String,
    testcaseName: String,
    variantName: String,
    targetName: String,
    status: String,
    stackTrace: String?,
    diffPercent: String?,
    previewName: String?,
    methodName: String?,
    refImagePath: String?,
    newImagePath: String?,
    diffImagePath: String?,
  ) {
    try {
      val packageName = classname.substringBeforeLast('.', "")
      val className = classname.substringAfterLast('.')

      rootReportBuilder
        .getOrAddModule(modulePath)
        .getOrAddPackage(packageName)
        .getOrAddClass(className)
        .getOrAddTestCase(testcaseName)
        .addResult(
          testSuiteName,
          variantName,
          targetName,
          status,
          stackTrace,
          diffPercent,
          previewName,
          methodName,
          refImagePath,
          newImagePath,
          diffImagePath,
        )
    } catch (e: Exception) {
      logger.error(e, "Error processing test case: $classname.$testcaseName")
    }
  }
}
