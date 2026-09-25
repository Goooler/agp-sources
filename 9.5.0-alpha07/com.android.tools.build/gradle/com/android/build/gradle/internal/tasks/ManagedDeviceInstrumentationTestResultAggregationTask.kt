/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.test.report.TestReport
import com.android.build.gradle.internal.test.report.XMLReportAggregator
import com.android.build.gradle.internal.test.report.processTestReportAggregation
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.CONNECTED_TEST_TEST_SUITE_NAME
import com.android.buildanalyzer.common.TaskCategory
import com.android.utils.FileUtils
import java.io.File
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.work.DisableCachingByDefault

/** Aggregates XML test results into one. */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class ManagedDeviceInstrumentationTestResultAggregationTask : NonIncrementalTask() {

  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) abstract val deviceTestResultDirs: ConfigurableFileCollection

  @get:OutputDirectory abstract val outputTestReportHtmlDir: DirectoryProperty

  @get:Input abstract val testReportAggregationEnabled: Property<Boolean>

  @get:Input abstract val testedVariantName: Property<String>

  @get:Optional @get:OutputDirectory abstract val xmlResultsDirectory: DirectoryProperty

  override fun doTaskAction() {
    if (testReportAggregationEnabled.getOrElse(false) && xmlResultsDirectory.isPresent) {
      val xmlResultsDirFile = xmlResultsDirectory.get().asFile
      FileUtils.cleanOutputDir(xmlResultsDirFile)
      deviceTestResultDirs.files.forEach { resultDir ->
        val targetName = resultDir.name
        processTestReportAggregation(
          resultDir,
          xmlResultsDirectory,
          projectPath.get(),
          testedVariantName.get(),
          CONNECTED_TEST_TEST_SUITE_NAME,
          targetName,
          logger,
        )
      }
      val aggregator = XMLReportAggregator(listOf(xmlResultsDirFile), projectPath.get())
      aggregator.writeReport(outputTestReportHtmlDir.get().asFile)
    } else {
      TestReport(ReportType.SINGLE_FLAVOR, deviceTestResultDirs.files.toList(), outputTestReportHtmlDir.get().asFile).generateReport()
    }

    val reportUrl = ConsoleRenderer().asClickableFileUrl(File(outputTestReportHtmlDir.get().asFile, "index.html"))
    logger.lifecycle("Test execution completed. See the report at: $reportUrl")
  }

  class CreationAction(
    creationConfig: InstrumentedTestCreationConfig,
    private val deviceTestResultDirs: List<File>,
    private val testReportHtmlOutputDir: File,
  ) : VariantTaskCreationAction<ManagedDeviceInstrumentationTestResultAggregationTask, InstrumentedTestCreationConfig>(creationConfig) {

    override val name: String
      get() = computeTaskName("merge", "TestResultProtos")

    override val type: Class<ManagedDeviceInstrumentationTestResultAggregationTask>
      get() = ManagedDeviceInstrumentationTestResultAggregationTask::class.java

    override fun handleProvider(taskProvider: TaskProvider<ManagedDeviceInstrumentationTestResultAggregationTask>) {
      creationConfig.artifacts
        .setInitialProvider(taskProvider, ManagedDeviceInstrumentationTestResultAggregationTask::outputTestReportHtmlDir)
        .withName("allDevices")
        .atLocation(testReportHtmlOutputDir.absolutePath)
        .on(InternalArtifactType.MANAGED_DEVICE_ANDROID_TEST_MERGED_RESULTS_REPORT)
    }

    override fun configure(task: ManagedDeviceInstrumentationTestResultAggregationTask) {
      super.configure(task)

      task.deviceTestResultDirs.from(deviceTestResultDirs).disallowChanges()

      val projectOptions = creationConfig.services.projectOptions
      task.testReportAggregationEnabled.set(projectOptions.get(BooleanOption.REPORT_AGGREGATION_SUPPORT))
      task.testReportAggregationEnabled.disallowChanges()

      val variantName = (creationConfig as? DeviceTestCreationConfig)?.mainVariant?.name ?: creationConfig.name
      task.testedVariantName.set(variantName)
      task.testedVariantName.disallowChanges()

      task.xmlResultsDirectory.set(File(testReportHtmlOutputDir.parentFile, "xml_results_merged_${creationConfig.name}"))
      task.xmlResultsDirectory.disallowChanges()
    }
  }
}
