/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.res

import com.android.SdkConstants
import com.android.SdkConstants.FN_R_CLASS_JAR
import com.android.SdkConstants.RES_QUALIFIER_SEP
import com.android.aaptcompiler.LocaleValue
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.MultiOutputHandler
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.dirName
import com.android.build.api.variant.impl.getFilter
import com.android.build.gradle.internal.AndroidJarInput
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.SymbolTableBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.services.getErrorFormatMode
import com.android.build.gradle.internal.services.getLeasingAapt2
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.AndroidResourcesTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.AndroidResourcesTaskCreationActionImpl
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.internal.tasks.runResourceShrinking
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.ComponentType
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.v2.Aapt2
import com.android.ide.common.process.ProcessException
import com.android.ide.common.resources.mergeIdentifiedSourceSetFiles
import com.android.ide.common.symbols.SymbolIo
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.tooling.BuildException
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.inject.Inject

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES, secondaryTaskCategories = [TaskCategory.LINKING])
abstract class LinkApplicationAndroidResourcesTask: ProcessAndroidResources() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    abstract val manifestFiles: DirectoryProperty

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    abstract val aaptFriendlyManifestFiles: DirectoryProperty

    // This input in not required for the task to function properly.
    // However, the implementation of getManifestFile() requires it to stay compatible with past
    // plugin and crashlitics related plugins are using it.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    @get:Deprecated("Deprecated and will be removed")
    @get:Incremental
    abstract val mergedManifestFiles: DirectoryProperty

    @get:OutputDirectory
    @get:Optional
    abstract val sourceOutputDirProperty: DirectoryProperty

    @get:OutputFile
    @get:Optional
    abstract val textSymbolOutputFileProperty: RegularFileProperty

    @get:OutputFile
    @get:Optional
    abstract val symbolsWithPackageNameOutputFile: RegularFileProperty

    @get:OutputFile
    @get:Optional
    abstract val proguardOutputFile: RegularFileProperty

    @get:Optional
    @get:OutputFile
    abstract val rClassOutputJar: RegularFileProperty

    @get:OutputFile
    @get:Optional
    abstract val mainDexListProguardOutputFile: RegularFileProperty

    @get:OutputFile
    @get:Optional
    abstract val stableIdsOutputFileProperty: RegularFileProperty

    @get:InputFiles
    @get:Optional
    @get:Incremental
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val dependenciesFileCollection: ConfigurableFileCollection

    @get:InputFile
    @get:Optional
    @get:Incremental
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val localResourcesFile: RegularFileProperty

    @get:InputFiles
    @get:Optional
    @get:Incremental
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sharedLibraryDependencies: ConfigurableFileCollection

    @get:Optional
    @get:Input
    abstract val resOffset: Property<Int>

    @get:Internal
    abstract val componentType: Property<ComponentType>

    @get:Input
    abstract val resourceConfigs: SetProperty<String>

    @get:Input
    @get:Optional
    abstract val noCompress: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val aaptAdditionalParameters: ListProperty<String>

    // Not an input as it is only used to rewrite exceptions and doesn't affect task output
    @get:Internal
    abstract val mergeBlameLogFolder: DirectoryProperty

    // No effect on task output, used for generating absolute paths for error messaging.
    @get:Internal
    abstract val sourceSetMaps: ConfigurableFileCollection

    @get:Classpath
    @get:Optional
    abstract val featureResourcePackages: ConfigurableFileCollection

    @get:Input
    abstract val namespace: Property<String>

    @get:Input
    abstract val useConditionalKeepRules: Property<Boolean>

    @get:Input
    abstract val useMinimalKeepRules: Property<Boolean>

    @get:OutputDirectory
    abstract val linkedResourcesOutputDir: DirectoryProperty

    @get:Input
    abstract val linkedResourcesArtifactType: Property<InternalArtifactType<Directory>>

    @get:Input
    abstract val projectBaseName: Property<String>

    @get:Input
    abstract val namespaced: Property<Boolean>

    @get:Input
    abstract val applicationId: Property<String>

    @get:Classpath
    @get:Optional
    @get:Incremental
    abstract val inputResourcesDir: DirectoryProperty

    @get:Input
    abstract val library: Property<Boolean>

    @get:Nested
    abstract val androidJarInput: AndroidJarInput

    @get:Input
    abstract val useFinalIds: Property<Boolean>

    @get:Input
    abstract val useStableIds: Property<Boolean>

    @get:Input
    abstract val dynamicFeature: Property<Boolean>

    @get:Nested
    abstract val outputsHandler: Property<MultiOutputHandler>

    // aarMetadataCheck doesn't affect the task output, but it's marked as an input so that this
    // task depends on CheckAarMetadataTask.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val aarMetadataCheck: ConfigurableFileCollection

    @get:Nested
    abstract val aapt2: Aapt2Input

    @get:Internal
    abstract val symbolTableBuildService: Property<SymbolTableBuildService>

    // Not an input as it is only used to rewrite exception and doesn't affect task output
    @get:Internal
    abstract val manifestMergeBlameFile: RegularFileProperty

    @get:Classpath
    @get:Optional
    abstract val compiledDependenciesResources: ConfigurableFileCollection

    @get:Internal
    abstract val incrementalDirectory: DirectoryProperty

    @get:Input
    abstract val localeFilters: SetProperty<String>

    @get:Input
    abstract val pseudoLocalesEnabled: Property<Boolean>

    @Internal
    override fun getManifestFile(): File? {
        val manifestDirectory = if (aaptFriendlyManifestFiles.isPresent) {
            aaptFriendlyManifestFiles.get().asFile
        } else if (mergedManifestFiles.isPresent) {
            mergedManifestFiles.get().asFile
        } else {
            manifestFiles.get().asFile
        }
        Preconditions.checkNotNull(manifestDirectory)

        return if (mainSplit != null) {
            FileUtils.join(
                manifestDirectory,
                mainSplit.dirName(),
                SdkConstants.ANDROID_MANIFEST_XML
            )
        } else {
            FileUtils.join(
                manifestDirectory,
                SdkConstants.ANDROID_MANIFEST_XML
            )
        }

    }

    override fun doTaskAction(inputChanges: InputChanges) {
        val stableIdsFile = stableIdsOutputFileProperty.orNull?.asFile
        if (useStableIds.get() && inputChanges.isIncremental) {
            // For now, we don't care about what changed - we only want to preserve the res IDs from the
            // previous run if stable IDs support is enabled.
            doFullTaskAction(stableIdsFile)
        } else {
            stableIdsFile?.toPath()?.let { Files.deleteIfExists(it) }
            doFullTaskAction(null)
        }
    }

    private fun doFullTaskAction(inputStableIdsFile: File?) {
        workerExecutor.noIsolation().submit(TaskAction::class.java) { parameters ->
            parameters.initializeFromBaseTask(this)

            parameters.mainDexListProguardOutputFile.set(mainDexListProguardOutputFile)
            parameters.outputStableIdsFile.set(stableIdsOutputFileProperty)
            parameters.proguardOutputFile.set(proguardOutputFile)
            parameters.rClassOutputJar.set(rClassOutputJar)
            parameters.linkedResourcesOutputDir.set(linkedResourcesOutputDir)
            parameters.linkedResourcesArtifactType.set(linkedResourcesArtifactType)
            parameters.sourceOutputDirectory.set(sourceOutputDirProperty)
            parameters.symbolsWithPackageNameOutputFile.set(symbolsWithPackageNameOutputFile)
            parameters.textSymbolOutputFile.set(textSymbolOutputFileProperty)

            parameters.androidJarInput.set(androidJarInput)
            parameters.aapt2.set(aapt2)
            parameters.symbolTableBuildService.set(symbolTableBuildService)

            parameters.aaptOptions.set(AaptOptions(noCompress.orNull, aaptAdditionalParameters.orNull))
            parameters.applicationId.set(applicationId)
            parameters.compiledDependenciesResources.from(compiledDependenciesResources)
            parameters.dependencies.from(dependenciesFileCollection)
            parameters.featureResourcePackages.from(featureResourcePackages)
            parameters.imports.from(sharedLibraryDependencies)
            parameters.incrementalDirectory.set(incrementalDirectory)
            parameters.inputResourcesDirectory.set(inputResourcesDir)
            parameters.inputStableIdsFile.set(inputStableIdsFile)
            parameters.dynamicFeature.set(dynamicFeature)
            parameters.library.set(library)
            parameters.localResourcesFile.set(localResourcesFile)
            parameters.manifestFiles.set(if (aaptFriendlyManifestFiles.isPresent) aaptFriendlyManifestFiles else manifestFiles)
            parameters.manifestMergeBlameFile.set(manifestMergeBlameFile)
            parameters.mergeBlameDirectory.set(mergeBlameLogFolder)
            parameters.namespace.set(namespace)
            parameters.namespaced.set(namespaced)
            parameters.packageId.set(resOffset)
            parameters.resourceConfigs.set(resourceConfigs)
            parameters.sharedLibraryDependencies.from(sharedLibraryDependencies)
            parameters.sourceSetMaps.from(sourceSetMaps)
            parameters.useConditionalKeepRules.set(useConditionalKeepRules)
            parameters.useFinalIds.set(useFinalIds)
            parameters.useMinimalKeepRules.set(useMinimalKeepRules)
            parameters.useStableIds.set(useStableIds)
            parameters.variantName.set(variantName)
            parameters.outputsHandler.set(outputsHandler.get().toSerializable())
            parameters.componentType.set(componentType)
            parameters.localeFilters.set(localeFilters)
            parameters.pseudoLocalesEnabled.set(pseudoLocalesEnabled)
        }
    }

    abstract class TaskWorkActionParameters : ProfileAwareWorkAction.Parameters() {

        abstract val mainDexListProguardOutputFile: RegularFileProperty
        abstract val outputStableIdsFile: RegularFileProperty
        abstract val proguardOutputFile: RegularFileProperty
        abstract val rClassOutputJar: RegularFileProperty
        abstract val linkedResourcesOutputDir: DirectoryProperty
        abstract val linkedResourcesArtifactType: Property<InternalArtifactType<Directory>>
        abstract val sourceOutputDirectory: DirectoryProperty
        abstract val symbolsWithPackageNameOutputFile: RegularFileProperty
        abstract val textSymbolOutputFile: RegularFileProperty

        @get:Nested abstract val androidJarInput: Property<AndroidJarInput>
        @get:Nested abstract val aapt2: Property<Aapt2Input>
        abstract val symbolTableBuildService: Property<SymbolTableBuildService>
        abstract val aaptOptions: Property<AaptOptions>
        abstract val applicationId: Property<String>
        abstract val outputsHandler: Property<MultiOutputHandler>
        abstract val compiledDependenciesResources: ConfigurableFileCollection
        abstract val dependencies: ConfigurableFileCollection
        abstract val featureResourcePackages: ConfigurableFileCollection
        abstract val imports: ConfigurableFileCollection
        abstract val incrementalDirectory: DirectoryProperty
        abstract val inputResourcesDirectory: DirectoryProperty
        abstract val inputStableIdsFile: RegularFileProperty
        abstract val dynamicFeature: Property<Boolean>
        abstract val library: Property<Boolean>
        abstract val localResourcesFile: RegularFileProperty
        abstract val manifestFiles: DirectoryProperty
        abstract val manifestMergeBlameFile: RegularFileProperty
        abstract val mergeBlameDirectory: DirectoryProperty
        abstract val namespace: Property<String>
        abstract val namespaced: Property<Boolean>
        abstract val packageId: Property<Int>
        abstract val resourceConfigs: SetProperty<String>
        abstract val sharedLibraryDependencies: ConfigurableFileCollection
        abstract val sourceSetMaps: ConfigurableFileCollection
        abstract val useConditionalKeepRules: Property<Boolean>
        abstract val useFinalIds: Property<Boolean>
        abstract val useMinimalKeepRules: Property<Boolean>
        abstract val useStableIds: Property<Boolean>
        abstract val variantName: Property<String>
        abstract val componentType: Property<ComponentType>
        abstract val localeFilters: SetProperty<String>
        abstract val pseudoLocalesEnabled: Property<Boolean>
    }

    abstract class TaskAction : ProfileAwareWorkAction<TaskWorkActionParameters>() {

        @get:Inject
        abstract val workerExecutor: WorkerExecutor

        override fun run() {
            if (!parameters.dynamicFeature.get() && !parameters.featureResourcePackages.isEmpty) {
            throw IllegalStateException(
                    """
                    This application (${parameters.applicationId.get()}) is not configured to use dynamic features.
                    Please ensure dynamic features are configured in the build file.
                    Refer to https://developer.android.com/guide/playcore/feature-delivery#base_feature_relationship
                    """.trimIndent()
                )
        }

            val manifestBuiltArtifacts = BuiltArtifactsLoaderImpl().load(parameters.manifestFiles)
                ?: throw RuntimeException("Cannot load processed manifest files, please file a bug.")
            // 'Incremental' runs should only preserve the stable IDs file.
            FileUtils.deleteDirectoryContents(parameters.linkedResourcesOutputDir.get().asFile)

            val outputsHandler = parameters.outputsHandler.get()

            val mainOutput = outputsHandler.getOutputs {
                it.getFilter(FilterConfiguration.FilterType.DENSITY) == null
            }.firstOrNull() ?: throw RuntimeException("No non-density apk found")

            val manifestOutput = outputsHandler.extractArtifactForSplit(
                artifacts = manifestBuiltArtifacts,
                config = mainOutput.variantOutputConfiguration
            ) ?: throw RuntimeException("Cannot find built manifest for $mainOutput")

            invokeAaptForSplit(
                variantOutput = mainOutput,
                manifestOutput = manifestOutput,
                generateRClass = true,
                aapt2 = parameters.aapt2.get().getLeasingAapt2(),
                stableIdsInputFile = parameters.inputStableIdsFile.orNull?.asFile,
                parameters = parameters
            )

            // This must happen after the main split is done, since the output of the main
            // split is used by the full splits.
            outputsHandler.getOutputs { true }.minus(mainOutput).let { unprocessedOutputs ->
                val workQueue = workerExecutor.noIsolation()
                for (variantOutput in unprocessedOutputs) {

                    val splitManifestOutput =
                        outputsHandler.extractArtifactForSplit(
                            artifacts = manifestBuiltArtifacts,
                            config = variantOutput.variantOutputConfiguration
                        ) ?: throw RuntimeException("Cannot find build manifest for $variantOutput")

                    workQueue.submit(InvokeAaptForSplitAction::class.java) { splitParameters ->
                        splitParameters.initializeFromProfileAwareWorkAction(this.parameters)
                        splitParameters.globalParameters.set(parameters)
                        splitParameters.variantOutput.set(variantOutput)
                        splitParameters.manifestOutput.set(splitManifestOutput)
                    }
                }
            }
        }

    }

    abstract class InvokeAaptForSplitAction : ProfileAwareWorkAction<InvokeAaptForSplitAction.Parameters>() {
        abstract class Parameters: ProfileAwareWorkAction.Parameters() {
            abstract val globalParameters: Property<TaskWorkActionParameters>
            abstract val variantOutput: Property<VariantOutputImpl.SerializedForm>
            abstract val manifestOutput: Property<BuiltArtifactImpl>
        }

        override fun run() {
            // If we're supporting stable IDs we need to make sure the splits get exactly
            // the same IDs as the main one.
            val globalParameters = parameters.globalParameters.get()
            invokeAaptForSplit(
                parameters.variantOutput.get(),
                parameters.manifestOutput.get(),
                false,
                globalParameters.aapt2.get().getLeasingAapt2(),
                if (globalParameters.useStableIds.get()) globalParameters.outputStableIdsFile.get().asFile else null,
                globalParameters)
        }
    }

    abstract class BaseCreationAction(
        creationConfig: ComponentCreationConfig,
        private val generateLegacyMultidexMainDexProguardRules: Boolean,
        private val baseName: Provider<String>,
        private val isLibrary: Boolean
    ) : VariantTaskCreationAction<LinkApplicationAndroidResourcesTask, ComponentCreationConfig>(
        creationConfig
    ), AndroidResourcesTaskCreationAction by AndroidResourcesTaskCreationActionImpl(creationConfig) {

        override val name: String
            get() = computeTaskName("process", "Resources")

        override val type: Class<LinkApplicationAndroidResourcesTask>
            get() = LinkApplicationAndroidResourcesTask::class.java

        protected open fun preconditionsCheck(creationConfig: ComponentCreationConfig) {}

        private fun generatesProguardOutputFile(
            creationConfig: ComponentCreationConfig
        ): Boolean {
            return ((creationConfig is ConsumableCreationConfig
                    && creationConfig
                .optimizationCreationConfig
                .minifiedEnabled)
                    || creationConfig.componentType.isDynamicFeature)
        }

        private val linkedResourcesArtifactType: InternalArtifactType<Directory>
            // Resource shrinker only works with proto format, so we produce the proto format
            // directly in that case.
            get() = if ((creationConfig as? ApplicationCreationConfig)?.runResourceShrinking() == true) {
                InternalArtifactType.LINKED_RESOURCES_PROTO_FORMAT
            } else {
                InternalArtifactType.LINKED_RESOURCES_BINARY_FORMAT
            }

        override fun handleProvider(
            taskProvider: TaskProvider<LinkApplicationAndroidResourcesTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.processAndroidResTask = taskProvider
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                LinkApplicationAndroidResourcesTask::linkedResourcesOutputDir
            ).on(linkedResourcesArtifactType)

            if (generatesProguardOutputFile(creationConfig)) {
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    LinkApplicationAndroidResourcesTask::proguardOutputFile
                ).withName(SdkConstants.FN_AAPT_RULES).on(InternalArtifactType.AAPT_PROGUARD_FILE)
            }

            if (generateLegacyMultidexMainDexProguardRules) {
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    LinkApplicationAndroidResourcesTask::mainDexListProguardOutputFile
                )
                    .withName("manifest_keep.txt")
                    .on(InternalArtifactType.LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES)
            }

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                LinkApplicationAndroidResourcesTask::stableIdsOutputFileProperty
            ).withName("stableIds.txt").on(InternalArtifactType.STABLE_RESOURCE_IDS_FILE)

        }

        override fun configure(
            task: LinkApplicationAndroidResourcesTask
        ) {
            super.configure(task)
            val projectOptions = creationConfig.services.projectOptions

            preconditionsCheck(creationConfig)

            task.linkedResourcesArtifactType.setDisallowChanges(linkedResourcesArtifactType)
            task.applicationId.setDisallowChanges(creationConfig.applicationId)

            task.incrementalDirectory.set(creationConfig.paths.getIncrementalDir(name))
            task.incrementalDirectory.disallowChanges()

            task.resourceConfigs.setDisallowChanges(
                if (creationConfig is ApplicationCreationConfig) {
                    androidResourcesCreationConfig.resourceConfigurations
                } else {
                    ImmutableSet.of()
                }
            )

            if (creationConfig is ApplicationCreationConfig) {
                task.mainSplit = creationConfig.outputs.getMainSplitOrNull()
            }
            task.namespace.setDisallowChanges(creationConfig.namespace)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.PACKAGED_MANIFESTS,
                task.manifestFiles
            )

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS, task.aaptFriendlyManifestFiles
            )
            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_MANIFESTS,
                task.mergedManifestFiles
            )

            task.componentType.setDisallowChanges(creationConfig.componentType)

            if (creationConfig is ApkCreationConfig) {
                task.noCompress.set(creationConfig.androidResources.noCompress)
                task.aaptAdditionalParameters.set(
                    creationConfig.androidResources.aaptAdditionalParameters
                )
            }
            task.noCompress.disallowChanges()
            task.aaptAdditionalParameters.disallowChanges()

            task.useConditionalKeepRules.setDisallowChanges(
                projectOptions.getProvider(BooleanOption.CONDITIONAL_KEEP_RULES)
            )
            task.useMinimalKeepRules.setDisallowChanges(
                projectOptions.getProvider(BooleanOption.MINIMAL_KEEP_RULES)
            )

            task.mergeBlameLogFolder.setDisallowChanges(
                creationConfig.artifacts.get(
                    InternalArtifactType.MERGED_RES_BLAME_FOLDER
                )
            )
            val componentType = creationConfig.componentType

            val sourceSetMap =
                creationConfig.artifacts.get(InternalArtifactType.SOURCE_SET_PATH_MAP)
            task.sourceSetMaps.fromDisallowChanges(
                creationConfig.services.fileCollection(sourceSetMap)
            )
            task.dependsOn(sourceSetMap)

            // Tests should not have feature dependencies, however because they include the
            // tested production component in their dependency graph, we see the tested feature
            // package in their graph. Therefore we have to manually not set this up for tests.
            if (!componentType.isForTesting) {
                task.featureResourcePackages.fromDisallowChanges(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        COMPILE_CLASSPATH, PROJECT, FEATURE_RESOURCE_PKG
                    )
                )
            } else {
                task.featureResourcePackages.disallowChanges()
            }

            if (componentType.isDynamicFeature && creationConfig is DynamicFeatureCreationConfig) {
                task.resOffset.set(creationConfig.resOffset)
                task.resOffset.disallowChanges()
            }

            task.dynamicFeature.setDisallowChanges(componentType.isDynamicFeature)
            task.projectBaseName.setDisallowChanges(baseName)
            task.library.setDisallowChanges(isLibrary)

            task.useFinalIds.setDisallowChanges(
                projectOptions.getProvider(BooleanOption.USE_NON_FINAL_RES_IDS)
                    .map { !it }
            )

            task.manifestMergeBlameFile.setDisallowChanges(creationConfig.artifacts.get(
                InternalArtifactType.MANIFEST_MERGE_BLAME_FILE
            ))
            creationConfig.services.initializeAapt2Input(task.aapt2, task)
            getBuildService<SymbolTableBuildService, BuildServiceParameters.None>(creationConfig.services.buildServiceRegistry).let {
                task.symbolTableBuildService.setDisallowChanges(it)
                task.usesService(it)
            }
            task.androidJarInput.initialize(task, creationConfig)

            task.useStableIds.setDisallowChanges(
                projectOptions.getProvider(BooleanOption.ENABLE_STABLE_IDS)
            )

            task.aarMetadataCheck.from(
                creationConfig.artifacts.get(InternalArtifactType.AAR_METADATA_CHECK)
            )

            task.outputsHandler.setDisallowChanges(MultiOutputHandler.create(creationConfig))

            when (creationConfig) {
                is ApplicationCreationConfig -> {
                    task.localeFilters.setDisallowChanges(creationConfig.androidResources.localeFilters)
                }

                is DynamicFeatureCreationConfig -> {
                    task.localeFilters.setDisallowChanges(creationConfig.baseModuleLocaleFilters)
                }

                else -> {
                    task.localeFilters.setDisallowChanges(ImmutableSet.of())
                }
            }

            task.pseudoLocalesEnabled.setDisallowChanges(
                androidResourcesCreationConfig.pseudoLocalesEnabled
            )
        }
    }

    internal class CreationAction(
        creationConfig: ComponentCreationConfig,
        generateLegacyMultidexMainDexProguardRules: Boolean,
        private val sourceArtifactType: TaskManager.MergeType,
        baseName: Provider<String>,
        isLibrary: Boolean
    ) : BaseCreationAction(
        creationConfig,
        generateLegacyMultidexMainDexProguardRules,
        baseName,
        isLibrary
    ) {

        override fun preconditionsCheck(creationConfig: ComponentCreationConfig) {
            if (creationConfig.componentType.isAar) {
                throw IllegalArgumentException("Use GenerateLibraryRFileTask")
            } else {
                Preconditions.checkState(
                    sourceArtifactType === TaskManager.MergeType.MERGE,
                    "source output type should be MERGE",
                    sourceArtifactType
                )
            }
        }

        override fun handleProvider(
            taskProvider: TaskProvider<LinkApplicationAndroidResourcesTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                LinkApplicationAndroidResourcesTask::rClassOutputJar
            ).withName(FN_R_CLASS_JAR).on(InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                LinkApplicationAndroidResourcesTask::textSymbolOutputFileProperty
            ).withName(SdkConstants.FN_RESOURCE_TEXT).on(SingleArtifact.RUNTIME_SYMBOL_LIST)

            if (!creationConfig.services.projectOptions[BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS]) {
                // Synthetic output for AARs (see SymbolTableWithPackageNameTransform), and created
                // in process resources for local subprojects.
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    LinkApplicationAndroidResourcesTask::symbolsWithPackageNameOutputFile
                ).withName("package-aware-r.txt").on(InternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME)
            }
        }

        override fun configure(
            task: LinkApplicationAndroidResourcesTask
        ) {
            super.configure(task)

            if (creationConfig.services.projectOptions[BooleanOption.NON_TRANSITIVE_R_CLASS]) {
                // List of local resources, used to generate a non-transitive R for the app module.
                creationConfig.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.LOCAL_ONLY_SYMBOL_LIST,
                    task.localResourcesFile
                )
            }

            task.dependenciesFileCollection.fromDisallowChanges(
                creationConfig
                    .variantDependencies.getArtifactFileCollection(
                        RUNTIME_CLASSPATH,
                        ALL,
                        AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME
                    )
            )
            creationConfig.artifacts.setTaskInputToFinalProduct(
                sourceArtifactType.outputType,
                task.inputResourcesDir
            )

            if (creationConfig.services.projectOptions[BooleanOption.SUPPORT_OEM_TOKEN_LIBRARIES]) {
                task.sharedLibraryDependencies.fromDisallowChanges(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        RUNTIME_CLASSPATH, ALL, AndroidArtifacts.ArtifactType.RES_SHARED_OEM_TOKEN_LIBRARY
                    )
                )
            }

            if (androidResourcesCreationConfig.isPrecompileDependenciesResourcesEnabled) {
                task.compiledDependenciesResources.fromDisallowChanges(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        RUNTIME_CLASSPATH,
                        ALL,
                        AndroidArtifacts.ArtifactType.COMPILED_DEPENDENCIES_RESOURCES
                    ))
            } else {
                task.compiledDependenciesResources.disallowChanges()
            }
            task.namespaced.setDisallowChanges(false)
        }
    }

    /**
     * TODO: extract in to a separate task implementation once splits are calculated in the split
     * discovery task.
     */
    class NamespacedCreationAction(
        creationConfig: ApkCreationConfig,
        generateLegacyMultidexMainDexProguardRules: Boolean,
        baseName: Provider<String>
    ) : BaseCreationAction(
        creationConfig,
        generateLegacyMultidexMainDexProguardRules,
        baseName,
        false
    ) {

        override fun handleProvider(
            taskProvider: TaskProvider<LinkApplicationAndroidResourcesTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                LinkApplicationAndroidResourcesTask::sourceOutputDirProperty
            ).withName("out").on(InternalArtifactType.RUNTIME_R_CLASS_SOURCES)
        }

        override fun configure(
            task: LinkApplicationAndroidResourcesTask
        ) {
            super.configure(task)

            val dependencies = ArrayList<FileCollection>(2)
            dependencies.add(
                creationConfig.services.fileCollection(
                    creationConfig.artifacts.get(InternalArtifactType.RES_STATIC_LIBRARY)
                )
            )
            dependencies.add(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    RUNTIME_CLASSPATH, ALL, AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY
                )
            )

            task.dependenciesFileCollection.fromDisallowChanges(
                creationConfig.services.fileCollection(
                    dependencies
                )
            )

            task.sharedLibraryDependencies.fromDisallowChanges(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    COMPILE_CLASSPATH, ALL, AndroidArtifacts.ArtifactType.RES_SHARED_STATIC_LIBRARY
                )
            )

            task.namespaced.setDisallowChanges(true)
        }
    }

    companion object {
        private val LOG = Logging.getLogger(LinkApplicationAndroidResourcesTask::class.java)

        @Synchronized
        @Throws(IOException::class)
        fun appendOutput(
            artifactType: InternalArtifactType<Directory>,
            applicationId: String,
            variantName: String,
            output: BuiltArtifactImpl,
            resPackageOutputFolder: File
        ) {
            (BuiltArtifactsLoaderImpl.loadFromDirectory(resPackageOutputFolder)?.addElement(output)
                    ?: BuiltArtifactsImpl(
                        artifactType = artifactType,
                        applicationId = applicationId,
                        variantName = variantName,
                        elements = listOf(output)
                    )
            ).saveToDirectory(resPackageOutputFolder)
        }

        @Throws(IOException::class)
        private fun invokeAaptForSplit(
            variantOutput: VariantOutputImpl.SerializedForm,
            manifestOutput: BuiltArtifactImpl,
            generateRClass: Boolean,
            aapt2: Aapt2,
            stableIdsInputFile: File?,
            parameters: TaskWorkActionParameters) {

            val featurePackagesBuilder = ImmutableList.builder<File>()
            for (featurePackage in parameters.featureResourcePackages.files) {
                val buildElements = BuiltArtifactsLoaderImpl.loadFromDirectory(featurePackage)

                if (buildElements?.elements != null && buildElements.elements.isNotEmpty()) {
                    val mainBuildOutput =
                        buildElements.getBuiltArtifact(VariantOutputConfiguration.OutputType.SINGLE)
                    if (mainBuildOutput != null) {
                        featurePackagesBuilder.add(File(mainBuildOutput.outputFile))
                    } else {
                        throw IOException(
                            "Cannot find PROCESSED_RES output for $variantOutput"
                        )
                    }
                }
            }

            val variantName = if (variantOutput.fullName.isNotEmpty()) {
                RES_QUALIFIER_SEP + variantOutput.fullName
            } else ""

            val linkedResourcesOutputFile = File(
                parameters.linkedResourcesOutputDir.get().asFile,
                parameters.linkedResourcesArtifactType.get().name().lowercase().replace("_", "-") + variantName + SdkConstants.DOT_RES
            )
            val generateProtos = when (parameters.linkedResourcesArtifactType.get()) {
                is InternalArtifactType.LINKED_RESOURCES_PROTO_FORMAT -> true
                is InternalArtifactType.LINKED_RESOURCES_BINARY_FORMAT -> false
                else -> error("Unexpected artifact type: ${parameters.linkedResourcesArtifactType.get()}")
            }

            val manifestFile = manifestOutput.outputFile

            var packageForR: String? = null
            var srcOut: File? = null
            var symbolOutputDir: File? = null
            var proguardOutputFile: File? = null
            var mainDexListProguardOutputFile: File? = null
            if (generateRClass) {
                packageForR = parameters.namespace.get()

                // we have to clean the source folder output in case the package name changed.
                srcOut = parameters.sourceOutputDirectory.orNull?.asFile
                if (srcOut != null) {
                    FileUtils.cleanOutputDir(srcOut)
                }

                symbolOutputDir = parameters.textSymbolOutputFile.orNull?.asFile?.parentFile
                proguardOutputFile = parameters.proguardOutputFile.orNull?.asFile
                mainDexListProguardOutputFile = parameters.mainDexListProguardOutputFile.orNull?.asFile
            }

            val densityFilterData = variantOutput.variantOutputConfiguration
                .getFilter(FilterConfiguration.FilterType.DENSITY)

            val preferredDensity = densityFilterData?.identifier

            parameters.localeFilters.get().forEach {
                if (!validLocale(it)) {
                    throw RuntimeException("The locale in localeFilters \"$it\" is invalid.")
                }
            }
            if (parameters.localeFilters.get().isNotEmpty()) {
                parameters.resourceConfigs.get().forEach {
                    if (validLocale(it)) {
                        throw RuntimeException(
                            "When localeFilters are specified, resourceConfigurations cannot " +
                            "include locale qualifiers. Please move all locale qualifiers to " +
                            "localeFilters."
                        )
                    }
                }
            }

            try {

                // If the new resources flag is enabled and if we are dealing with a library process
                // resources through the new parsers
                run {
                    val configBuilder = AaptPackageConfig.Builder()
                        .setManifestFile(File(manifestFile))
                        .setOptions(parameters.aaptOptions.get())
                        .setCustomPackageForR(packageForR)
                        .setSymbolOutputDir(symbolOutputDir)
                        .setSourceOutputDir(srcOut)
                        .setResourceOutputApk(linkedResourcesOutputFile)
                        .setGenerateProtos(generateProtos)
                        .setProguardOutputFile(proguardOutputFile)
                        .setMainDexListProguardOutputFile(mainDexListProguardOutputFile)
                        .setComponentType(parameters.componentType.get())
                        .setResourceConfigs(parameters.resourceConfigs.get())
                        .setPreferredDensity(preferredDensity)
                        .setPackageId(parameters.packageId.orNull)
                        .setAllowReservedPackageId(
                            parameters.packageId.isPresent && parameters.packageId.get() < FeatureSetMetadata.BASE_ID
                        )
                        .setDependentFeatures(featurePackagesBuilder.build())
                        .setImports(parameters.imports.files)
                        .setIntermediateDir(parameters.incrementalDirectory.get().asFile)
                        .setAndroidJarPath(parameters.androidJarInput.get()
                            .getAndroidJar()
                            .get().absolutePath)
                        .setUseConditionalKeepRules(parameters.useConditionalKeepRules.get())
                        .setUseMinimalKeepRules(parameters.useMinimalKeepRules.get())
                        .setUseFinalIds(parameters.useFinalIds.get())
                        .setEmitStableIdsFile(parameters.outputStableIdsFile.orNull?.asFile)
                        .setConsumeStableIdsFile(stableIdsInputFile)
                        .setLocalSymbolTableFile(parameters.localResourcesFile.orNull?.asFile)
                        .setMergeBlameDirectory(parameters.mergeBlameDirectory.get().asFile)
                        .setManifestMergeBlameFile(parameters.manifestMergeBlameFile.orNull?.asFile)
                        .setLocaleFilters(parameters.localeFilters.get())
                        .setPseudoLocalesEnabled(parameters.pseudoLocalesEnabled.get())
                        .apply {
                            val compiledDependencyResourceFiles =
                                parameters.compiledDependenciesResources.files
                            // In the event of running process[variant]AndroidTestResources
                            // on a module that depends on a module with no precompiled resources,
                            // we must avoid passing the compiled resource directory to AAPT link.
                            if (compiledDependencyResourceFiles.all(File::exists)) {
                                addResourceDirectories(
                                    compiledDependencyResourceFiles.reversed().toImmutableList())
                            }
                        }

                    if (parameters.namespaced.get()) {
                        configBuilder.setStaticLibraryDependencies(ImmutableList.copyOf(parameters.dependencies.files))
                    } else {
                        if (generateRClass) {
                            configBuilder.setLibrarySymbolTableFiles(parameters.dependencies.files)
                        }
                        configBuilder.addResourceDir(checkNotNull(parameters.inputResourcesDirectory.orNull?.asFile))
                    }

                    val logger = Logging.getLogger(LinkApplicationAndroidResourcesTask::class.java)

                    configBuilder.setIdentifiedSourceSetMap(
                            mergeIdentifiedSourceSetFiles(
                                    parameters.sourceSetMaps.files.filterNotNull())
                    )

                    processResources(
                        aapt = aapt2,
                        aaptConfig = configBuilder.build(),
                        rJar = if (generateRClass) parameters.rClassOutputJar.orNull?.asFile else null,
                        logger = logger,
                        errorFormatMode = parameters.aapt2.get().getErrorFormatMode(),
                        symbolTableLoader = parameters.symbolTableBuildService.get()::loadClasspath,
                    )

                    if (LOG.isInfoEnabled) {
                        LOG.info("Aapt output file {}", linkedResourcesOutputFile.absolutePath)
                    }
                }

                if (generateRClass
                    && (parameters.library.get() || !parameters.dependencies.files.isEmpty())
                    && parameters.symbolsWithPackageNameOutputFile.isPresent
                ) {
                    SymbolIo.writeSymbolListWithPackageName(
                        parameters.textSymbolOutputFile.orNull?.asFile!!.toPath(),
                        packageForR,
                        parameters.symbolsWithPackageNameOutputFile.get().asFile.toPath()
                    )
                }
                appendOutput(
                    parameters.linkedResourcesArtifactType.get(),
                    parameters.applicationId.get().orEmpty(),
                    parameters.variantName.get(),
                    manifestOutput.newOutput(linkedResourcesOutputFile.toPath()),
                    parameters.linkedResourcesOutputDir.get().asFile
                )
            } catch (e: ProcessException) {
                throw BuildException(
                    "Failed to process resources, see aapt output above for details.", e
                )
            }
        }

        fun validLocale(locale: String): Boolean {
            val parts = locale.split('-').map { it.lowercase() }
            return LocaleValue().initFromParts(parts, 0) != 0
        }
    }


    @Internal // sourceOutputDirProperty is already marked as @OutputDirectory
    override fun getSourceOutputDir(): File? {
        return sourceOutputDirProperty.orNull?.asFile
    }

    @Suppress("unused") // Used by butterknife
    @Internal
    fun getTextSymbolOutputFile(): File? = textSymbolOutputFileProperty.orNull?.asFile

}
