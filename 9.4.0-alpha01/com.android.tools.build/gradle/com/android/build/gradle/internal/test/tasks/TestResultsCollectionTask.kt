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

package com.android.build.gradle.internal.test.tasks

import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import java.io.File
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class TestResultsCollectionTask : NonIncrementalTask() {

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) @get:Optional abstract val testSuiteResults: ListProperty<Directory>

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) @get:Optional abstract val unitTestResults: DirectoryProperty

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) @get:Optional abstract val androidTestResults: DirectoryProperty

  @get:InputFiles
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val dependentModuleTestResults: ConfigurableFileCollection

  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  override fun doTaskAction() {
    val outputDir = this.outputDir.get().asFile

    val copyXmls = { directory: File ->
      if (directory.exists()) {
        directory
          .listFiles { file -> file.extension == "xml" }
          ?.forEach { xmlFile ->
            val targetFile = outputDir.resolve(xmlFile.name)
            xmlFile.copyTo(targetFile, overwrite = true)
          }
      }
    }

    if (testSuiteResults.isPresent) {
      testSuiteResults.get().forEach { directory -> copyXmls(directory.asFile) }
    }

    if (unitTestResults.isPresent) {
      copyXmls(unitTestResults.get().asFile)
    }

    if (androidTestResults.isPresent) {
      copyXmls(androidTestResults.get().asFile)
    }

    dependentModuleTestResults.asFileTree.forEach { xmlFile ->
      val targetFile = outputDir.resolve(xmlFile.name)
      xmlFile.copyTo(targetFile, overwrite = true)
    }
  }

  class AggregatedTestResultsCollectionCreationAction(creationConfig: VariantCreationConfig) :
    BaseTestResultsCollectionCreationAction(creationConfig) {

    override val name = computeTaskName("aggregatedTestResultsCollection")

    override fun configure(task: TestResultsCollectionTask) {
      super.configure(task)

      task.dependentModuleTestResults.from(
        creationConfig.variantDependencies.getArtifactFileCollection(
          AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
          AndroidArtifacts.ArtifactScope.PROJECT,
          AndroidArtifacts.ArtifactType.TEST_RESULTS,
        )
      )
    }

    override fun handleProvider(taskProvider: TaskProvider<TestResultsCollectionTask>) {
      super.handleProvider(taskProvider)
      creationConfig.global.globalArtifacts
        .use(taskProvider)
        .wiredWith(TestResultsCollectionTask::outputDir)
        .toAppendTo(InternalMultipleArtifactType.ALL_PROJECT_TEST_RESULTS)
    }
  }

  class TestResultsCollectionCreationAction(creationConfig: VariantCreationConfig) :
    BaseTestResultsCollectionCreationAction(creationConfig) {

    override val name = computeTaskName("testResultsCollection")

    override fun handleProvider(taskProvider: TaskProvider<TestResultsCollectionTask>) {
      super.handleProvider(taskProvider)

      creationConfig.artifacts
        .setInitialProvider(taskProvider, TestResultsCollectionTask::outputDir)
        .on(InternalArtifactType.VARIANT_TEST_RESULTS)

      creationConfig.global.globalArtifacts
        .use(taskProvider)
        .wiredWith(TestResultsCollectionTask::outputDir)
        .toAppendTo(InternalMultipleArtifactType.PROJECT_LEVEL_TEST_RESULTS)
    }
  }

  abstract class BaseTestResultsCollectionCreationAction(creationConfig: VariantCreationConfig) :
    VariantTaskCreationAction<TestResultsCollectionTask, VariantCreationConfig>(creationConfig) {
    override val type = TestResultsCollectionTask::class.java

    override fun configure(task: TestResultsCollectionTask) {
      super.configure(task)

      task.testSuiteResults.set(creationConfig.artifacts.getAll(InternalMultipleArtifactType.TEST_SUITE_RESULTS))

      task.unitTestResults.set(creationConfig.artifacts.get(InternalArtifactType.UNIT_TEST_RESULTS))

      task.androidTestResults.set(creationConfig.artifacts.get(InternalArtifactType.ANDROID_TEST_RESULTS))
    }
  }
}
