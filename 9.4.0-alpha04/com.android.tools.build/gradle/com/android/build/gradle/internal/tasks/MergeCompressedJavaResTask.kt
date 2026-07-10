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
import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.api.artifact.impl.InternalScopedArtifacts.InternalScope
import com.android.build.api.variant.Packaging
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScope
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.immutableListBuilder
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.merge.DelegateFileMergerOutput
import com.android.builder.merge.FileMerger
import com.android.builder.merge.FileMergerInput
import com.android.builder.merge.FileMergerInputNonIncremental
import com.android.builder.merge.FileMergerOutputs
import com.android.builder.merge.FilterFileMergerInput
import com.android.builder.merge.JavaResZipSourceMerger
import com.android.builder.merge.LazyFileMergerInput
import com.android.builder.merge.MergeOutputWriters
import com.android.builder.packaging.PackagingUtils
import com.android.builder.packaging.ParsedPackagingOptions
import com.google.common.collect.ImmutableList
import kotlin.sequences.map
import kotlin.sequences.sortedBy
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
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

  @get:InputFile @get:PathSensitive(PathSensitivity.NAME_ONLY) @get:Optional abstract val projectJavaResJar: RegularFileProperty

  @get:InputFiles @get:Classpath abstract val mergedDependenciesJavaRes: ConfigurableFileCollection

  @get:InputFiles @get:Optional @get:Classpath abstract val featureJavaRes: ConfigurableFileCollection

  @get:Input abstract val excludes: SetProperty<String>

  @get:Input abstract val pickFirsts: SetProperty<String>

  @get:Input abstract val merges: SetProperty<String>

  @get:Input @get:Optional abstract val noCompress: ListProperty<String>

  @get:OutputFile abstract val outputFile: RegularFileProperty

  override fun doTaskAction() {
    workerExecutor.noIsolation().submit(MergeJavaResOptimizedWorkAction::class.java) {
      it.initializeFromBaseTask(this)
      it.projectJavaResJar.set(projectJavaResJar)
      it.mergedDependenciesJavaRes.from(mergedDependenciesJavaRes)
      it.featureJavaRes.from(featureJavaRes)
      it.outputFile.set(outputFile)
      it.noCompress.set(noCompress)
      it.excludes.set(excludes)
      it.pickFirsts.set(pickFirsts)
      it.merges.set(merges)
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

      task.projectJavaResJar.setDisallowChanges(creationConfig.artifacts.get(InternalArtifactType.JAVA_RES_COMPRESSED_JAR))

      if (mergeScopes.contains(InternalScopedArtifacts.InternalScope.SUB_PROJECTS)) {
        task.mergedDependenciesJavaRes.from(
          creationConfig.variantDependencies
            .getArtifactCollection(
              AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
              AndroidArtifacts.ArtifactScope.PROJECT,
              AndroidArtifacts.ArtifactType.JAVA_RES,
            )
            .artifactFiles
        )
      }

      if (mergeScopes.contains(InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS)) {
        task.mergedDependenciesJavaRes.from(
          creationConfig.variantDependencies
            .getArtifactCollection(
              AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
              AndroidArtifacts.ArtifactScope.EXTERNAL,
              AndroidArtifacts.ArtifactType.JAVA_RES,
            )
            .artifactFiles
        )
      }

      if (mergeScopes.contains(InternalScopedArtifacts.InternalScope.FEATURES)) {
        task.featureJavaRes.from(
          creationConfig.variantDependencies
            .getArtifactCollection(
              AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
              AndroidArtifacts.ArtifactScope.PROJECT,
              AndroidArtifacts.ArtifactType.REVERSE_METADATA_JAVA_RES,
            )
            .artifactFiles
        )
      }

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

      task.variantName = ""
      task.projectJavaResJar.disallowChanges()

      task.mergedDependenciesJavaRes.from(
        creationConfig.dependencies
          .getArtifactCollection(AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH, AndroidArtifacts.ArtifactType.JAVA_RES)
          .artifactFiles
      )

      task.featureJavaRes.disallowChanges()

      task.excludes.setDisallowChanges(creationConfig.packaging.resources.excludes)
      task.pickFirsts.setDisallowChanges(creationConfig.packaging.resources.pickFirsts)
      task.merges.setDisallowChanges(creationConfig.packaging.resources.merges)
    }
  }
}

