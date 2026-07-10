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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.caching.DisabledCachingReason
import com.android.utils.FileUtils
import java.nio.file.Files
import java.util.zip.ZipInputStream
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault

/**
 * A transform that extracts a Layoutlib runtime artifact (which is a ZIP) into a directory. This is used to provide the screenshot engine
 * with the necessary layoutlib data.
 */
@DisableCachingByDefault(because = DisabledCachingReason.COPY_TASK)
abstract class LayoutlibExtractor : TransformAction<GenericTransformParameters> {

  @get:PathSensitive(PathSensitivity.NAME_ONLY) @get:InputArtifact abstract val layoutlibDistributionArtifact: Provider<FileSystemLocation>

  @get:PathSensitive(PathSensitivity.NONE) @get:InputArtifactDependencies abstract val artifactDependencies: FileCollection

  override fun transform(transformOutputs: TransformOutputs) {
    val input = layoutlibDistributionArtifact.get().asFile
    val outDir = transformOutputs.dir(input.nameWithoutExtension).toPath()
    Files.createDirectories(outDir)

    ZipInputStream(input.inputStream().buffered()).use { zipInputStream ->
      while (true) {
        val entry = zipInputStream.nextEntry ?: break
        if (entry.isDirectory || entry.name.isEmpty()) {
          zipInputStream.closeEntry()
          continue
        }
        val destinationFile = outDir.resolve(entry.name).normalize()
        if (!destinationFile.startsWith(outDir)) {
          throw SecurityException("Zip entry path traverses outside target directory: ${entry.name}")
        }
        Files.createDirectories(destinationFile.parent)
        Files.newOutputStream(destinationFile).buffered().use { output -> zipInputStream.copyTo(output) }
        zipInputStream.closeEntry()
      }
    }

    /**
     * Reconstructs the expected Android SDK platform directory structure for LayoutLib.
     *
     * LayoutLib expects the platform resources to be located at `data/framework_res.jar` relative to its data directory. Since Maven
     * packages the native runtime (ZIP) and resources (JAR) separately, this transform copies the resolved resources JAR into the extracted
     * runtime directory to satisfy LayoutLib's initialization.
     */
    val frameworkResJar = artifactDependencies.files.find { it.name.startsWith("layoutlib-resources") && it.name.endsWith(".jar") }
    requireNotNull(frameworkResJar) {
      "Failed to resolve LayoutLib resources. " +
        "Could not find 'layoutlib-resources' JAR in the dependencies of 'layoutlib-runtime'. " +
        "Please check your dependency configuration."
    }
    val resJar = outDir.resolve("data").resolve("framework_res.jar").toFile()
    Files.createDirectories(resJar.parentFile.toPath())
    FileUtils.copyFile(frameworkResJar, resJar)
  }
}
