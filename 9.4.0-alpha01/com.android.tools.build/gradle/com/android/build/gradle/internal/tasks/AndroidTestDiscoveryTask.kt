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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import java.io.File
import java.util.zip.ZipFile
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.objectweb.asm.ClassReader

/**
 * Task to discover tests in the compiled classes of an instrumentation test variant. It produces a test-list.txt file in the output
 * directory if tests are found. If no tests are found, the output directory remains empty, causing Gradle to skip dependent tasks that
 * use @SkipWhenEmpty on this directory.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class AndroidTestDiscoveryTask : NewIncrementalTask() {

  @get:Classpath abstract val testClasses: ConfigurableFileCollection

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  override fun doTaskAction(inputChanges: org.gradle.work.InputChanges) {
    val discoveredTests = mutableSetOf<String>()

    testClasses.files.forEach { file ->
      if (file.isDirectory) {
        file
          .walkTopDown()
          .filter { it.isFile && it.extension == "class" }
          .forEach { classFile -> scanClassFile(classFile, discoveredTests) }
      } else if (file.extension == "jar") {
        ZipFile(file).use { zipFile ->
          zipFile
            .entries()
            .asSequence()
            .filter { it.name.endsWith(SdkConstants.DOT_CLASS) }
            .forEach { entry -> zipFile.getInputStream(entry).use { inputStream -> scanClass(inputStream.readBytes(), discoveredTests) } }
        }
      }
    }

    val outputFile = outputDirectory.file("test-list.txt").get().asFile
    outputFile.writeText(discoveredTests.sorted().joinToString("\n"))
  }

  private fun scanClassFile(classFile: File, discoveredTests: MutableSet<String>) {
    scanClass(classFile.readBytes(), discoveredTests)
  }

  private fun scanClass(byteCode: ByteArray, discoveredTests: MutableSet<String>) {
    val reader = ClassReader(byteCode)
    val visitor = TestDiscoveryClassVisitor()
    reader.accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    if (visitor.isTestClass) {
      discoveredTests.add(reader.className.replace('/', '.'))
    }
  }

  class CreationAction(creationConfig: ApkCreationConfig) :
    VariantTaskCreationAction<AndroidTestDiscoveryTask, ApkCreationConfig>(creationConfig) {
    override val name: String
      get() = computeTaskName("merge", "AndroidTestDiscovery")

    override val type: Class<AndroidTestDiscoveryTask>
      get() = AndroidTestDiscoveryTask::class.java

    override fun handleProvider(taskProvider: org.gradle.api.tasks.TaskProvider<AndroidTestDiscoveryTask>) {
      super.handleProvider(taskProvider)
      creationConfig.artifacts
        .setInitialProvider(taskProvider, AndroidTestDiscoveryTask::outputDirectory)
        .on(InternalArtifactType.ANDROID_TEST_DISCOVERY_LIST)
    }

    override fun configure(task: AndroidTestDiscoveryTask) {
      super.configure(task)
      task.testClasses.from(
        creationConfig.artifacts
          .forScope(com.android.build.api.variant.ScopedArtifacts.Scope.PROJECT)
          .getFinalArtifacts(com.android.build.api.artifact.ScopedArtifact.POST_COMPILATION_CLASSES)
      )
    }
  }
}
