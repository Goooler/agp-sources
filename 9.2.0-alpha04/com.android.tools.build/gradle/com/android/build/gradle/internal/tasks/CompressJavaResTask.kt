/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.android.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants.DOT_JAR
import com.android.build.gradle.internal.dependency.UncompressedJavaRes
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.creationconfig.ProcessJavaResCreationConfig
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

/** Task to compress project Java resources into a JAR. */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.JAVA_RESOURCES)
abstract class CompressJavaResTask @Inject constructor(@get:Internal internal val archiveOperations: ArchiveOperations) :
  NonIncrementalTask() {

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val javaResFiles: ConfigurableFileCollection

  @get:OutputFile abstract val outputFile: RegularFileProperty

  override fun doTaskAction() {
    UncompressedJavaRes.FileTree(javaResFiles.asFileTree).compressToJar(outputFile.get().asFile)
  }

  class CreationAction(creationConfig: ProcessJavaResCreationConfig) :
    VariantTaskCreationAction<CompressJavaResTask, ProcessJavaResCreationConfig>(creationConfig) {

    override val name: String
      get() = computeTaskName("compress", "JavaRes")

    override val type: Class<CompressJavaResTask>
      get() = CompressJavaResTask::class.java

    override fun handleProvider(taskProvider: TaskProvider<CompressJavaResTask>) {
      super.handleProvider(taskProvider)

      creationConfig.artifacts
        .setInitialProvider(taskProvider, CompressJavaResTask::outputFile)
        .withName("java_res.jar")
        .on(InternalArtifactType.JAVA_RES_COMPRESSED_JAR)
    }

    override fun configure(task: CompressJavaResTask) {
      super.configure(task)
      task.javaResFiles.from(getProjectJavaRes(creationConfig, task, task.archiveOperations))
    }
  }
}

private fun getProjectJavaRes(
  creationConfig: ProcessJavaResCreationConfig,
  task: CompressJavaResTask,
  archiveOperations: ArchiveOperations,
): FileCollection {
  fun zipTree(jarFile: File): FileTree = archiveOperations.zipTree(jarFile)

  val javaRes = creationConfig.services.fileCollection()
  javaRes.from(creationConfig.sources?.getAsFileTrees())
  // use lazy file collection here in case an annotationProcessor dependency is add via
  // Configuration.defaultDependencies(), for example.
  javaRes.from(
    Callable {
      if (projectHasAnnotationProcessors(creationConfig)) {
        creationConfig.artifacts.get(InternalArtifactType.JAVAC)
      } else {
        listOf<File>()
      }
    }
  )

  creationConfig.extraClasses.forEach {
    javaRes.from(it.filter { file -> !file.name.endsWith(DOT_JAR) })

    javaRes.from(it.filter { file -> file.name.endsWith(DOT_JAR) }.elements.map { jars -> jars.map { jar -> zipTree(jar.asFile) } })
  }

  if (creationConfig.useBuiltInKotlinSupport) {
    // Also collect `.kotlin_module` files (see b/446696613)
    javaRes.from(creationConfig.artifacts.get(InternalArtifactType.BUILT_IN_KOTLINC))
  }

  if (creationConfig.packageJacocoRuntime) {
    javaRes.from(creationConfig.artifacts.get(InternalArtifactType.JACOCO_CONFIG_RESOURCES))
  }
  return javaRes.asFileTree.matching(MergeJavaResourceTask.patternSet)
}
