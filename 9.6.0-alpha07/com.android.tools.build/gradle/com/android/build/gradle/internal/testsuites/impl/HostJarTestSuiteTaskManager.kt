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

package com.android.build.gradle.internal.testsuites.impl

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.LifecycleTasksImpl
import com.android.build.api.variant.Packaging
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.api.variant.impl.TestSuiteSourceContainer
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.TestSuiteTaskManager
import com.android.build.gradle.internal.api.HostJarTestSuiteSourceSet
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.res.GenerateLibraryRFileTask
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.PackageForHostTest
import com.android.build.gradle.internal.tasks.ProcessJavaResTask
import com.android.build.gradle.internal.tasks.creationconfig.ProcessJavaResCreationConfig
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import com.android.build.gradle.tasks.GenerateTestConfig
import com.android.build.gradle.tasks.JavaCompileCreationAction
import com.android.build.gradle.tasks.JavaPreCompileTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider

/**
 * Task manager responsible for creating all tasks necessary to process a [com.android.build.api.variant.TestSuiteSourceSet.HostJar] source
 * set.
 */
class HostJarTestSuiteTaskManager(val project: Project, val testSuiteTaskManager: TestSuiteTaskManager) {

  /**
   * Creates all necessary tasks to process a [com.android.build.api.variant.TestSuiteSourceSet.HostJar] type of source set.
   *
   * @return the final [org.gradle.api.tasks.TaskProvider] that can be used as a dependent of the
   *   [com.android.build.gradle.tasks.TestSuiteTestTask].
   */
  internal fun createTasks(
    testSuite: TestSuiteCreationConfig,
    sourceContainer: TestSuiteSourceContainer,
    source: HostJarTestSuiteSourceSet,
    taskFactory: TaskFactory,
    taskCreationServices: TaskCreationServices,
  ): TaskProvider<out Task> {
    val hostJarConfig = sourceContainer.creationConfig as TestSuiteHostJarCreationConfig

    testSuiteTaskManager.createAnchorTasksForSuite(hostJarConfig)
    testSuiteTaskManager.createAssembleTaskForSuite(hostJarConfig)

    // first process java resources.
    val config =
      object : ProcessJavaResCreationConfig {
        override val extraClasses: Collection<FileCollection>
          get() = listOf()

        override val useBuiltInKotlinSupport: Boolean
          get() = hostJarConfig.useBuiltInKotlinSupport

        override val packageJacocoRuntime: Boolean
          get() = false

        override val annotationProcessorConfiguration: Configuration?
          get() = null

        override val sources: FlatSourceDirectoriesImpl
          get() = source.resources

        override val packaging: Packaging
          get() = testSuite.testedVariant.packaging

        override fun setJavaResTask(task: TaskProvider<out Sync>) {}

        override val name: String
          get() = sourceContainer.identifier

        override val services: TaskCreationServices
          get() = taskCreationServices

        override val taskContainer: MutableTaskContainer
          get() = throw RuntimeException("Test Suites should not access the deprecated `taskContainer`")

        override val artifacts: ArtifactsImpl
          get() = sourceContainer.artifacts

        override val global: GlobalTaskCreationConfig
          get() = testSuite.global

        override val lifecycleTasks: LifecycleTasksImpl
          get() = testSuite.testedVariant.lifecycleTasks
      }

    val task = taskFactory.register(ProcessJavaResTask.CreationAction(config))

    if (hasAndroidResources(testSuite)) {
      setupAndroidResourceTasks(testSuite, taskFactory, hostJarConfig)
    } else if (hasRuntimeStubRClasses(testSuite)) {
      // A library's runtime classes hold no R class. This generates R classes with non-zero IDs for the library and its runtime
      // dependencies, which TestSuiteTestTask.CreationAction adds to the test runtime classpath.
      taskFactory.register(GenerateLibraryRFileTask.TestRuntimeStubRClassCreationAction(hostJarConfig))
    }

    createCompilationTasks(testSuite, sourceContainer, source, taskFactory, taskCreationServices, hostJarConfig)

    setupAssembleTasks(testSuite, hostJarConfig, task)

    return hostJarConfig.taskContainer.assembleTask
  }

  /**
   * Registers the Android resource tasks, resource APK packaging ([PackageForHostTest]), and test config generation ([GenerateTestConfig])
   * for [hostJarConfig].
   */
  private fun setupAndroidResourceTasks(
    testSuite: TestSuiteCreationConfig,
    taskFactory: TaskFactory,
    hostJarConfig: TestSuiteHostJarCreationConfig,
  ) {
    val testedVariant = testSuite.testedVariant
    if (testedVariant.componentType.isApk) {
      if (testedVariant is ApplicationCreationConfig) {
        // The resources and assets of the tested application miss the ones of the dependencies that only the test suite has, so the test
        // suite merges and links its own.
        testSuiteTaskManager.createHostJarTestSuiteResourcesTasks(hostJarConfig)
        testSuite.artifacts.copy(InternalArtifactType.LINKED_RESOURCES_BINARY_FORMAT, hostJarConfig.artifacts)
        testSuite.artifacts.copy(SingleArtifact.ASSETS, hostJarConfig.artifacts)
      } else {
        // Linking the resources of a dynamic feature requires the ones of its base module.
        testSuiteTaskManager.createTestSuiteProcessTestManifestTask(hostJarConfig)
        testSuite.artifacts.copy(InternalArtifactType.LINKED_RESOURCES_BINARY_FORMAT, testedVariant.artifacts)
        testSuite.artifacts.copy(SingleArtifact.ASSETS, testedVariant.artifacts)
        hostJarConfig.artifacts.copy(SingleArtifact.ASSETS, testedVariant.artifacts)
      }
      testSuite.artifacts.copy(InternalArtifactType.MERGED_MANIFESTS, hostJarConfig.artifacts)

      taskFactory.register(PackageForHostTest.TestSuiteCreationAction(testSuite))
      hostJarConfig.artifacts.copy(InternalArtifactType.APK_FOR_LOCAL_TEST, testSuite.artifacts)
    } else if (testedVariant.componentType.isAar) {
      testSuiteTaskManager.createHostJarTestSuiteResourcesTasks(hostJarConfig)

      taskFactory.register(PackageForHostTest.CreationAction(hostJarConfig))
      testSuite.artifacts.copy(InternalArtifactType.APK_FOR_LOCAL_TEST, hostJarConfig.artifacts)
    } else {
      return
    }

    // Writes com/android/tools/test_config.properties with the paths to the resource APK, merged assets, and merged manifest.
    taskFactory.register(GenerateTestConfig.CreationAction(hostJarConfig))
  }

