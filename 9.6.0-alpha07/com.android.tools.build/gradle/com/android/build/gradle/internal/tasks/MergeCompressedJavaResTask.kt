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
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.api.variant.Packaging
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScope
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.MergeJavaResWorkAction.SourcedInput
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.immutableListBuilder
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.merge.DelegateFileMergerOutput
import com.android.builder.merge.FileMerger
import com.android.builder.merge.FileMergerInput
import com.android.builder.merge.FileMergerOutputs
import com.android.builder.merge.JavaResZipSourceMerger
import com.android.builder.merge.LazyFileMergerInput
import com.android.builder.merge.MergeOutputWriters
import com.android.builder.packaging.PackagingUtils
import com.android.builder.packaging.ParsedPackagingOptions
import com.google.common.collect.ImmutableList
import java.io.File
import kotlin.sequences.map
import kotlin.sequences.sortedBy
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * Task to merge java resources when android.experimental.enableJavaResourceOptimizations=true.
 *
 * This task consumes compressed java resources (jars) from the project and its dependencies.
 *
 * The task will replace [MergeJavaResourceTask] once benchmarked performance is surpassed.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.JAVA_RESOURCES, secondaryTaskCategories = [TaskCategory.MERGING])
abstract class MergeCompressedJavaResTask : NonIncrementalTask(), GlobalTask {

