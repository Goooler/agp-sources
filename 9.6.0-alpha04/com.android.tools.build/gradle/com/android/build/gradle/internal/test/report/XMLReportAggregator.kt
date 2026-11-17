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
import com.android.build.gradle.internal.coverage.renderer.utils.openUncachedStream
import com.android.utils.FileUtils
import com.android.utils.ILogger
import com.android.utils.XmlUtils
import com.google.common.annotations.VisibleForTesting
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.IdentityHashMap
import java.util.Locale
import java.util.TreeMap
import java.util.UUID
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import kotlin.math.roundToLong
import org.gradle.internal.logging.ConsoleRenderer

/** Aggregates JUnit XML reports and test attachments into a self-contained HTML test report. */
class XMLReportAggregator
internal constructor(
  private val files: List<File>,
  projectName: String,
  private val rootDir: File,
  private val adapterRegistry: SuiteReportAdapterRegistry,
  private val logger: ILogger,
) {

  constructor(
    files: List<File>,
    projectName: String,
    rootDir: File,
  ) : this(
    files = files,
    projectName = projectName,
    rootDir = rootDir,
    adapterRegistry = SuiteReportAdapterRegistry(),
    logger = LoggerWrapper.getLogger(XMLReportAggregator::class.java),
  )

  private val gson: Gson = GsonBuilder().create()

  private val warnedDetailSchemaVersions = HashSet<Int>()

  private val rootReportBuilder = RootReportBuilder(projectName, adapterRegistry, logger)

  /** Caches the lazily aggregated [AggregatedReportBundle] across report queries and writes. */
  private val aggregatedBundle: AggregatedReportBundle by lazy {
    getInputFiles().forEach { file -> processXmlForAggregation(file) }
    rootReportBuilder.build()
  }

  private val rootReport: RootReport
    get() = aggregatedBundle.report

  private val lazyTestCount: Int by lazy {
    rootReport.modules.sumOf { module ->
      module.testSuiteSummaries.filter { it.name == AGGREGATED_TEST_SUITE_NAME }.flatMap { it.variantSummaries }.sumOf { it.total }
    }
  }

  /**
   * Writes `data.js`, lazy `details/<id>.js` scripts, copied `artifacts/`, and renderer assets to [outputDir].
   *
   * @param writeMarkdown whether to also write a Markdown version of the report, [MARKDOWN_REPORT_FILE_NAME], to the same directory
   */
  @JvmOverloads
  fun writeReport(outputDir: File, writeMarkdown: Boolean = false) {
    if (!outputDir.exists()) {
      outputDir.mkdirs()
    }
    FileUtils.deleteRecursivelyIfExists(File(outputDir, "details"))
    FileUtils.deleteRecursivelyIfExists(File(outputDir, "artifacts"))

    val bundle = aggregatedBundle
    val finalReport = bundle.report.copy(relativeRootDir = calculateRelativeRootDir(outputDir, rootDir))

    File(outputDir, "data.js").bufferedWriter(Charsets.UTF_8).use { writer ->
      writer.write("const TEST_DATA_SOURCE = ")
      gson.toJson(finalReport, writer)
    }

    RENDERER_RESOURCES.forEach { relativePath ->
      val resourcePath = "/com/android/build/gradle/internal/test/report/renderer/$relativePath"
      val stream = XMLReportAggregator::class.java.getResource(resourcePath)?.let { openUncachedStream(it) }
      if (stream == null) {
        logger.warning("Missing HTML test report renderer resource: $resourcePath")
      } else {
        stream.use { inputStream ->
          val destFile = File(outputDir, relativePath)
          destFile.parentFile?.mkdirs()
          destFile.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
          }
        }
      }
    }

    bundle.generatedFiles.forEach { spec ->
      val destFile = File(outputDir, spec.relativePath)
      destFile.parentFile?.mkdirs()
      destFile.writeText(spec.content, Charsets.UTF_8)
    }

    bundle.copiedFiles.forEach { spec ->
      try {
        val destFile = File(outputDir, spec.relativeDestPath)
        destFile.parentFile?.mkdirs()
        spec.sourceFile.copyTo(destFile, overwrite = true)
      } catch (e: IOException) {
        logger.warning("Failed to copy report artifact '${spec.sourceFile.name}': ${e.message}")
      }
    }

    if (bundle.lazyEnvDetails.isNotEmpty() || bundle.lazyTestDetails.isNotEmpty()) {
      val detailsDir = File(outputDir, "details").apply { mkdirs() }
      bundle.lazyEnvDetails.forEach { (envId, envDetail) ->
        writeLazyDetailScript(detailsDir, envId, envDetail)
      }
      bundle.lazyTestDetails.forEach { (detailId, pendingDetail) ->
        val lazyLogs =
          pendingDetail.logs.map { spec ->
            val (content, previewTruncated) = readLogFileCapped(spec.sourceFile, MAX_INLINE_LOG_BYTES, logger)
            LazyLogEntry(
              title = spec.title,
              content = content,
              lineCount = spec.lineCount,
              capturedTruncated = spec.capturedTruncated,
              previewTruncated = previewTruncated,
              artifactPath = spec.artifactPath,
            )
          }
        val detailPayload =
          LazyTestDetail(
            logs = lazyLogs,
            systemOut = pendingDetail.systemOut,
            systemOutTruncated = pendingDetail.systemOutTruncated,
            systemErr = pendingDetail.systemErr,
            systemErrTruncated = pendingDetail.systemErrTruncated,
          )
        writeLazyDetailScript(detailsDir, detailId, detailPayload)
      }
    }

    val reportLocation = ConsoleRenderer().asClickableFileUrl(File(outputDir, "index.html"))
    logger.quiet("Test report generated at: $reportLocation")

    if (writeMarkdown) {
      writeMarkdownReport(finalReport, File(outputDir, MARKDOWN_REPORT_FILE_NAME))
    }
  }

  /** Writes [report] as Markdown to [markdownFile]. A failure is logged rather than thrown, so it never fails the test task. */
  private fun writeMarkdownReport(report: RootReport, markdownFile: File) {
    try {
      markdownFile.writeText(MarkdownReportRenderer().render(report))
      logger.quiet("Markdown test report generated at: ${ConsoleRenderer().asClickableFileUrl(markdownFile)}")
    } catch (e: Exception) {
      logger.warning("Unable to write the Markdown test report to %s: %s", markdownFile.absolutePath, e.message)
    }
  }

  private fun writeLazyDetailScript(detailsDir: File, id: String, payload: Any) {
    File(detailsDir, "$id.js").bufferedWriter(Charsets.UTF_8).use { writer ->
      writer.write("window.__AGP_TEST_DETAILS = window.__AGP_TEST_DETAILS || {};\n")
      writer.write("window.__AGP_TEST_DETAILS[${gson.toJson(id)}] = ")
      gson.toJson(payload, writer)
      writer.write(";\n")
    }
  }

  /** Returns the total test count from the aggregated report. */
  fun getTestCount(): Int = lazyTestCount

  /** Returns the number of failed tests in the aggregated report. */
  fun getFailedTestCount(): Int =
    rootReport.modules.sumOf { module ->
      module.testSuiteSummaries.filter { it.name == AGGREGATED_TEST_SUITE_NAME }.flatMap { it.variantSummaries }.sumOf { it.failed }
    }

  /** Generates the final [RootReport] resolved relative to [outputDir]. */
  @VisibleForTesting
  fun generateReport(outputDir: File = rootDir): RootReport {
    val relativeRoot = calculateRelativeRootDir(outputDir, rootDir)
    return rootReport.copy(relativeRootDir = relativeRoot)
  }

  private fun getInputFiles(): List<File> = files

  /**
   * Processes JUnit XML files in [outputDir] for aggregation.
   *
   * @param outputDir the directory containing XML report files and optional attachments
   */
  private fun processXmlForAggregation(outputDir: File) {
    if (!outputDir.exists()) {
      logger.warning("Test result output directory '${outputDir.absolutePath}' does not exist. Skipping aggregation for this directory.")
      return
    }

    val xmlFiles = outputDir.listFiles()?.filter { it.isFile && it.extension == EXT_XML }?.sortedBy { it.name } ?: return

    xmlFiles.forEach { xmlFile ->
      logger.verbose("Found XML file: ${xmlFile.name}")
      try {
        xmlFile.inputStream().use { inputStream ->
          processXmlStream(
            inputStream = inputStream,
            streamName = xmlFile.name,
            xmlFile = xmlFile,
          )
        }
      } catch (e: IOException) {
        logger.error(e, "Error reading file ${xmlFile.name}")
      } catch (e: Exception) {
        logger.error(e, "Error processing file ${xmlFile.name}")
      }
    }
  }

  private data class ParsedXmlTestCase(
    val classname: String,
    val testcaseName: String,
    val durationMs: Long?,
    val status: String,
    val stackTrace: String?,
    val systemOut: String?,
    val systemOutTruncated: Boolean,
    val systemErr: String?,
    val systemErrTruncated: Boolean,
    val properties: Map<String, String>,
    val attachments: List<String>,
  )

  private class SuiteFrame(
    val suiteName: String? = null,
    var modulePath: String? = null,
    var testSuiteName: String? = null,
    var testTarget: String? = null,
    var variantName: String? = null,
    val suiteProperties: LinkedHashMap<String, String> = LinkedHashMap(),
  ) {
    var suiteSystemOutCollector: SystemOutStreamCollector? = null
    val pendingTestCases = ArrayList<ParsedXmlTestCase>()

    fun createChild(childSuiteName: String?): SuiteFrame =
      SuiteFrame(
        suiteName = childSuiteName,
        modulePath = modulePath,
        testSuiteName = testSuiteName,
        testTarget = testTarget,
        variantName = variantName,
        suiteProperties = LinkedHashMap(suiteProperties),
      )
  }

  /**
   * Parses an XML stream and adds JUnit `<testsuite>` test cases to the report builder.
   *
   * Non-JUnit XML files (whose root element is neither `<testsuite>` nor `<testsuites>`) are skipped cleanly.
   */
  private fun processXmlStream(
    inputStream: InputStream,
    streamName: String? = null,
    xmlFile: File? = null,
  ) {
    var latestVariantName: String? = null
    val factory = XmlUtils.createXmlInputFactory()
    factory.setProperty(XMLInputFactory.IS_COALESCING, true)

    try {
      inputStream.use { stream ->
        val reader = factory.createXMLStreamReader(stream)
        var seenRootElement = false

        val suiteStack = ArrayDeque<SuiteFrame>().apply { addLast(SuiteFrame()) }

        var inTestCase = false
        var inFailureOrError = false
        var inTestCaseSystemOut = false
        var inSuiteSystemOut = false
        var inSystemErr = false
        var inProperty = false
        var pendingPropertyName: String? = null
        var pendingPropertyText: StringBuilder? = null

        var currentClassname: String? = null
        var currentTestcaseName: String? = null
        var currentDurationMs: Long? = null
        var currentStatus = STATUS_PASS
        var failureBuffer: StringBuilder? = null
        var testcaseSystemOutCollector: SystemOutStreamCollector? = null
        var systemErrBuffer: StringBuilder? = null
        var systemErrCapped = false
        val testcaseProperties = LinkedHashMap<String, String>()

        fun recordProperty(name: String, value: String) {
          if (inTestCase) {
            testcaseProperties[name] = value
          } else {
            val activeFrame = suiteStack.last()
            when (name) {
              KEY_MODULE_PATH -> activeFrame.modulePath = value
              KEY_TEST_SUITE_NAME -> activeFrame.testSuiteName = value
              KEY_TEST_TARGET -> activeFrame.testTarget = value
              KEY_TESTED_VARIANT_NAME -> {
                rootReportBuilder.addVariant(value)
                activeFrame.variantName = value
                latestVariantName = value
              }
              else -> activeFrame.suiteProperties[name] = value
            }
          }
        }

        fun flushSuiteFrame(frame: SuiteFrame) {
          frame.suiteSystemOutCollector?.finish()
          val suiteAttachments = frame.suiteSystemOutCollector?.attachments?.toList().orEmpty()
          val suiteEnvRecord =
            TestDetailRecordReader.parseSuiteEnvironmentFromProperties(
              suiteProperties = frame.suiteProperties,
              modulePath = frame.modulePath,
              variantName = frame.variantName,
              suiteName = frame.testSuiteName,
              targetName = frame.testTarget,
              extraEnvironmentKeys = adapterRegistry.allSuiteEnvironmentPropertyKeys,
            )
          val suiteEnvPair = suiteEnvRecord?.let { rec ->
            val key =
              SuiteEnvironmentKey(
                modulePath = rec.modulePath!!,
                variantName = rec.variantName!!,
                suiteName = rec.suiteName!!,
                targetName = rec.targetName?.takeIf { it.isNotBlank() } ?: UNKNOWN_TARGET,
              )
            rootReportBuilder.addEnvironment(key, rec)
            key to rec
          }

          val mod = frame.modulePath
          val suite = frame.testSuiteName
          val variant = frame.variantName
          if (mod != null && suite != null && variant != null) {
            val target = frame.testTarget ?: UNKNOWN_TARGET
            val suiteOwnedPrefixes = adapterRegistry.allSuiteOwnedPropertyPrefixes
            val suiteHasReportOrAdapterProps =
              frame.suiteProperties.keys.any { key ->
                key.startsWith(INTERNAL_REPORT_KEY_PREFIX) || suiteOwnedPrefixes.any { key.startsWith(it) }
              }
            for (tc in frame.pendingTestCases) {
              val isSyntheticClassFailure =
                tc.testcaseName in SYNTHETIC_TEST_NAMES || tc.testcaseName == tc.classname || tc.testcaseName == frame.suiteName
              val hasOwnReportEntries = tc.properties.keys.any { it.startsWith(INTERNAL_REPORT_KEY_PREFIX) }
              val effectiveProperties =
                if (tc.status == STATUS_FAIL && isSyntheticClassFailure && !hasOwnReportEntries && suiteHasReportOrAdapterProps) {
                  LinkedHashMap<String, String>().apply {
                    for ((k, v) in frame.suiteProperties) {
                      if (k.startsWith(INTERNAL_REPORT_KEY_PREFIX) || suiteOwnedPrefixes.any { k.startsWith(it) }) {
                        put(k, v)
                      }
                    }
                    putAll(tc.properties)
                  }
                } else {
                  LinkedHashMap(tc.properties)
                }
              val effectiveAttachments =
                if (tc.status == STATUS_FAIL && isSyntheticClassFailure && tc.attachments.isEmpty()) {
                  suiteAttachments
                } else {
                  tc.attachments
                }

              rootReportBuilder.addTarget(target)
              addTestResult(
                modulePath = mod,
                testSuiteName = suite,
                classname = tc.classname,
                testcaseName = tc.testcaseName,
                variantName = variant,
                targetName = target,
                status = tc.status,
                durationMs = tc.durationMs,
                stackTrace = tc.stackTrace,
                rawSystemOut = tc.systemOut,
                rawSystemOutTruncated = tc.systemOutTruncated,
                rawSystemErr = tc.systemErr,
                rawSystemErrTruncated = tc.systemErrTruncated,
                rawProperties = effectiveProperties,
                attachments = effectiveAttachments,
                xmlFile = xmlFile,
                suiteEnvironment = suiteEnvPair,
              )
            }
          }
          frame.pendingTestCases.clear()
          frame.suiteSystemOutCollector = null
        }

        try {
          while (reader.hasNext()) {
            when (reader.next()) {
              XMLStreamConstants.START_ELEMENT -> {
                val tagName = reader.localName
                if (!seenRootElement) {
                  seenRootElement = true
                  if (tagName != TAG_TESTSUITE && tagName != TAG_TESTSUITES) {
                    val location = streamName ?: "stream"
                    logger.verbose("Ignoring non-JUnit XML file: $location")
                    break
                  }
                }

                when (tagName) {
                  TAG_TESTSUITE -> {
                    suiteStack.addLast(suiteStack.last().createChild(reader.getAttributeValue(null, ATTR_NAME)))
                    inSuiteSystemOut = false
                  }
                  TAG_PROPERTY -> {
                    val name = reader.getAttributeValue(null, ATTR_NAME)
                    val value = reader.getAttributeValue(null, ATTR_VALUE)
                    if (name != null) {
                      if (value != null) {
                        recordProperty(name, value)
                        inProperty = false
                      } else {
                        inProperty = true
                        pendingPropertyName = name
                        pendingPropertyText = StringBuilder()
                      }
                    }
                  }
                  TAG_TESTCASE -> {
                    inTestCase = true
                    inFailureOrError = false
                    inTestCaseSystemOut = false
                    inSystemErr = false
                    inProperty = false
                    currentClassname = reader.getAttributeValue(null, ATTR_CLASSNAME)
                    currentTestcaseName = reader.getAttributeValue(null, ATTR_NAME)
                    currentDurationMs = parseDurationMs(reader.getAttributeValue(null, ATTR_TIME))
                    currentStatus = STATUS_PASS
                    failureBuffer = null
                    testcaseSystemOutCollector = null
                    systemErrBuffer = null
                    systemErrCapped = false
                    testcaseProperties.clear()
                  }
                  TAG_SKIPPED -> {
                    if (inTestCase) {
                      currentStatus = STATUS_SKIPPED
                    }
                  }
                  TAG_FAILURE,
                  TAG_ERROR -> {
                    if (inTestCase) {
                      currentStatus = STATUS_FAIL
                      inFailureOrError = true
                      failureBuffer = StringBuilder()
                    }
                  }
                  TAG_SYSTEM_OUT -> {
                    if (inTestCase) {
                      inTestCaseSystemOut = true
                      if (testcaseSystemOutCollector == null) {
                        testcaseSystemOutCollector = SystemOutStreamCollector(MAX_STREAM_CHARS)
                      }
                    } else {
                      inSuiteSystemOut = true
                      val activeFrame = suiteStack.last()
                      if (activeFrame.suiteSystemOutCollector == null) {
                        activeFrame.suiteSystemOutCollector = SystemOutStreamCollector(maxChars = 0)
                      }
                    }
                  }
                  TAG_SYSTEM_ERR -> {
                    if (inTestCase) {
                      inSystemErr = true
                      if (systemErrBuffer == null) {
                        systemErrBuffer = StringBuilder()
                      }
                    }
                  }
                }
              }
              XMLStreamConstants.CHARACTERS,
              XMLStreamConstants.CDATA -> {
                when {
                  inProperty && pendingPropertyText != null -> pendingPropertyText.append(reader.text)
                  inFailureOrError && failureBuffer != null -> failureBuffer.append(reader.text)
                  inTestCaseSystemOut && testcaseSystemOutCollector != null -> {
                    testcaseSystemOutCollector.append(reader.text)
                  }
                  inSuiteSystemOut -> {
                    suiteStack.last().suiteSystemOutCollector?.append(reader.text)
                  }
                  inSystemErr && !systemErrCapped && systemErrBuffer != null -> {
                    systemErrCapped = appendCapped(systemErrBuffer, reader.text, MAX_STREAM_CHARS)
                  }
                }
              }
              XMLStreamConstants.END_ELEMENT -> {
                when (reader.localName) {
                  TAG_PROPERTY -> {
                    val propName = pendingPropertyName
                    if (inProperty && propName != null) {
                      recordProperty(propName, pendingPropertyText?.toString() ?: "")
                    }
                    inProperty = false
                    pendingPropertyName = null
                    pendingPropertyText = null
                  }
                  TAG_FAILURE,
                  TAG_ERROR -> {
                    inFailureOrError = false
                  }
                  TAG_SYSTEM_OUT -> {
                    if (inTestCase) {
                      inTestCaseSystemOut = false
                    } else {
                      inSuiteSystemOut = false
                    }
                  }
                  TAG_SYSTEM_ERR -> {
                    inSystemErr = false
                  }
                  TAG_TESTCASE -> {
                    val cls = currentClassname
                    val tcName = currentTestcaseName
                    if (cls != null && tcName != null) {
                      val parsedSystemOut = testcaseSystemOutCollector?.finish()
                      val parsedSystemOutTruncated = parsedSystemOut != null && testcaseSystemOutCollector?.truncated == true
                      val parsedSystemErr = finalizeStreamBuffer(systemErrBuffer)
                      val parsedSystemErrTruncated = parsedSystemErr != null && systemErrCapped
                      // The output of a passing test without report entries is never shown, so it isn't held until the suite ends.
                      val keepOutput =
                        currentStatus != STATUS_PASS || testcaseProperties.keys.any { it.startsWith(INTERNAL_REPORT_KEY_PREFIX) }
                      suiteStack
                        .last()
                        .pendingTestCases
                        .add(
                          ParsedXmlTestCase(
                            classname = cls,
                            testcaseName = tcName,
                            durationMs = currentDurationMs,
                            status = currentStatus,
                            stackTrace = failureBuffer?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                            systemOut = parsedSystemOut.takeIf { keepOutput },
                            systemOutTruncated = keepOutput && parsedSystemOutTruncated,
                            systemErr = parsedSystemErr.takeIf { keepOutput },
                            systemErrTruncated = keepOutput && parsedSystemErrTruncated,
                            properties = LinkedHashMap(testcaseProperties),
                            attachments = testcaseSystemOutCollector?.attachments?.toList().orEmpty(),
                          )
                        )
                    }
                    inTestCase = false
                    inFailureOrError = false
                    inTestCaseSystemOut = false
                    inSystemErr = false
                    currentClassname = null
                    currentTestcaseName = null
                    currentDurationMs = null
                    failureBuffer = null
                    testcaseSystemOutCollector = null
                    systemErrBuffer = null
                    systemErrCapped = false
                    testcaseProperties.clear()
                  }
                  TAG_TESTSUITE -> {
                    val completedFrame = if (suiteStack.size > 1) suiteStack.removeLast() else suiteStack.last()
                    flushSuiteFrame(completedFrame)
                  }
                }
              }
            }
          }
        } finally {
          // Test cases parsed before a malformed or truncated element are still reported.
          suiteStack.asReversed().forEach { frame -> if (frame.pendingTestCases.isNotEmpty()) flushSuiteFrame(frame) }
        }
        reader.close()
      }
    } catch (e: XMLStreamException) {
      val location = streamName ?: "stream"
      logger.error(e, "Error parsing XML for variant '$latestVariantName' in $location")
    } catch (e: Exception) {
      val location = streamName ?: "stream"
      logger.error(e, "An unexpected error occurred for variant '$latestVariantName' in $location")
    }
  }

  /** Safely adapts and adds a test result to the nested builder hierarchy. */
  private fun addTestResult(
    modulePath: String,
    testSuiteName: String,
    classname: String,
    testcaseName: String,
    variantName: String,
    targetName: String,
    status: String,
    durationMs: Long?,
    stackTrace: String?,
    rawSystemOut: String?,
    rawSystemOutTruncated: Boolean,
    rawSystemErr: String?,
    rawSystemErrTruncated: Boolean,
    rawProperties: Map<String, String>,
    attachments: List<String>,
    xmlFile: File?,
    suiteEnvironment: Pair<SuiteEnvironmentKey, SuiteEnvironmentRecord>?,
  ) {
    try {
      val xmlDir = xmlFile?.parentFile
      val resolvedEnv = suiteEnvironment ?: rootReportBuilder.findEnvironment(modulePath, variantName, testSuiteName, targetName)

      val detailRecord =
        TestDetailRecordReader.parseDetailRecordFromEntries(
          rawProperties = rawProperties,
          attachments = attachments,
          classname = classname,
          testcaseName = testcaseName,
          logger = logger,
          warnedSchemaVersions = warnedDetailSchemaVersions,
        )

      val adapterContext =
        SuiteAdapterContext(
          rawProperties = rawProperties,
          detailRecord = detailRecord,
          environment = resolvedEnv?.second,
        )
      val adapter = adapterRegistry.resolve(adapterContext)
      val adapted = adapter.adapt(adapterContext)
      rootReportBuilder.mergeSuiteDescriptor(testSuiteName, adapted.suiteDescriptor)
      val enrichedLogs = detailRecord?.logs?.let { adapter.enrichLogs(it, adapted.result.actors) }

      // Test output is only shown in the rich detail view, so classic results drop it.
      val keepStreams = detailRecord != null || (status != STATUS_PASS && adapter.keepsFailureStreams)
      val systemOut = if (keepStreams) rawSystemOut else null
      val systemErr = if (keepStreams) rawSystemErr else null

      val packageName = classname.substringBeforeLast('.', "")
      val simpleClassName = classname.substringAfterLast('.')

      rootReportBuilder
        .getOrAddModule(modulePath)
        .getOrAddPackage(packageName)
        .getOrAddClass(simpleClassName)
        .getOrAddTestCase(testcaseName)
        .addResult(
          TestCaseExecution(
            testSuiteName = testSuiteName,
            variantName = variantName,
            targetName = targetName,
            status = status,
            durationMs = durationMs,
            stackTrace = stackTrace,
            systemOut = systemOut,
            systemOutTruncated = systemOut != null && rawSystemOutTruncated,
            systemErr = systemErr,
            systemErrTruncated = systemErr != null && rawSystemErrTruncated,
            xmlDir = xmlDir,
            environmentKey = resolvedEnv?.first,
            adaptedResult = adapted.result,
            rawLogs = enrichedLogs,
            rawArtifacts = detailRecord?.artifacts,
          )
        )
    } catch (e: Exception) {
      logger.error(e, "Error processing test case: $classname.$testcaseName")
    }
  }

  private data class FileCopySpec(val sourceFile: File, val relativeDestPath: String)

  private data class PendingLazyLogSpec(
    val title: String,
    val sourceFile: File,
    val lineCount: Int,
    val capturedTruncated: Boolean,
    val artifactPath: String,
  )

  private data class PendingLazyTestDetail(
    val logs: List<PendingLazyLogSpec>,
    val systemOut: String?,
    val systemOutTruncated: Boolean,
    val systemErr: String?,
    val systemErrTruncated: Boolean,
  )

  private data class AggregatedReportBundle(
    val report: RootReport,
    val lazyEnvDetails: Map<String, LazyEnvironmentDetail>,
    val lazyTestDetails: Map<String, PendingLazyTestDetail>,
    val generatedFiles: List<GeneratedTextFileSpec>,
    val copiedFiles: List<FileCopySpec>,
  )

  private class ArtifactCollector(
    private val environments: Map<SuiteEnvironmentKey, SuiteEnvironmentInfo>,
    private val logger: ILogger,
  ) {
    private var nextDetailSequence = 1
    val lazyTestDetails = LinkedHashMap<String, PendingLazyTestDetail>()
    val copiedFiles = mutableListOf<FileCopySpec>()

    fun buildVariantTestResult(exec: TestCaseExecution, stackTraceId: String?): VariantTestResult {
      val envId = exec.environmentKey?.let { environments[it]?.envId }

      val resolvedLogs = mutableListOf<Pair<LogRecord, File>>()
      if (exec.xmlDir != null && !exec.rawLogs.isNullOrEmpty()) {
        for (logRecord in exec.rawLogs) {
          val logPath = logRecord.path?.takeIf { it.isNotBlank() } ?: continue
          val safeFile = PathSafetyUtils.resolveAttachmentFile(exec.xmlDir, logPath, logger) ?: continue
          if (!safeFile.isFile) {
            logger.warning("Referenced test log file '$logPath' does not exist in '${exec.xmlDir.path}'.")
            continue
          }
          resolvedLogs.add(logRecord to safeFile)
        }
      }

      val resolvedArtifactFiles = IdentityHashMap<ArtifactRecord, File>()
      if (exec.xmlDir != null && !exec.rawArtifacts.isNullOrEmpty()) {
        for (artifactRecord in exec.rawArtifacts) {
          val rawPath = artifactRecord.path?.takeIf { it.isNotBlank() } ?: continue
          val safeFile = PathSafetyUtils.resolveAttachmentFile(exec.xmlDir, rawPath, logger) ?: continue
          if (!safeFile.isFile) {
            logger.warning("Referenced test artifact '$rawPath' does not exist in '${exec.xmlDir.path}'.")
            continue
          }
          resolvedArtifactFiles[artifactRecord] = safeFile
        }
      }

      val needsLazyDetail = resolvedLogs.isNotEmpty() || exec.systemOut != null || exec.systemErr != null
      val needsArtifactSlot = needsLazyDetail || resolvedArtifactFiles.isNotEmpty()

      var detailId: String? = null
      val logMetaList = mutableListOf<LogMetaInfo>()
      val artifactInfoList = mutableListOf<ArtifactInfo>()

      if (needsArtifactSlot) {
        val allocatedId = String.format(Locale.US, "t_%04d", nextDetailSequence++)
        val usedFileNames = HashSet<String>()
        val pendingLogs = mutableListOf<PendingLazyLogSpec>()

        for ((logRecord, sourceFile) in resolvedLogs) {
          val destFileName = PathSafetyUtils.allocateUniqueFileName(sourceFile.name, "log.txt", usedFileNames)
          val relPath = "artifacts/$allocatedId/$destFileName"
          val lineCount = countLogLinesCapped(sourceFile, MAX_INLINE_LOG_BYTES, logger)
          val title = logRecord.title?.takeIf { it.isNotBlank() } ?: sourceFile.name

          logMetaList.add(LogMetaInfo(title = title, lineCount = lineCount, artifactPath = relPath))
          pendingLogs.add(
            PendingLazyLogSpec(
              title = title,
              sourceFile = sourceFile,
              lineCount = lineCount,
              capturedTruncated = logRecord.truncated == true,
              artifactPath = relPath,
            )
          )
          copiedFiles.add(FileCopySpec(sourceFile = sourceFile, relativeDestPath = relPath))
        }

        exec.rawArtifacts?.forEach { artifactRecord ->
          val name = artifactRecord.name?.takeIf { it.isNotBlank() } ?: return@forEach
          val sourceFile = resolvedArtifactFiles[artifactRecord]
          if (artifactRecord.path.isNullOrBlank()) {
            artifactInfoList.add(ArtifactInfo(name = name, values = artifactRecord.values, path = null))
          } else if (sourceFile != null) {
            val destFileName = PathSafetyUtils.allocateUniqueFileName(sourceFile.name, "artifact.bin", usedFileNames)
            val relPath = "artifacts/$allocatedId/$destFileName"
            val effectiveValues =
              artifactRecord.values?.takeIf { it.isNotEmpty() } ?: mapOf("size" to formatArtifactByteSize(sourceFile.length()))
            artifactInfoList.add(ArtifactInfo(name = name, values = effectiveValues, path = relPath))
            copiedFiles.add(FileCopySpec(sourceFile = sourceFile, relativeDestPath = relPath))
          } else if (!artifactRecord.values.isNullOrEmpty()) {
            artifactInfoList.add(ArtifactInfo(name = name, values = artifactRecord.values, path = null))
          }
        }

        if (needsLazyDetail) {
          detailId = allocatedId
          lazyTestDetails[allocatedId] =
            PendingLazyTestDetail(
              logs = pendingLogs,
              systemOut = exec.systemOut,
              systemOutTruncated = exec.systemOutTruncated,
              systemErr = exec.systemErr,
              systemErrTruncated = exec.systemErrTruncated,
            )
        }
      } else {
        exec.rawArtifacts?.forEach { artifactRecord ->
          val name = artifactRecord.name?.takeIf { it.isNotBlank() } ?: return@forEach
          if (artifactRecord.path.isNullOrBlank() || !artifactRecord.values.isNullOrEmpty()) {
            artifactInfoList.add(ArtifactInfo(name = name, values = artifactRecord.values, path = null))
          }
        }
      }

      return exec.adaptedResult.copy(
        status = exec.status,
        stackTraceId = stackTraceId,
        durationMs = exec.durationMs,
        detailId = detailId,
        envId = envId,
        artifacts = artifactInfoList.takeIf { it.isNotEmpty() },
        logMeta = logMetaList.takeIf { it.isNotEmpty() },
      )
    }
  }

  private class RootReportBuilder(
    private val projectName: String,
    private val adapterRegistry: SuiteReportAdapterRegistry,
    private val logger: ILogger,
  ) {
    private val variants = HashSet<String>()
    private val targets = HashSet<String>()
    private val moduleBuilders = mutableMapOf<String, ModuleBuilder>()
    private val suiteDescriptors = TreeMap<String, SuiteDescriptor>()
    private val environments = TreeMap<SuiteEnvironmentKey, SuiteEnvironmentRecord>()

    fun addVariant(variant: String) {
      variants.add(variant)
    }

    fun addTarget(target: String) {
      targets.add(target)
    }

    fun addEnvironment(key: SuiteEnvironmentKey, record: SuiteEnvironmentRecord) {
      environments.putIfAbsent(key, record)
    }

    fun findEnvironment(
      modulePath: String,
      variantName: String,
      suiteName: String,
      targetName: String,
    ): Pair<SuiteEnvironmentKey, SuiteEnvironmentRecord>? {
      val exactKey = SuiteEnvironmentKey(modulePath, variantName, suiteName, targetName)
      environments[exactKey]?.let {
        return exactKey to it
      }
      if (targetName == UNKNOWN_TARGET) {
        val matches =
          environments.entries.filter { (key, _) ->
            key.modulePath == modulePath && key.variantName == variantName && key.suiteName == suiteName
          }
        if (matches.size == 1) {
          return matches.single().let { it.key to it.value }
        }
      }
      return null
    }

    fun mergeSuiteDescriptor(testSuiteName: String, incoming: SuiteDescriptor) {
      if (testSuiteName == AGGREGATED_TEST_SUITE_NAME) return
      val existing = suiteDescriptors[testSuiteName]
      if (existing == null) {
        suiteDescriptors[testSuiteName] = incoming
        return
      }
      val incomingOverridesGeneric =
        existing.adapterId == ADAPTER_ID_DEFAULT &&
          existing.suiteType.isNullOrBlank() &&
          (incoming.adapterId != ADAPTER_ID_DEFAULT || !incoming.suiteType.isNullOrBlank())
      val primary = if (incomingOverridesGeneric) incoming else existing
      val secondary = if (primary === existing) incoming else existing

      val mergedColumns = LinkedHashMap<String, SummaryColumnDef>()
      primary.summaryColumns.forEach { mergedColumns.putIfAbsent(it.key, it) }
      secondary.summaryColumns.forEach { mergedColumns.putIfAbsent(it.key, it) }
      val orderedColumns = adapterRegistry.findById(primary.adapterId).orderSummaryColumns(mergedColumns.values)

      suiteDescriptors[testSuiteName] =
        SuiteDescriptor(
          adapterId = primary.adapterId,
          suiteType = primary.suiteType ?: secondary.suiteType,
          title = primary.title ?: secondary.title,
          likelyCauseHeading = primary.likelyCauseHeading ?: secondary.likelyCauseHeading,
          summaryColumns = orderedColumns,
          docsUrl = primary.docsUrl ?: secondary.docsUrl,
        )
    }

    fun getOrAddModule(name: String) = moduleBuilders.getOrPut(name) { ModuleBuilder(name) }

    fun build(): AggregatedReportBundle {
      val generatedFiles = mutableListOf<GeneratedTextFileSpec>()
      val lazyEnvDetails = LinkedHashMap<String, LazyEnvironmentDetail>()
      val preparedEnvironments = LinkedHashMap<SuiteEnvironmentKey, SuiteEnvironmentInfo>()

      environments.entries.forEachIndexed { index, (key, rawEnv) ->
        val envId = String.format(Locale.US, "env_%02d", index + 1)
        val resolvedSuiteDescriptor = suiteDescriptors[key.suiteName]
        val envAdapter =
          if (resolvedSuiteDescriptor != null) {
            adapterRegistry.findById(resolvedSuiteDescriptor.adapterId)
          } else {
            adapterRegistry.resolve(
              SuiteAdapterContext(
                rawProperties = rawEnv.extraProperties,
                environment = rawEnv,
              )
            )
          }
        val adaptedEnv = envAdapter.adaptEnvironment(envId, rawEnv)
        generatedFiles.addAll(adaptedEnv.generatedFiles)
        if (adaptedEnv.lazyFiles.isNotEmpty()) {
          lazyEnvDetails[envId] = LazyEnvironmentDetail(files = adaptedEnv.lazyFiles)
        }

        preparedEnvironments[key] =
          SuiteEnvironmentInfo(
            envId = envId,
            modulePath = key.modulePath,
            variantName = key.variantName,
            suiteName = key.suiteName,
            targetName = rawEnv.targetName?.takeIf { it.isNotBlank() } ?: key.targetName,
            suiteType = rawEnv.suiteType,
            applicationId = rawEnv.applicationId,
            applicationName = rawEnv.applicationName,
            versionName = rawEnv.versionName,
            versionCode = rawEnv.versionCode,
            targetSdkVersion = rawEnv.targetSdkVersion,
            agpVersion = rawEnv.agpVersion,
            gradleVersion = rawEnv.gradleVersion,
            testLibraryVersion = rawEnv.testLibraryVersion,
            runner = rawEnv.runner,
            attributes = adaptedEnv.attributes,
            configFiles = adaptedEnv.configFiles,
          )
      }

      val collector = ArtifactCollector(preparedEnvironments, logger)

      val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
      val zonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
      val formattedTimestamp = zonedDateTime.format(formatter)
      val modules = moduleBuilders.values.sortedBy { it.name }.map { it.build(collector) }

      val numberOfModules = modules.size
      val numberOfPackages = modules.sumOf { it.packages.size }
      val numberOfClasses = modules.sumOf { module -> module.packages.sumOf { pkg -> pkg.classes.size } }

      val testSuites = modules.flatMap { it.testSuiteSummaries.map { ts -> ts.name } }.distinct().sorted()
      val targetsSorted = if (targets.isEmpty()) listOf(UNKNOWN_TARGET) else targets.sorted()

      val report =
        RootReport(
          projectName = projectName,
          timestamp = formattedTimestamp,
          numberOfModules = numberOfModules,
          numberOfPackages = numberOfPackages,
          numberOfClasses = numberOfClasses,
          variants = variants.sorted(),
          testSuites = testSuites,
          targets = targetsSorted,
          modules = modules,
          relativeRootDir = "",
          suiteDescriptors = LinkedHashMap(suiteDescriptors),
          suiteEnvironments = preparedEnvironments.values.toList(),
        )

      return AggregatedReportBundle(
        report = report,
        lazyEnvDetails = lazyEnvDetails,
        lazyTestDetails = collector.lazyTestDetails,
        generatedFiles = generatedFiles,
        copiedFiles = collector.copiedFiles,
      )
    }
  }

  private class ModuleBuilder(val name: String) {
    val packages = mutableMapOf<String, PackageBuilder>()

    fun getOrAddPackage(name: String) = packages.getOrPut(name) { PackageBuilder(name) }

    fun build(collector: ArtifactCollector): Module {
      val pkgs = packages.values.sortedBy { it.name }.map { it.build(collector) }
      return Module(name = name, testSuiteSummaries = rollupChildSuiteSummaries(pkgs.map { it.testSuiteSummaries }), packages = pkgs)
    }
  }

  private class PackageBuilder(val name: String) {
    val classes = mutableMapOf<String, ClassBuilder>()

    fun getOrAddClass(name: String) = classes.getOrPut(name) { ClassBuilder(name) }

    fun build(collector: ArtifactCollector): Package {
      val clzs = classes.values.sortedBy { it.name }.map { it.build(collector) }
      return Package(name = name, testSuiteSummaries = rollupChildSuiteSummaries(clzs.map { it.testSuiteSummaries }), classes = clzs)
    }
  }

  private class ClassBuilder(val name: String) {
    val testCases = mutableMapOf<String, TestCaseBuilder>()

    fun getOrAddTestCase(name: String) = testCases.getOrPut(name) { TestCaseBuilder(name) }

    fun build(collector: ArtifactCollector): ClassType {
      val testCasesList = testCases.values.sortedBy { it.name }.map { it.build(collector) }
      val testSuiteSummaries = summarizeClassExecutions(testCases.values.map { it.executions }).toMutableList()
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
      val aggregatedVariantSummaries = flattenVariantSummaries(testSuiteSummaries.flatMap { it.variantSummaries }, allVariants)
      testSuiteSummaries.add(TestSuiteSummary(AGGREGATED_TEST_SUITE_NAME, aggregatedVariantSummaries))
      return ClassType(name = name, testSuiteSummaries = testSuiteSummaries, testCases = testCasesList)
    }
  }

  private class TestCaseBuilder(val name: String) {
    val executions = mutableListOf<TestCaseExecution>()

    fun addResult(execution: TestCaseExecution) {
      executions.add(execution)
    }

    fun build(collector: ArtifactCollector): TestCase {
      val targetsList =
        executions
          .groupBy { it.targetName }
          .entries
          .sortedBy { it.key }
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
                  val winningExecByVariant = LinkedHashMap<String, TestCaseExecution>()
                  suiteExecs.forEach { exec ->
                    val existing = winningExecByVariant[exec.variantName]
                    if (shouldReplaceExecution(existing, exec)) {
                      winningExecByVariant[exec.variantName] = exec
                    }
                  }

                  val variantResults = LinkedHashMap<String, VariantTestResult>()
                  winningExecByVariant.forEach { (variantName, exec) ->
                    variantResults[variantName] =
                      collector.buildVariantTestResult(
                        exec = exec,
                        stackTraceId = exec.stackTrace?.let { stackTraceToId[it] },
                      )
                  }
                  TestSuiteTestResult(suiteName, variantResults)
                }

            Target(
              name = targetName,
              testSuiteSummaries = summarizeTargetExecutions(targetExecutions),
              testSuiteResults = testSuiteResults,
              commonStackTraces = stackTraceGroups,
            )
          }

      return TestCase(name = name, targets = targetsList)
    }
  }

  companion object {
    private val RENDERER_RESOURCES = listOf("index.html", "styles.css", "script.js")

    /** Parses a JUnit `time` attribute, in seconds written as a plain decimal number, into milliseconds. */
    internal fun parseDurationMs(timeAttribute: String?): Long? {
      if (timeAttribute.isNullOrBlank()) return null
      val seconds = timeAttribute.trim().toDoubleOrNull() ?: return null
      if (!seconds.isFinite() || seconds < 0.0) return null
      return (seconds * 1000.0).roundToLong()
    }

    private class CappedLogBytes(
      val bytes: ByteArray,
      val validLen: Int,
      val truncated: Boolean,
    )

    private fun utf8LeadSequenceLength(byte: Byte): Int =
      when (byte.toInt() and 0xFF) {
        in 0xC2..0xDF -> 2
        in 0xE0..0xEF -> 3
        in 0xF0..0xF4 -> 4
        else -> 1
      }

    private fun readLogBytesCapped(file: File, maxBytes: Int, logger: ILogger): CappedLogBytes? {
      if (maxBytes <= 0) {
        return try {
          val truncated = file.inputStream().use { it.read() >= 0 }
          CappedLogBytes(ByteArray(0), 0, truncated)
        } catch (e: IOException) {
          logger.warning("Failed to read log file '${file.name}': ${e.message}")
          null
        }
      }
      return try {
        file.inputStream().use { input ->
          val bufferSize = minOf(file.length(), maxBytes + 1L).coerceAtLeast(0L).toInt()
          val buffer = ByteArray(bufferSize)
          var totalRead = 0
          while (totalRead < buffer.size) {
            val read = input.read(buffer, totalRead, buffer.size - totalRead)
            if (read <= 0) break
            totalRead += read
          }
          val truncated = totalRead > maxBytes
          var validLen = minOf(totalRead, maxBytes)
          if (truncated && (buffer[maxBytes].toInt() and 0xC0) == 0x80) {
            var leadIdx = validLen - 1
            while (leadIdx >= 0 && (buffer[leadIdx].toInt() and 0xC0) == 0x80) {
              leadIdx--
            }
            if (leadIdx >= 0 && validLen - leadIdx < utf8LeadSequenceLength(buffer[leadIdx])) {
              validLen = leadIdx
            }
          }
          CappedLogBytes(buffer, validLen, truncated)
        }
      } catch (e: IOException) {
        logger.warning("Failed to read log file '${file.name}': ${e.message}")
        null
      }
    }

    internal fun readLogFileCapped(file: File, maxBytes: Int, logger: ILogger): Pair<String, Boolean> {
      val capped = readLogBytesCapped(file, maxBytes, logger) ?: return "" to false
      return String(capped.bytes, 0, capped.validLen, Charsets.UTF_8) to capped.truncated
    }

    /** Counts the lines of the preview that [readLogFileCapped] returns for the same [file] and [maxBytes]. */
    internal fun countLogLinesCapped(file: File, maxBytes: Int, logger: ILogger): Int {
      val capped = readLogBytesCapped(file, maxBytes, logger) ?: return 0
      if (capped.validLen == 0) return 0
      val newline = '\n'.code.toByte()
      val newlines = (0 until capped.validLen).count { capped.bytes[it] == newline }
      return if (capped.bytes[capped.validLen - 1] == newline) newlines else newlines + 1
    }

    private fun formatRelativePath(path: Path): String {
      val normalized = path.toString().replace(File.separatorChar, '/')
      return if (normalized.isEmpty()) {
        ""
      } else if (normalized.endsWith('/')) {
        normalized
      } else {
        "$normalized/"
      }
    }

    @JvmStatic
    fun calculateRelativeRootDir(outputDir: File, rootDir: File): String {
      return try {
        val outputCanonical = outputDir.canonicalFile.toPath()
        val rootCanonical = rootDir.canonicalFile.toPath()
        formatRelativePath(outputCanonical.relativize(rootCanonical))
      } catch (_: Exception) {
        try {
          val outputNormalized = outputDir.toPath().toAbsolutePath().normalize()
          val rootNormalized = rootDir.toPath().toAbsolutePath().normalize()
          formatRelativePath(outputNormalized.relativize(rootNormalized))
        } catch (_: Exception) {
          ""
        }
      }
    }

    internal fun formatArtifactByteSize(bytes: Long): String {
      val nonNegative = bytes.coerceAtLeast(0L)
      return when {
        nonNegative < 1024L -> "$nonNegative B"
        nonNegative < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", nonNegative / 1024.0)
        nonNegative < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", nonNegative / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.1f GB", nonNegative / (1024.0 * 1024.0 * 1024.0))
      }
    }
  }
}
