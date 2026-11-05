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
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.utils.setDisallowChanges
import java.nio.file.Files
import java.util.zip.ZipInputStream
import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault

/** A transform that extracts a Layoutlib runtime artifact into a directory, including framework resources. */
@DisableCachingByDefault(because = DisabledCachingReason.COPY_TASK)
abstract class LayoutlibExtractor : TransformAction<LayoutlibExtractor.Parameters> {

  abstract class Parameters : GenericTransformParameters {

    /** The `layoutlib-resources` artifact that goes with the layoutlib runtime distribution being extracted. */
    @get:Classpath abstract val frameworkResources: ConfigurableFileCollection
  }

  @get:PathSensitive(PathSensitivity.NAME_ONLY) @get:InputArtifact abstract val layoutlibDistributionArtifact: Provider<FileSystemLocation>

  override fun transform(transformOutputs: TransformOutputs) {
    val input = layoutlibDistributionArtifact.get().asFile

    val frameworkResources = parameters.frameworkResources.files
    if (frameworkResources.size != 1) {
      throw RuntimeException(
        "Expected exactly one $LAYOUTLIB_GROUP:$LAYOUTLIB_RESOURCES_MODULE artifact to extract ${input.name} with, but found " +
          "${frameworkResources.map { it.absolutePath }}. The rendering engine cannot run without the framework resources, make sure " +
          "that the test engine dependencies of the test suite bring in $LAYOUTLIB_GROUP:$LAYOUTLIB_RESOURCES_MODULE."
      )
    }

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

    val frameworkResJar = outDir.resolve(DATA_FOLDER).resolve(FRAMEWORK_RES_JAR)
    Files.createDirectories(frameworkResJar.parent)
    frameworkResources.single().copyTo(frameworkResJar.toFile(), overwrite = true)
  }

  companion object {

    /** Maven coordinates of the layoutlib artifacts consumed by the screenshot rendering engine. */
    const val LAYOUTLIB_GROUP = "com.android.tools.layoutlib"

    const val LAYOUTLIB_RUNTIME_MODULE = "layoutlib-runtime"

    const val LAYOUTLIB_RESOURCES_MODULE = "layoutlib-resources"

    private const val DATA_FOLDER = "data"

    private const val FRAMEWORK_RES_JAR = "framework_res.jar"

    /** Attribute identifying which consumer a [LayoutlibExtractor] registration belongs to. */
    @JvmField
    val LAYOUTLIB_CONSUMER_ATTRIBUTE: Attribute<String> =
      Attribute.of("com.android.build.api.attributes.LayoutlibConsumerAttr", String::class.java)

    /** Registers a [LayoutlibExtractor] transform for [consumerId] with [frameworkResources]. */
    fun registerTransform(project: Project, consumerId: String, frameworkResources: FileCollection) {
      project.dependencies.registerTransform(LayoutlibExtractor::class.java) { spec ->
        spec.from.attribute(AndroidArtifacts.ARTIFACT_TYPE, ArtifactTypeDefinition.JAR_TYPE)
        spec.to.attribute(AndroidArtifacts.ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.EXTRACTED_LAYOUTLIB.type)
        spec.from.attribute(LAYOUTLIB_CONSUMER_ATTRIBUTE, consumerId)
        spec.to.attribute(LAYOUTLIB_CONSUMER_ATTRIBUTE, consumerId)
        spec.parameters.projectName.setDisallowChanges(project.name)
        spec.parameters.frameworkResources.setFrom(frameworkResources)
        spec.parameters.frameworkResources.disallowChanges()
      }
    }
  }
}