  @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY) @get:Optional abstract val projectJavaResJar: RegularFileProperty

  @get:Classpath abstract val mergedDependenciesJavaRes: ConfigurableFileCollection

  @get:Classpath @get:Optional abstract val featureJavaRes: ConfigurableFileCollection

  @get:Classpath @get:Optional abstract val localDepsJavaRes: ConfigurableFileCollection

  /* External library, subproject and feature dependencies are precompressed and locally merged in [JavaResCompressionTransform] and added
   * as a task input as mergedDependenciesJavaRes. For ambiguous Java resource conflicts between packages, the task will use the
   * artifact sources to determine the project or module to report in conflict error messages.
   */
  @get:Internal abstract val externalLibJavaRes: Property<ArtifactCollection>
  @get:Internal abstract val subProjectJavaRes: Property<ArtifactCollection>
  @get:Internal abstract val featureJavaResProvenance: Property<ArtifactCollection>

  @get:Input abstract val excludes: SetProperty<String>

  @get:Input abstract val pickFirsts: SetProperty<String>

  @get:Input abstract val merges: SetProperty<String>

  @get:Input @get:Optional abstract val noCompress: ListProperty<String>

  @get:Input abstract val hasIncludedBuilds: Property<Boolean>

  @get:OutputFile abstract val outputFile: RegularFileProperty

  override fun doTaskAction() {
    val displayBuildInfo = hasIncludedBuilds.get()
    val projectJavaResJar = projectJavaResJar.orNull
    val subProjectList = subProjectJavaRes.sourceFileToModuleId(displayBuildInfo)
    val externalLibList = externalLibJavaRes.sourceFileToModuleId(displayBuildInfo) + localDepsJavaRes.toSourcedInputs()
    val featureList = featureJavaResProvenance.sourceFileToModuleId(displayBuildInfo)

    val projectJavaResJarFile = if (projectJavaResJar?.asFile?.exists() == true) projectJavaResJar.asFile else null

    MergeJavaResCompressedJarsMerger(
        projectPath = projectPath.get(),
        projectJavaResJar = projectJavaResJarFile,
        subProjectJavaRes = subProjectList,
        externalLibJavaRes = externalLibList,
        featureJavaRes = featureList,
        outputFile = outputFile.get().asFile,
        noCompress = noCompress.getOrElse(emptyList()),
        excludes = excludes.get(),
        pickFirsts = pickFirsts.get(),
        merges = merges.get(),
      )
      .run()
  }

  private fun Property<ArtifactCollection>.sourceFileToModuleId(displayBuildInfo: Boolean): List<SourcedInput> {
    if (!isPresent) return emptyList()
    return get().artifacts.map {
      val name =
        when (val id = it.id.componentIdentifier) {
          is ModuleComponentIdentifier -> "${id.group}:${id.module}:${id.version}/${it.file.name}"
          is ProjectComponentIdentifier -> {
            if (displayBuildInfo) {
              "project(\"${id.projectPath}\") - Build: ${id.build.buildPath}"
            } else {
              "project(\"${id.projectPath}\")"
            }
          }
          else -> id.displayName + "/${it.file.name}"
        }
      SourcedInput(it.file, name)
    }
  }

  class CreationAction(
    private val mergeScopes: Set<InternalScopedArtifacts.InternalScope>,
    private val packaging: Packaging,
    creationConfig: ComponentCreationConfig,
  ) : VariantTaskCreationAction<MergeCompressedJavaResTask, ComponentCreationConfig>(creationConfig) {

    override val name: String
      get() = computeTaskName("merge", "JavaResource")

    override val type: Class<MergeCompressedJavaResTask>
      get() = MergeCompressedJavaResTask::class.java

    override fun handleProvider(taskProvider: TaskProvider<MergeCompressedJavaResTask>) {
      super.handleProvider(taskProvider)
      val fileName =
        if (creationConfig.componentType.isBaseModule) {
          "base.jar"
        } else {
          TaskManager.getFeatureFileName(creationConfig.services.projectInfo.path, SdkConstants.DOT_JAR)
        }
      creationConfig.artifacts
        .setInitialProvider(taskProvider, MergeCompressedJavaResTask::outputFile)
        .withName(fileName)
        .on(InternalArtifactType.ORIGINAL_MERGED_JAVA_RES)

      creationConfig.artifacts
        .setInitialProvider(taskProvider, MergeCompressedJavaResTask::outputFile)
        .withName(fileName)
        .on(InternalArtifactType.MERGED_JAVA_RES)
    }

    override fun configure(task: MergeCompressedJavaResTask) {
      super.configure(task)

      configureHasIncludedBuilds(task.project.gradle, task)

      task.projectJavaResJar.setDisallowChanges(creationConfig.artifacts.get(InternalArtifactType.JAVA_RES_COMPRESSED_JAR))

      if (mergeScopes.contains(InternalScopedArtifacts.InternalScope.SUB_PROJECTS)) {
        val artifacts =
          creationConfig.variantDependencies.getArtifactCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.PROJECT,
            AndroidArtifacts.ArtifactType.JAVA_RES,
          )
        task.mergedDependenciesJavaRes.from(artifacts.artifactFiles)
        task.subProjectJavaRes.set(artifacts)
      }
      task.subProjectJavaRes.disallowChanges()

      if (mergeScopes.contains(InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS)) {
        val artifacts =
          creationConfig.variantDependencies.getArtifactCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.EXTERNAL,
            AndroidArtifacts.ArtifactType.JAVA_RES,
          )
        task.mergedDependenciesJavaRes.from(artifacts.artifactFiles)
        task.externalLibJavaRes.set(artifacts)
      }
      task.externalLibJavaRes.disallowChanges()

      if (mergeScopes.contains(InternalScopedArtifacts.InternalScope.LOCAL_DEPS)) {
        val artifacts =
          creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.LOCAL_DEPS).getFinalArtifacts(ScopedArtifact.JAVA_RES)
        task.mergedDependenciesJavaRes.from(artifacts)
        task.localDepsJavaRes.from(artifacts)
      }
      task.localDepsJavaRes.disallowChanges()

      if (mergeScopes.contains(InternalScopedArtifacts.InternalScope.FEATURES)) {
        val artifacts =
          creationConfig.variantDependencies.getArtifactCollection(
            AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
            AndroidArtifacts.ArtifactScope.PROJECT,
            AndroidArtifacts.ArtifactType.REVERSE_METADATA_JAVA_RES,
          )
        task.featureJavaRes.from(artifacts.artifactFiles)
        task.featureJavaResProvenance.set(artifacts)
      }
      task.featureJavaResProvenance.disallowChanges()

      task.excludes.setDisallowChanges(packaging.resources.excludes)
      task.pickFirsts.setDisallowChanges(packaging.resources.pickFirsts)
      task.merges.setDisallowChanges(packaging.resources.merges)
      if (creationConfig is ApkCreationConfig) {
        task.noCompress.set(creationConfig.androidResources.noCompress)
      }
    }
  }

  class FusedLibraryCreationAction(private val creationConfig: FusedLibraryGlobalScope) :
    GlobalTaskCreationAction<MergeCompressedJavaResTask>() {

    override val name: String
      get() = "mergeLibraryJavaResources"

    override val type: Class<MergeCompressedJavaResTask>
      get() = MergeCompressedJavaResTask::class.java

    override fun handleProvider(taskProvider: TaskProvider<MergeCompressedJavaResTask>) {
      super.handleProvider(taskProvider)
      creationConfig.artifacts
        .setInitialProvider(taskProvider, MergeCompressedJavaResTask::outputFile)
        .withName("base.jar")
        .on(FusedLibraryInternalArtifactType.MERGED_JAVA_RES)
    }

    override fun configure(task: MergeCompressedJavaResTask) {
      super.configure(task)

      configureHasIncludedBuilds(task.project.gradle, task)

      task.variantName = ""
      task.projectJavaResJar.disallowChanges()

      val artifacts =
        creationConfig.dependencies.getArtifactCollection(
          AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
          AndroidArtifacts.ArtifactType.JAVA_RES,
        )
      task.mergedDependenciesJavaRes.from(artifacts.artifactFiles)
      task.subProjectJavaRes.set(artifacts)

      task.externalLibJavaRes.disallowChanges()
      task.localDepsJavaRes.disallowChanges()
      task.featureJavaRes.disallowChanges()
      task.featureJavaResProvenance.disallowChanges()

      task.excludes.setDisallowChanges(creationConfig.packaging.resources.excludes)
      task.pickFirsts.setDisallowChanges(creationConfig.packaging.resources.pickFirsts)
      task.merges.setDisallowChanges(creationConfig.packaging.resources.merges)
    }
  }
}

