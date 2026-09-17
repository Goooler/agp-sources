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

package com.android.build.gradle.tasks

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.core.ComponentTypeImpl
import java.io.File
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Task that collects compiled bytecode, resource APK, R JAR, and runtime classpath into a structured runfiles JSON manifest for Compose
 * preview rendering.
 */
@DisableCachingByDefault(because = "Minimal file generation task; cache calculation overhead exceeds execution time")
abstract class GenerateComposePreviewRunfilesTask : NonIncrementalTask() {

  @get:Input abstract val packageName: Property<String>
  @get:InputFiles @get:Classpath abstract val projectClasses: ConfigurableFileCollection
  @get:InputFiles @get:Classpath abstract val runtimeClasspath: ConfigurableFileCollection
  @get:InputFile @get:PathSensitive(PathSensitivity.NAME_ONLY) abstract val resourceApk: RegularFileProperty
  @get:InputFile @get:Classpath abstract val rJar: RegularFileProperty
  @get:OutputFile abstract val composePreviewManifestFile: RegularFileProperty

  @Option(
    option = "compose-preview-manifest-file",
    description = "Explicit destination file path for generated Compose preview runfiles manifest JSON",
  )
  fun setComposePreviewManifestFileOption(path: String) {
    composePreviewManifestFile.set(File(path))
  }

  override fun doTaskAction() {
    val variantSuffix =
      try {
        " (variant '$variantName')"
      } catch (e: Exception) {
        ""
      }
    val projPrefix = projectPath.orNull?.let { " for '$it'" } ?: ""
    val outputFile = composePreviewManifestFile.get().asFile

    val resApk = resourceApk.get().asFile
    if (!resApk.exists()) {
      error(
        "Compose Preview failed for '${projectPath.get()}' (variant '$variantName'): Resource APK does not exist at '${resApk.absolutePath}'."
      )
    }

    val rJarFile = rJar.get().asFile
    if (!rJarFile.exists()) {
      error(
        "Compose Preview failed for '${projectPath.get()}' (variant '$variantName'): R.jar file does not exist at '${rJarFile.absolutePath}'."
      )
    }

    val projectPaths = buildList {
      add(rJarFile.absolutePath)
      projectClasses.files.forEach { add(it.absolutePath) }
    }

    val data =
      ComposePreviewRunfilesData(
        packageName = packageName.get(),
        resourceApk = resApk.absolutePath,
        projectClasspath = projectPaths,
        classpath = runtimeClasspath.files.map { it.absolutePath },
      )

    outputFile.parentFile?.mkdirs()
    outputFile.writeText(data.toJson())
  }

  class CreationAction(creationConfig: ComponentCreationConfig) :
    VariantTaskCreationAction<GenerateComposePreviewRunfilesTask, ComponentCreationConfig>(creationConfig) {

    override val name: String
      get() = computeTaskName("generate", "ComposePreviewRunfiles")

    override val type: Class<GenerateComposePreviewRunfilesTask>
      get() = GenerateComposePreviewRunfilesTask::class.java

    override fun configure(task: GenerateComposePreviewRunfilesTask) {
      super.configure(task)
      task.composePreviewManifestFile.convention(
        creationConfig.services.provider {
          task.logger.error(
            "Compose Preview failed for '${creationConfig.services.projectInfo.path}' (variant '${creationConfig.name}'): " +
              "Missing required option --compose-preview-manifest-file=<path>."
          )
          null
        }
      )
      val artifacts = creationConfig.artifacts

      task.packageName.setDisallowChanges(creationConfig.namespace)

      task.projectClasses.from(artifacts.forScope(ScopedArtifacts.Scope.PROJECT).getFinalArtifacts(ScopedArtifact.CLASSES))
      task.projectClasses.disallowChanges()

      val unitTestComponent =
        (creationConfig as? VariantCreationConfig)?.nestedComponents?.filterIsInstance<HostTestCreationConfig>()?.firstOrNull {
          it.componentType == ComponentTypeImpl.UNIT_TEST
        }

      val testComponent =
        unitTestComponent
          ?: error(
            "Compose Preview failed for '${creationConfig.services.projectInfo.path}' (variant '${creationConfig.name}'): " +
              "Unit tests are disabled for this variant (enableUnitTest = false in beforeVariants). " +
              "Compose Preview requires unit tests to package Android XML resources and themes into the resource APK for Layoutlib."
          )

      if (!testComponent.androidResourcesIncluded) {
        error(
          "Compose Preview failed for '${creationConfig.services.projectInfo.path}' (variant '${creationConfig.name}'): " +
            "Android resources are excluded from unit tests ('testOptions.unitTests.isIncludeAndroidResources = false' in finalizeDsl). " +
            "Compose Preview requires compiled Android resources to resolve layout XML and theme attributes."
        )
      }

      task.resourceApk.setDisallowChanges(testComponent.artifacts.get(InternalArtifactType.APK_FOR_LOCAL_TEST))

      val rJarProvider =
        when {
          creationConfig.componentType.isAar -> testComponent.artifacts.get(InternalArtifactType.COMPILE_AND_RUNTIME_R_CLASS_JAR)
          creationConfig.componentType.isApk -> artifacts.get(InternalArtifactType.COMPILE_AND_RUNTIME_R_CLASS_JAR)
          else ->
            error(
              "Compose Preview failed for '${creationConfig.services.projectInfo.path}' (variant '${creationConfig.name}'): " +
                "Unsupported component type '${creationConfig.componentType}'."
            )
        }
      task.rJar.setDisallowChanges(rJarProvider)

      val runtimeClasspath = creationConfig.variantDependencies.runtimeClasspath
      val classesJars =
        runtimeClasspath.incoming
          .artifactView { view ->
            view.attributes { it.attribute(AndroidArtifacts.ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.CLASSES_JAR.type) }
            view.lenient(true)
          }
          .files
      val standardJars =
        runtimeClasspath.incoming
          .artifactView { view ->
            view.attributes { it.attribute(AndroidArtifacts.ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.JAR.type) }
            view.lenient(true)
          }
          .files
      val processedJars =
        runtimeClasspath.incoming
          .artifactView { view ->
            view.attributes { it.attribute(AndroidArtifacts.ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.PROCESSED_JAR.type) }
            view.lenient(true)
          }
          .files

      task.runtimeClasspath.from(classesJars, standardJars, processedJars)
      task.runtimeClasspath.disallowChanges()
    }
  }
}
