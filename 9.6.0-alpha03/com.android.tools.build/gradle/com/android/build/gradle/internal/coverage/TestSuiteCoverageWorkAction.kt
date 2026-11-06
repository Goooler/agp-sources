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

package com.android.build.gradle.internal.coverage

import com.android.build.gradle.internal.coverage.renderer.CodeCoverageReportOrchestrator
import com.android.build.gradle.internal.coverage.report.ReportType
import com.android.tools.coverage.reporter.CoverageXmlMerger
import java.io.File
import java.nio.file.Files
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

interface TestSuiteCoverageWorkParameters : WorkParameters {
  val coverageFiles: ConfigurableFileCollection
  val reportDir: DirectoryProperty
  val classFolders: ConfigurableFileCollection
  val sourceFolders: ConfigurableFileCollection
  val reportName: Property<String>

  val projectName: Property<String>
  val variantName: Property<String>
  val testSuiteName: Property<String>
  val rootProjectName: Property<String>
  val rootProjectDir: DirectoryProperty
}

abstract class TestSuiteCoverageWorkAction : WorkAction<TestSuiteCoverageWorkParameters> {
  override fun execute() {
    val logger = Logging.getLogger(TestSuiteCoverageWorkAction::class.java)
    try {
      val coverageFiles = parameters.coverageFiles.files
      // With android.experimental.androidTest.coverage.useOnDeviceAgent, device tests write the agent's .pb files instead of JaCoCo's
      // execution data.
      val jacocoFiles = coverageFiles.filter { it.extension != "pb" }
      val onDeviceRuns = findOnDeviceCoverageFiles(coverageFiles.filter { it.extension == "pb" }.map { it.parentFile }.distinct())
      if (jacocoFiles.isNotEmpty() || onDeviceRuns.isNotEmpty()) {
        val xmlReportFileName = "report"
        if (onDeviceRuns.isEmpty()) {
          generateReport(
            coverageFiles = jacocoFiles,
            reportDir = parameters.reportDir.asFile.get(),
            classFolders = parameters.classFolders.files,
            sourceFolders = parameters.sourceFolders.files,
            tabWidth = 4,
            reportName = parameters.reportName.get(),
            logger = logger,
            reportTypes = listOf(ReportType.XML),
            xmlReportName = xmlReportFileName,
          )
        } else {
          writeOnDeviceXmlReport(jacocoFiles, onDeviceRuns, File(parameters.reportDir.asFile.get(), "${xmlReportFileName}.xml"), logger)
        }

        val xmlFile = File(parameters.reportDir.asFile.get(), "${xmlReportFileName}.xml")
        val rootDir = parameters.rootProjectDir.get().asFile

        val successfulReportGeneration =
          CodeCoverageReportOrchestrator.orchestrate(
            inputDirectories = listOf(parameters.reportDir.asFile.get()),
            htmlReportDir = parameters.reportDir,
            rootProjectName = parameters.rootProjectName.get(),
            rootProjectDir = rootDir,
            modulePathOverride = parameters.projectName.get(),
            variantNameOverride = parameters.variantName.get(),
            testSuiteNameOverride = parameters.testSuiteName.get(),
            sourcePaths = parameters.sourceFolders.files.map { it.relativeToOrNull(rootDir)?.path ?: it.path },
          )

        if (successfulReportGeneration) {
          val reportLocation = parameters.reportDir.locationOnly.get().file("index.html").asFile.toURI()
          logger.lifecycle("View coverage report at $reportLocation")
        } else {
          logger.warn("No code coverage data was found for test suite '${parameters.testSuiteName.get()}', skipping report generation.")
        }
      }
    } catch (e: Exception) {
      logger.warn("Unable to generate coverage report", e)
    }
  }

  /**
   * Writes the XML report of the on-device coverage runs into [xmlFile]. The reports of several runs, or of on-device and JaCoCo data (a
   * suite with host and device tests), can only be merged as XML reports.
   */
  private fun writeOnDeviceXmlReport(jacocoFiles: List<File>, onDeviceRuns: List<OnDeviceCoverageFiles>, xmlFile: File, logger: Logger) {
    val reportName = parameters.reportName.get()
    val sourceFolders = parameters.sourceFolders.files
    if (jacocoFiles.isEmpty() && onDeviceRuns.size == 1) {
      val run = onDeviceRuns.single()
      generateOnTheFlyXml(run.metadata, run.hits, xmlFile, reportName, testPackageId = null, exclusions = emptySet(), sourceFolders)
      return
    }
    val reportsDir = Files.createTempDirectory("coverage").toFile()
    try {
      val reports = mutableListOf<File>()
      if (jacocoFiles.isNotEmpty()) {
        generateReport(
          coverageFiles = jacocoFiles,
          reportDir = reportsDir,
          classFolders = parameters.classFolders.files,
          sourceFolders = sourceFolders,
          tabWidth = 4,
          reportName = reportName,
          logger = logger,
          reportTypes = listOf(ReportType.XML),
          xmlReportName = "jacoco",
        )
        reports += File(reportsDir, "jacoco.xml")
      }
      onDeviceRuns.forEachIndexed { index, run ->
        val report = File(reportsDir, "on-device-$index.xml")
        generateOnTheFlyXml(run.metadata, run.hits, report, reportName, testPackageId = null, exclusions = emptySet(), sourceFolders)
        reports += report
      }
      CoverageXmlMerger().merge(reports, reportName, xmlFile)
    } finally {
      reportsDir.deleteRecursively()
    }
  }
}