/** Runs merging with [SourcedInput] */
internal class MergeJavaResCompressedJarsMerger(
  private val projectPath: String,
  private val projectJavaResJar: File?,
  private val subProjectJavaRes: List<SourcedInput>,
  private val externalLibJavaRes: List<SourcedInput>,
  private val featureJavaRes: List<SourcedInput>,
  private val outputFile: File,
  private val noCompress: List<String>,
  private val excludes: Set<String>,
  private val pickFirsts: Set<String>,
  private val merges: Set<String>,
) {

  fun run() {
    val sources = immutableListBuilder {
      if (projectJavaResJar != null) {
        add(
          CompressedJavaResJar(
            "project(\"${projectPath}\") - This project",
            projectJavaResJar,
            JavaResMergingPriority.HIGH,
          )
        )
      }
      subProjectJavaRes.forEach {
        add(CompressedJavaResJar(it.source, it.input, determinePriority(InternalScopedArtifacts.InternalScope.SUB_PROJECTS)))
      }
      externalLibJavaRes.forEach {
        add(CompressedJavaResJar(it.source, it.input, determinePriority(InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS)))
      }
      featureJavaRes.forEach {
        add(CompressedJavaResJar(it.source, it.input, determinePriority(InternalScopedArtifacts.InternalScope.FEATURES)))
      }
    }

    val packagingOptions = ParsedPackagingOptions(excludes, pickFirsts, merges)
    val inputFilter =
      MergeJavaResourceTask.predicate.and { path ->
        packagingOptions.getAction(path) != ParsedPackagingOptions.JavaResPackagingFileAction.EXCLUDE
      }

    val highPriorityInputs = mutableSetOf<FileMergerInput>()

    // create final input list, sorted and filtered.
    val finalInputList =
      sources
        .asSequence()
        .sortedBy(CompressedJavaResJar::priority)
        .map { jar ->
          val input = LazyFileMergerInput(jar.name, jar.file, inputFilter)

          if (jar.priority != JavaResMergingPriority.LOW) {
            highPriorityInputs.add(input)
          }

          input
        }
        .toList()

    val merger = JavaResZipSourceMerger(packagingOptions)
    val baseOutput = FileMergerOutputs.fromAlgorithmAndWriter(merger, MergeOutputWriters.toZipWithZipFlinger(outputFile))

    val output =
      object : DelegateFileMergerOutput(baseOutput) {
        override fun create(path: String, inputs: List<FileMergerInput>, compress: Boolean) {
          super.create(path, filter(path, inputs), compress)
        }

        private fun filter(path: String, inputs: List<FileMergerInput>): ImmutableList<FileMergerInput> {
          val packagingAction = packagingOptions.getAction(path)
          val shouldFilterInputs =
            packagingAction == ParsedPackagingOptions.JavaResPackagingFileAction.NONE && inputs.any { it in highPriorityInputs }
          return if (shouldFilterInputs) {
            val filteredInputs = inputs.filter { it in highPriorityInputs }
            if (filteredInputs.size < inputs.size) {
              val logger = LoggerWrapper(Logging.getLogger(MergeJavaResourcesDelegate::class.java))
              logger.warning(
                "More than one file was found with OS independent path '$path'. " +
                  "This version of the Android Gradle Plugin chooses the file " +
                  "from the app or dynamic-feature module, but this can cause " +
                  "unexpected behavior or errors at runtime. Future versions " +
                  "of the Android Gradle Plugin will throw an error in this " +
                  "case."
              )
            }
            filteredInputs.toImmutableList()
          } else {
            inputs.toImmutableList()
          }
        }
      }

    FileMerger.merge(finalInputList, output, PackagingUtils.getNoCompressPredicateForJavaRes(noCompress))
  }
}

private fun ConfigurableFileCollection.toSourcedInputs(): List<SourcedInput> = map { SourcedInput(it, it.absolutePath) }

private fun configureHasIncludedBuilds(gradle: Gradle, task: MergeCompressedJavaResTask) {
  val rootGradle = gradle.parent ?: gradle
  task.hasIncludedBuilds.setDisallowChanges(rootGradle.includedBuilds.isNotEmpty())
}