/** [ProfileAwareWorkAction] to merge java resources when optimized. */
abstract class MergeJavaResOptimizedWorkAction : ProfileAwareWorkAction<MergeJavaResOptimizedWorkAction.Params>() {

  override fun run() {
    if (!parameters.featureJavaRes.isEmpty) {
      error(
        "Dynamic features contain Java Resources. " +
          "This is not yet supported when android.experimental.enableJavaResourceOptimizations=true"
      )
    }

    val outputFile = parameters.outputFile.get().asFile

    val sources = immutableListBuilder {
      if (parameters.projectJavaResJar.isPresent) {
        add(CompressedJavaResJar(parameters.projectJavaResJar.get().asFile, JavaResMergingPriority.HIGH))
      }
      parameters.mergedDependenciesJavaRes.forEach { add(CompressedJavaResJar(it, JavaResMergingPriority.LOW)) }
    }

    val packagingOptions = ParsedPackagingOptions(parameters.excludes.get(), parameters.pickFirsts.get(), parameters.merges.get())
    val inputFilter =
      MergeJavaResourceTask.predicate.and { path ->
        packagingOptions.getAction(path) != ParsedPackagingOptions.JavaResPackagingFileAction.EXCLUDE
      }

    val highPriorityInputs = mutableListOf<FileMergerInput>()

    // create final input list, sorted and filtered.
    val finalInputList =
      sources
        .asSequence()
        .sortedBy(CompressedJavaResJar::priority)
        .map { jar ->
          val input = LazyFileMergerInput(jar.file.name, jar.file)
          val filteredInput = FilterFileMergerInput(input, inputFilter)

          if (jar.priority != JavaResMergingPriority.LOW) {
            highPriorityInputs.add(filteredInput)
          }

          filteredInput
        }
        .toList()

    val merger = JavaResZipSourceMerger(packagingOptions)
    val baseOutput = FileMergerOutputs.fromAlgorithmAndWriter(merger, MergeOutputWriters.toZipWithZipFlinger(outputFile))

    val output =
      object : DelegateFileMergerOutput(baseOutput) {
        override fun create(path: String, inputs: List<FileMergerInputNonIncremental>, compress: Boolean) {
          super.create(path, filter(path, inputs), compress)
        }

        private fun filter(path: String, inputs: List<FileMergerInputNonIncremental>): ImmutableList<FileMergerInputNonIncremental> {
          val packagingAction = packagingOptions.getAction(path)
          val shouldFilterInputs =
            packagingAction == ParsedPackagingOptions.JavaResPackagingFileAction.NONE && inputs.any { highPriorityInputs.contains(it) }
          return if (shouldFilterInputs) {
            val filteredInputs = ImmutableList.copyOf(inputs.filter { highPriorityInputs.contains(it) })
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
            filteredInputs
          } else {
            ImmutableList.copyOf(inputs)
          }
        }
      }

    FileMerger.merge(finalInputList, output, PackagingUtils.getNoCompressPredicateForJavaRes(parameters.noCompress.get()))
  }

  abstract class Params : Parameters() {
    abstract val projectJavaResJar: RegularFileProperty
    abstract val mergedDependenciesJavaRes: ConfigurableFileCollection
    abstract val featureJavaRes: ConfigurableFileCollection
    abstract val outputFile: RegularFileProperty
    abstract val noCompress: ListProperty<String>
    abstract val excludes: SetProperty<String>
    abstract val pickFirsts: SetProperty<String>
    abstract val merges: SetProperty<String>
  }
}
