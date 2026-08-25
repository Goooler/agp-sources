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

package com.android.build.gradle.internal

import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.api.component.impl.computeTaskName
import com.android.build.api.dsl.TestTaskContext
import com.android.build.api.variant.impl.TestSuiteSourceContainer
import com.android.build.api.variant.impl.capitalizeFirstChar
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.AndroidTestDiscoveryTask
import com.android.build.gradle.internal.tasks.CompressAssetsTask
import com.android.build.gradle.internal.tasks.DeviceSerialTestTask
import com.android.build.gradle.internal.tasks.ProcessNavigationXmlTask
import com.android.build.gradle.internal.tasks.SigningConfigVersionsWriterTask
import com.android.build.gradle.internal.tasks.SigningConfigWriterTask
import com.android.build.gradle.internal.tasks.StripDebugSymbolsTask
import com.android.build.gradle.internal.tasks.ValidateResourcesTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.internal.testsuites.impl.TestSuiteApkCreationConfig
import com.android.build.gradle.internal.testsuites.impl.TestSuiteHostJarCreationConfig
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.CompileNavigationXmlTask
import com.android.build.gradle.tasks.TestSuiteTestTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile

class TestSuiteTaskManager(project: Project, globalConfig: GlobalTaskCreationConfig) : TaskManager(project, globalConfig) {

  fun createApkTestSuiteTasks(
    testSuite: TestSuiteCreationConfig,
    sourceContainer: TestSuiteSourceContainer,
    javacTask: TaskProvider<out JavaCompile>,
    preBuildTask: TaskProvider<out Task>,
  ): TaskProvider<out Task> {
    val apkCreationConfig = TestSuiteApkCreationConfig(testSuite, sourceContainer)

    val taskContainer = apkCreationConfig.taskContainer
    taskContainer.javacTask = javacTask
    taskContainer.preBuildTask = preBuildTask

    val sourceGenTask = taskFactory.register(apkCreationConfig.computeTaskNameInternal("generate", "Sources"))
    taskContainer.sourceGenTask = sourceGenTask

    val resourceGenTask = taskFactory.register(ValidateResourcesTask.CreateAction(apkCreationConfig))
    taskContainer.resourceGenTask = resourceGenTask

    val assetGenTask = taskFactory.register(apkCreationConfig.computeTaskNameInternal("generate", "Assets"))
    taskContainer.assetGenTask = assetGenTask

    val assembleTaskName = apkCreationConfig.computeTaskNameInternal("assemble")
    val assembleTask = taskFactory.register(assembleTaskName)
    taskContainer.assembleTask = assembleTask

    createMergeResourcesTask(apkCreationConfig, true, emptySet())
    val appCompileRClass = apkCreationConfig.services.projectOptions[BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS]
    if (appCompileRClass) {
      basicCreateMergeResourcesTask(
        apkCreationConfig,
        MergeType.PACKAGE,
        includeDependencies = false,
        processResources = false,
        alsoOutputNotCompiledResources = false,
        emptySet(),
        null,
      )
    }
    createMergeAssetsTask(apkCreationConfig)
    taskFactory.register(CompressAssetsTask.CreationAction(apkCreationConfig))
    createNavigationProcessingTasks(apkCreationConfig)
    createApkProcessResTask(apkCreationConfig)
    createProcessJavaResTask(apkCreationConfig, apkCreationConfig.packaging)
    createMergeJniLibFoldersTasks(apkCreationConfig)
    taskFactory.register(StripDebugSymbolsTask.CreationAction(apkCreationConfig))
    createPostCompilationTasks(apkCreationConfig)
    createValidateSigningTask(apkCreationConfig)

    taskFactory.register(SigningConfigWriterTask.CreationAction(apkCreationConfig))
    taskFactory.register(SigningConfigVersionsWriterTask.CreationAction(apkCreationConfig))

    taskFactory.register(AndroidTestDiscoveryTask.CreationAction(apkCreationConfig))

    createPackagingTask(apkCreationConfig)

    return assembleTask
  }