  private fun createCompilationTasks(
    testSuite: TestSuiteCreationConfig,
    sourceContainer: TestSuiteSourceContainer,
    source: HostJarTestSuiteSourceSet,
    taskFactory: TaskFactory,
    taskCreationServices: TaskCreationServices,
    hostJarConfig: TestSuiteHostJarCreationConfig,
  ) {

    val javaPreCompileTaskCreationConfig =
      JavaPreCompileTaskCreationConfigImpl(testSuite, sourceContainer, taskCreationServices, hostJarConfig.taskContainer)

    taskFactory.register(JavaPreCompileTask.CreationAction(javaPreCompileTaskCreationConfig))

    val javaCompileConfig =
      JavaCompileCreationConfigForTestSuite(
        sourceContainer,
        source,
        testSuite.testedVariant,
        taskCreationServices,
        javaPreCompileTaskCreationConfig.taskContainer,
      )
    val javacTask = taskFactory.register(JavaCompileCreationAction(javaCompileConfig))
    hostJarConfig.taskContainer.javacTask = javacTask
    TaskManager.setJavaCompilerTask(javacTask, hostJarConfig)

    testSuiteTaskManager.setupCompilationContext(
      artifacts = sourceContainer.artifacts,
      useBuiltInKotlinSupport = hostJarConfig.useBuiltInKotlinSupport,
      useBuiltInKaptSupport = hostJarConfig.useBuiltInKaptSupport,
    )

    val builtInCreationConfig =
      BuiltInKotlinCreationConfigImpl(
        testSuite = testSuite,
        sourceContainer = sourceContainer,
        source = source,
        testedVariant = testSuite.testedVariant,
        services = taskCreationServices,
        hostJarConfig.taskContainer,
        javacTask,
      )
    testSuiteTaskManager.maybeCreateKotlinTasks(builtInCreationConfig)

    /// TODO : add asm processing pipeline and lint task
  }

  private fun setupAssembleTasks(
    testSuite: TestSuiteCreationConfig,
    hostJarConfig: TestSuiteHostJarCreationConfig,
    javaResTask: TaskProvider<out Task>,
  ) {
    hostJarConfig.taskContainer.assembleTask.configure { task: Task ->
      task.dependsOn(hostJarConfig.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).getFinalArtifacts(ScopedArtifact.CLASSES))
      task.dependsOn(javaResTask)
      if (hasAndroidResources(testSuite)) {
        task.dependsOn(testSuite.artifacts.get(InternalArtifactType.APK_FOR_LOCAL_TEST))
        task.dependsOn(hostJarConfig.artifacts.get(InternalArtifactType.UNIT_TEST_CONFIG_DIRECTORY))
      } else if (hasRuntimeStubRClasses(testSuite)) {
        task.dependsOn(hostJarConfig.artifacts.get(InternalArtifactType.COMPILE_AND_RUNTIME_R_CLASS_JAR))
      }
    }
  }

  companion object {
    /**
     * Whether [testSuite] builds Android resources ([InternalArtifactType.APK_FOR_LOCAL_TEST] and
     * [InternalArtifactType.UNIT_TEST_CONFIG_DIRECTORY]) for its host-jar sources: the suite requests Android resources, and the tested APK
     * or AAR variant has Android resources enabled. The suite's resource tasks reuse the resource intermediates of the tested variant (for
     * example [InternalArtifactType.GENERATED_RES] and [InternalArtifactType.LOCAL_ONLY_SYMBOL_LIST]), which only a variant with Android
     * resources produces.
     */
    internal fun hasAndroidResources(testSuite: TestSuiteCreationConfig): Boolean =
      testSuite.androidResourcesIncluded &&
        testSuite.testedVariant.buildFeatures.androidResources &&
        (testSuite.testedVariant.componentType.isApk || testSuite.testedVariant.componentType.isAar)

    /**
     * Whether the test runtime classpath of [testSuite] gets generated `R` classes
     * ([InternalArtifactType.COMPILE_AND_RUNTIME_R_CLASS_JAR]): the tested variant is an Android library with Android resources enabled,
     * and the suite links no Android resources.
     */
    internal fun hasRuntimeStubRClasses(testSuite: TestSuiteCreationConfig): Boolean =
      !testSuite.androidResourcesIncluded &&
        testSuite.testedVariant.componentType.isAar &&
        testSuite.testedVariant.buildFeatures.androidResources
  }
}
