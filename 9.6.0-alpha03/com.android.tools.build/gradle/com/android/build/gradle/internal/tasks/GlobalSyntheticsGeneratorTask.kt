/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.dexing.GlobalSyntheticsGeneratorTool
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

/**
 * Generates a standalone DEX containing all possible global synthetics for the current compile SDK.
 *
 * **Optimization (b/423826264 & b/279930074):** In Debug builds, generating global synthetics per-class causes a massive merge bottleneck,
 * breaking task avoidance for dynamic feature modules. This task generates them once as a "pre-baked" artifact. The standard dexing
 * pipeline must then be configured with a no-op consumer to skip redundant generation.
 *
 * Internally uses [com.android.builder.dexing.GlobalSyntheticsGeneratorTool] to bridge the R8 classloader boundary.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.DEXING)
abstract class GlobalSyntheticsGeneratorTask : NonIncrementalTask() {

  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) abstract val bootClasspath: ConfigurableFileCollection

  @get:Input abstract val minSdk: Property<Int>

  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  override fun doTaskAction() {
    val outputLocation = outputDir.get().asFile.toPath()
    val libraryPaths = bootClasspath.files.map { it.toPath() }
    val minApi = minSdk.get()

    com.android.utils.FileUtils.cleanOutputDir(outputLocation.toFile())

    GlobalSyntheticsGeneratorTool.generate(libraryPaths, minApi, outputLocation)
  }

  class CreationAction(creationConfig: ApkCreationConfig) :
    VariantTaskCreationAction<GlobalSyntheticsGeneratorTask, ApkCreationConfig>(creationConfig) {

    override val name: String
      get() = computeTaskName("generate", "GlobalSynthetics")

    override val type: Class<GlobalSyntheticsGeneratorTask>
      get() = GlobalSyntheticsGeneratorTask::class.java

    override fun handleProvider(taskProvider: TaskProvider<GlobalSyntheticsGeneratorTask>) {
      super.handleProvider(taskProvider)
      creationConfig.artifacts
        .setInitialProvider(taskProvider, GlobalSyntheticsGeneratorTask::outputDir)
        .on(InternalArtifactType.GLOBAL_SYNTHETICS_DEX)
    }

    override fun configure(task: GlobalSyntheticsGeneratorTask) {
      super.configure(task)
      task.minSdk.setDisallowChanges(creationConfig.dexing.minSdkVersionForDexing)
      task.bootClasspath.from(creationConfig.global.bootClasspath)
    }
  }
}