  fun createHostJarTestSuiteResourcesTasks(hostJarConfig: TestSuiteHostJarCreationConfig) {
    // Add a task to process the manifest
    createProcessTestManifestTask(hostJarConfig)

    // Add a task to create the res values
    createGenerateResValuesTask(hostJarConfig)

    // Add a task to merge the assets folders
    createMergeAssetsTask(hostJarConfig, includeDependencies = true)

    createMergeResourcesTask(hostJarConfig, true, emptySet())

    // Add a task to process the Android Resources and generate source files
    createApkProcessResTask(hostJarConfig, InternalArtifactType.FEATURE_RESOURCE_PKG)
  }

  fun createTestSuiteProcessTestManifestTask(config: TestComponentCreationConfig) {
    createProcessTestManifestTask(config)
  }

  fun createAnchorTasksForSuite(creationConfig: ComponentCreationConfig) {
    createAnchorTasks(creationConfig)
  }

  override val javaResMergingScopes: Set<InternalScopedArtifacts.InternalScope>
    get() = setOf()

  fun createTasks(creationConfig: TestSuiteCreationConfig) {
    // first create all tasks related to processing the source folders.
    val allSourcesProcessingTasks =
      creationConfig.sourceContainers.mapNotNull { testSuiteSourceContainer ->
        testSuiteSourceContainer.createTasks(
          testSuiteTaskManager = this,
          taskCreationServices = creationConfig.services,
          creationConfig = creationConfig,
        )
      }
    val connectedCheckSerials: Provider<List<String>> =
      taskFactory.named(globalConfig.taskNames.connectedCheck).flatMap { test -> (test as DeviceSerialTestTask).serialValues }

    val tasks = mutableListOf(Pair("test", false))
    if (creationConfig.requiresUpdateTask.get()) {
      tasks.add(Pair("update", true))
    }

    tasks.forEach { (verb, isUpdate) ->
      creationConfig.targets
        .filter { it.value.enabled }
        .forEach { mapEntry ->
          val target = mapEntry.value
          val taskName =
            computeTaskName(
              creationConfig.testedVariant.name,
              "${verb}${creationConfig.name.capitalizeFirstChar()}${target.uniqueName.capitalizeFirstChar()}",
              "TestSuite",
            )
          val testSuiteTestTask =
            taskFactory.register(TestSuiteTestTask.CreationAction(creationConfig, target, taskName, connectedCheckSerials))
          val context =
            object : TestTaskContext {
              override val targetName: String
                get() = target.name

              override val isUpdateTask: Boolean
                get() = isUpdate

              override val suiteName: String
                get() = creationConfig.name

              override val targetedVariant: String
                get() = creationConfig.testedVariant.name

              override val targetedDevices: Collection<String>
                get() = target.targetDevices

              override fun toString(): String {
                return super.toString() +
                  "targetName:$targetName, isUpdateTask:$isUpdateTask, suiteName:$suiteName," +
                  " targetedVariant:$targetedVariant, " +
                  "devices = ${targetedDevices.joinToString(separator = ":")}"
              }
            }
          creationConfig.runTestTaskConfigurationActions(context, testSuiteTestTask)

          // add sources processing dependencies
          allSourcesProcessingTasks.forEach { sourceProcessingTask -> testSuiteTestTask.dependsOn(sourceProcessingTask) }

          // Adds GMD Setup task dependency.
          target.targetDevices.forEach { targetDeviceName ->
            val targetDevice = creationConfig.global.androidTestOptions.managedDevices.localDevices.getByName(targetDeviceName)
            testSuiteTestTask.dependsOn(setupTaskName(targetDevice))
          }
        }
    }
  }

  private fun createNavigationProcessingTasks(creationConfig: ComponentCreationConfig) {
    taskFactory.register(ProcessNavigationXmlTask.LibraryCreationAction(creationConfig))
    taskFactory.register(CompileNavigationXmlTask.CreationAction(creationConfig))
  }
}
