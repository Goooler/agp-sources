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
import java.nio.file.Files
import java.util.zip.ZipInputStream
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
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
  }
}
