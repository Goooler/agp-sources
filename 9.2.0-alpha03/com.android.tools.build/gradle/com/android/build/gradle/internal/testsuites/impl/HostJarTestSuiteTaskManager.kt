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

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.LifecycleTasksImpl
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.api.variant.impl.TestSuiteSourceContainer
import com.android.build.gradle.internal.TaskManager.PreBuildCreationAction
import com.android.build.gradle.internal.TestSuiteTaskManager
import com.android.build.gradle.internal.api.HostJarTestSuiteSourceSet
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.ProcessJavaResTask
import com.android.build.gradle.internal.tasks.creationconfig.ProcessJavaResCreationConfig
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskFactory
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

    // first process java resources.
    val config =
      object : ProcessJavaResCreationConfig {
        override val extraClasses: Collection<FileCollection>
          get() = listOf()

        override val useBuiltInKotlinSupport: Boolean
          get() = false // so far, since we don't compile yet.

        override val packageJacocoRuntime: Boolean
          get() = false

        override val annotationProcessorConfiguration: Configuration?
          get() = null

        override val sources: FlatSourceDirectoriesImpl
          get() = source.resources

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

    createCompilationTasks(testSuite, sourceContainer, source, taskFactory, taskCreationServices)
    return task
  }

  private fun createCompilationTasks(
    testSuite: TestSuiteCreationConfig,
    sourceContainer: TestSuiteSourceContainer,
    source: HostJarTestSuiteSourceSet,
    taskFactory: TaskFactory,
    taskCreationServices: TaskCreationServices,
  ) {

    val javaPreCompileTaskCreationConfig = JavaPreCompileTaskCreationConfigImpl(testSuite, sourceContainer, taskCreationServices)
    taskFactory.register(PreBuildCreationAction(javaPreCompileTaskCreationConfig))

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
    testSuiteTaskManager.setupCompilationContext(
      artifacts = sourceContainer.artifacts,
      useBuiltInKotlinSupport = true,
      useBuiltInKaptSupport = false,
    )

    val builtInCreationConfig =
      BuiltInKotlinCreationConfigImpl(
        testSuite = testSuite,
        sourceContainer = sourceContainer,
        source = source,
        testedVariant = testSuite.testedVariant,
        services = taskCreationServices,
        javaPreCompileTaskCreationConfig.taskContainer,
        javacTask,
      )
    testSuiteTaskManager.maybeCreateKotlinTasks(builtInCreationConfig)

    /// TODO : add asm processing pipeline and lint task
  }
}
