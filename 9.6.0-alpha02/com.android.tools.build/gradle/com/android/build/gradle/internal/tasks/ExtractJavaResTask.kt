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

import com.android.build.gradle.internal.caching.DisabledCachingReason
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.utils.isValidZipEntryName
import com.android.builder.utils.isValidZipEntryPath
import com.android.utils.FileUtils
import com.android.zipflinger.ZipRepo
import java.nio.file.Files
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * Used when `android.experimental.enableJavaResourceOptimizations=true` to extract compressed project Java resources JAR into a directory.
 */
@DisableCachingByDefault(because = DisabledCachingReason.FAST_TASK)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.JAVA_RESOURCES)
abstract class ExtractJavaResTask : NonIncrementalTask() {

  @get:Classpath abstract val javaResCompressedJar: RegularFileProperty

  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  override fun doTaskAction() {
    val outDir = outputDir.get().asFile
    FileUtils.cleanOutputDir(outDir)
    val outRoot = outDir.toPath().toAbsolutePath().normalize()
    val jarFile = javaResCompressedJar.get().asFile
    if (jarFile != null && jarFile.exists()) {
      ZipRepo(jarFile.toPath()).use { zip ->
        for (entry in zip.entries.values) {
          if (!entry.isDirectory) {
            val target =
              if (isValidZipEntryName(entry.name)) {
                outRoot.resolve(entry.name.replace('\\', '/')).normalize()
              } else {
                error(
                  "Unable to extract java resource entry from '${jarFile.path}'. Entry name '${entry.name}' contains illegal characters."
                )
              }
            if (!target.startsWith(outRoot) || !isValidZipEntryPath(target.toFile(), outDir)) {
              throw InvalidUserDataException("Refusing to extract zip entry outside of $outRoot: ${entry.name} from $jarFile")
            }
            Files.createDirectories(target.parent)
            zip.getInputStream(entry.name).use { input -> Files.copy(input, target) }
          }
        }
      }
    }
  }

  class CreationAction(creationConfig: ComponentCreationConfig) :
    VariantTaskCreationAction<ExtractJavaResTask, ComponentCreationConfig>(creationConfig) {

    override val name: String
      get() = computeTaskName("extract", "JavaRes")

    override val type: Class<ExtractJavaResTask>
      get() = ExtractJavaResTask::class.java

    override fun handleProvider(taskProvider: TaskProvider<ExtractJavaResTask>) {
      super.handleProvider(taskProvider)

      creationConfig.artifacts
        .setInitialProvider(taskProvider, ExtractJavaResTask::outputDir)
        .withName("out")
        .on(InternalArtifactType.JAVA_RES)
    }

    override fun configure(task: ExtractJavaResTask) {
      super.configure(task)
      task.javaResCompressedJar.setDisallowChanges(creationConfig.artifacts.get(InternalArtifactType.JAVA_RES_COMPRESSED_JAR))
    }
  }
}
