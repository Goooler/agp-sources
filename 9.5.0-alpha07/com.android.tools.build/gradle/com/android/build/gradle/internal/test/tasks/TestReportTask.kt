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

package com.android.build.gradle.internal.test.tasks

import com.android.build.gradle.internal.coverage.renderer.CodeCoverageReportOrchestrator
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalGlobalTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.test.report.XMLReportAggregator
import com.android.buildanalyzer.common.TaskCategory
import java.io.File
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.logging.ConsoleRenderer

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class TestReportTask : NonIncrementalGlobalTask() {

  @get:Input abstract val rootProjectName: Property<String>

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val testResults: ListProperty<Directory>

  @get:OutputDirectory abstract val testReport: DirectoryProperty

  @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE) abstract val coverageXmlReports: ListProperty<Directory>

  @get:OutputDirectory @get:Optional abstract val coverageHtmlReportDir: DirectoryProperty

  @get:Internal abstract val rootProjectDir: RegularFileProperty

  override fun doTaskAction() {
    val inputDirectories: List<File> = testResults.get().map { it.asFile }
    val testReport = testReport.get().asFile

    XMLReportAggregator(inputDirectories, rootProjectName.get()).writeReport(testReport)

    // --- CODE COVERAGE REPORT GENERATION ---
    if (coverageXmlReports.isPresent && coverageXmlReports.get().isNotEmpty()) {
      if (coverageHtmlReportDir.isPresent) {
        val xmlReportDirs = coverageXmlReports.get().map { it.asFile }
        try {
          val successfulReportGeneration =
            CodeCoverageReportOrchestrator.orchestrate(
              xmlReportDirs,
              coverageHtmlReportDir,
              rootProjectName.get(),
              rootProjectDir.get().asFile,
            )

          if (successfulReportGeneration) {
            val reportLocation = ConsoleRenderer().asClickableFileUrl(File(coverageHtmlReportDir.get().asFile, "index.html"))
            logger.lifecycle("View coverage report at $reportLocation")
          }
        } catch (e: Exception) {
          logger.warn("Unable to generate code coverage report: ${e.message}", e)
        }
      }
    } else {
      logger.info(
        """
        Code coverage report was not generated.
        To enable code coverage, please set `enableUnitTestCoverage = true` and/or `enableAndroidTestCoverage = true` in the relevant build types inside your module-level build.gradle files.
        """
          .trimIndent()
      )
    }
  }

  class AggregatedTestReportCreationAction(creationConfig: GlobalTaskCreationConfig) : BaseCreationAction(creationConfig) {
    override val name = "testAllSuitesWithDependencies"
    override val artifactType = InternalMultipleArtifactType.ALL_PROJECT_TEST_RESULTS
    override val coverageArtifactType = InternalMultipleArtifactType.AGGREGATED_CODE_COVERAGE_DATA

    override fun handleProvider(taskProvider: TaskProvider<TestReportTask>) {
      super.handleProvider(taskProvider)

      creationConfig.globalArtifacts
        .setInitialProvider(taskProvider, TestReportTask::coverageHtmlReportDir)
        .on(InternalArtifactType.AGGREGATED_CODE_COVERAGE_HTML_REPORT)
    }

    override fun configure(task: TestReportTask) {
      super.configure(task)
      task.description =
        "Generates an aggregated test results report for unit and instrumentation tests across the current module and its project dependencies."
      task.testReport.set(task.project.layout.buildDirectory.dir("reports/tests/aggregated-test-report"))
    }
  }

  class TestReportCreationAction(creationConfig: GlobalTaskCreationConfig) : BaseCreationAction(creationConfig) {
    override val name = "testAllSuites"
    override val artifactType = InternalMultipleArtifactType.PROJECT_LEVEL_TEST_RESULTS
    override val coverageArtifactType = InternalMultipleArtifactType.CODE_COVERAGE_DATA

    override fun handleProvider(taskProvider: TaskProvider<TestReportTask>) {
      super.handleProvider(taskProvider)

      creationConfig.globalArtifacts
        .setInitialProvider(taskProvider, TestReportTask::coverageHtmlReportDir)
        .on(InternalArtifactType.CODE_COVERAGE_HTML_REPORT)
    }

    override fun configure(task: TestReportTask) {
      super.configure(task)
      task.description = "Generates a test results report for unit and instrumentation tests within the current module."
      task.testReport.set(task.project.layout.buildDirectory.dir("reports/tests/test-report"))
    }
  }

  abstract class BaseCreationAction(val creationConfig: GlobalTaskCreationConfig) : GlobalTaskCreationAction<TestReportTask>() {

    abstract val artifactType: InternalMultipleArtifactType<Directory>
    abstract val coverageArtifactType: InternalMultipleArtifactType<Directory>
    override val type = TestReportTask::class.java

    override fun configure(task: TestReportTask) {
      super.configure(task)
      task.testResults.set(creationConfig.globalArtifacts.getAll(artifactType))
      task.testResults.disallowChanges()
      task.rootProjectName.set(creationConfig.services.projectInfo.rootProjectName)

      task.coverageXmlReports.set(creationConfig.globalArtifacts.getAll(coverageArtifactType))
      task.rootProjectDir.set(creationConfig.services.projectInfo.rootDir)
    }
  }
}
