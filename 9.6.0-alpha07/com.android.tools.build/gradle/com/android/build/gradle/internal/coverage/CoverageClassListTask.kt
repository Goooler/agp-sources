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

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.BaseServices
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.buildanalyzer.common.TaskCategory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

/**
 * Writes the list of the classes for the on-device coverage agent to instrument (see [writeCoverageClassList]), from the class folders of
 * the tested variant that the coverage report covers.
 *
 * The test engine puts the list next to the agent. With it, the agent instruments exactly the classes that the build-time report covers,
 * whatever their package, instead of the classes under the package prefix: for example the classes in `hilt_aggregated_deps`, and not the
 * test classes or the classes of other modules.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class CoverageClassListTask : NonIncrementalTask() {

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val classFolders: ConfigurableFileCollection

  @get:OutputFile abstract val classList: RegularFileProperty

  override fun doTaskAction() {
    writeCoverageClassList(classFolders.files, classList.get().asFile)
  }

  /**
   * Creates the task for [creationConfig], the component of the tests, from [classFolders], the classes that its coverage report covers.
   */
  class CreationAction(creationConfig: ComponentCreationConfig, private val classFolders: FileCollection) :
    VariantTaskCreationAction<CoverageClassListTask, ComponentCreationConfig>(creationConfig) {

    override val name: String = computeTaskName("generate", "CoverageClassList")
    override val type: Class<CoverageClassListTask>
      get() = CoverageClassListTask::class.java

    override fun handleProvider(taskProvider: TaskProvider<CoverageClassListTask>) {
      creationConfig.artifacts
        .setInitialProvider(taskProvider, CoverageClassListTask::classList)
        .withName(FILE_NAME)
        .on(InternalArtifactType.ON_DEVICE_COVERAGE_CLASS_LIST)
    }

    override fun configure(task: CoverageClassListTask) {
      super.configure(task)
      task.classFolders.fromDisallowChanges(classFolders)
    }
  }

  companion object {
    /**
     * The instrumentation argument in which the Android Gradle plugin passes the path of the list to the test engine, as it passes the
     * agent's package prefix in `com.android.tools.coverage.prefixes`.
     */
    const val INSTRUMENTATION_ARG = "com.android.tools.coverage.classList"

    private const val FILE_NAME = "coverage_classes.txt"

    /**
     * Returns whether the tests of a component get a class list: they collect coverage ([codeCoverageEnabled]) with the on-device agent. An
     * older agent that the project pins doesn't read the list, and instruments the classes under its package prefix as before.
     */
    @JvmStatic
    fun isEnabled(services: BaseServices, codeCoverageEnabled: Boolean): Boolean =
      codeCoverageEnabled && services.projectOptions[BooleanOption.ENABLE_ON_THE_FLY_CODE_COVERAGE]
  }
}

/**
 * Writes [listFile], the list of the classes for the on-device coverage agent to instrument: the classes in [classFolders] that the
 * coverage report covers (see [findProjectClasses]), without `package-info` and `module-info`, which the build-time report skips too.
 *
 * The header, with a version and the number of classes, and the end marker let the agent tell a complete list from a missing or partial
 * one. The file is written next to its final location and moved into place, so it's never partial. Without classes, there is no file, and
 * the agent instruments the classes under its package prefix.
 */
internal fun writeCoverageClassList(classFolders: Collection<File>, listFile: File) {
  val classes =
    findProjectClasses(classFolders).filterNot { it == "module-info" || it == "package-info" || it.endsWith("/package-info") }.sorted()
  Files.deleteIfExists(listFile.toPath())
  if (classes.isEmpty()) {
    return
  }
  listFile.parentFile.mkdirs()
  val partialFile = File(listFile.parentFile, "${listFile.name}.tmp")
  partialFile.bufferedWriter().use { writer ->
    writer.write("#coverage-classes v1 count=${classes.size}\n")
    for (className in classes) {
      writer.write(className)
      writer.write("\n")
    }
    writer.write("#end\n")
  }
  Files.move(partialFile.toPath(), listFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
