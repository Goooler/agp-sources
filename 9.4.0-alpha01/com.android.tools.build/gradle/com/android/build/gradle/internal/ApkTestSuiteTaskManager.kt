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

import com.android.build.api.variant.impl.TestSuiteSourceContainer
import com.android.build.gradle.internal.api.TestApkTestSuiteSourceSet
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.creationconfig.forTestSuite
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import com.android.build.gradle.internal.testsuites.impl.BuiltInKotlinCreationConfigImpl
import com.android.build.gradle.internal.testsuites.impl.JavaCompileCreationConfigForTestSuite
import com.android.build.gradle.internal.testsuites.impl.JavaPreCompileTaskCreationConfigImpl
import com.android.build.gradle.tasks.JavaCompileCreationAction
import com.android.build.gradle.tasks.JavaPreCompileTask
import com.android.build.gradle.tasks.ProcessTestManifest
import com.android.build.gradle.tasks.ProcessTestManifestPackaging
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/** Task manager responsible for creating all tasks necessary to process a [TestSuiteSourceSet.TestApk] source set. */
class ApkTestSuiteTaskManager(val project: Project, val testSuiteTaskManager: TestSuiteTaskManager) {

  fun createTasks(
    testSuite: TestSuiteCreationConfig,
    sourceContainer: TestSuiteSourceContainer,
    source: TestApkTestSuiteSourceSet,
    taskFactory: TaskFactory,
    taskCreationServices: TaskCreationServices,
  ): TaskProvider<out Task> {
    val taskConfig = forTestSuite(testSuite, sourceContainer, source)
    assert(taskConfig != null) { "ApkTestSuiteTaskManager create tasks should be called for Apk and Libraries only" }
    taskFactory.register(ProcessTestManifestPackaging.CreationAction(taskConfig!!))
    taskFactory.register(ProcessTestManifest.CreationAction(taskConfig))

    val javaPreCompileTaskCreationConfig = JavaPreCompileTaskCreationConfigImpl(testSuite, sourceContainer, taskCreationServices)
    val preBuildTask = taskFactory.register(TaskManager.PreBuildCreationAction(javaPreCompileTaskCreationConfig))
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

    return testSuiteTaskManager.createApkTestSuiteTasks(testSuite, sourceContainer, javacTask, preBuildTask)
  }
}
