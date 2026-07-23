/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.dependency.ShrinkerVersion
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.services.R8D8ThreadPoolBuildService
import com.android.build.gradle.internal.services.doClose
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.dexing.KeepRuleFile
import com.android.builder.dexing.PartialShrinking
import com.android.builder.dexing.ProguardConfig
import com.android.builder.dexing.ProguardOutputFiles
import com.android.builder.dexing.ProguardOutputReports
import com.android.builder.dexing.R8OutputType
import com.android.builder.dexing.ResourceShrinkingConfig
import com.android.builder.dexing.ToolConfig
import com.android.builder.dexing.runR8
import com.android.utils.FileUtils
import com.android.zipflinger.ZipArchive
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

/**
 * Task that uses R8 to convert class files to dex. In case of a library variant, this task outputs class files.
 *
 * R8 task inputs are: program class files, library class files (e.g. android.jar), java resourcesJar, Proguard configuration files, main
 * dex list configuration files, other tool-specific parameters. Output is dex or class files, depending on whether we are building an APK,
 * or AAR.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.OPTIMIZATION)
abstract class R8Task @Inject constructor(projectLayout: ProjectLayout) : BaseR8Task(projectLayout) {

  // R8 will produce either classes or dex
  @get:Optional @get:OutputFile abstract val outputClasses: RegularFileProperty

  @get:Optional @get:OutputDirectory abstract val outputDex: DirectoryProperty

  @get:Optional @get:OutputDirectory abstract val featureDexDir: DirectoryProperty

  @get:Optional @get:OutputDirectory abstract val featureJavaResourceOutputDir: DirectoryProperty

  @get:OutputFile abstract val outputResources: RegularFileProperty

  @get:Input abstract val enableR8ConfigurationAnalyzerReport: Property<Boolean>

  @get:Optional @get:OutputFile abstract val r8ConfigurationAnalyzerDataOutput: RegularFileProperty

  @get:Optional @get:OutputFile abstract val r8ConfigurationAnalyzerReportOutput: RegularFileProperty

  @get:Optional @get:OutputFile abstract val mainDexListOutput: RegularFileProperty

  @get:Optional @get:OutputFile abstract val outputArtProfile: RegularFileProperty

  @get:OutputFile abstract val proguardSeedsOutput: RegularFileProperty

  @get:OutputFile abstract val proguardUsageOutput: RegularFileProperty

  @get:OutputFile abstract val proguardConfigurationOutput: RegularFileProperty

  @get:OutputFile abstract val missingKeepRulesOutput: RegularFileProperty

  @get:OutputFile abstract val r8Metadata: RegularFileProperty

  @get:Inject abstract val providerFactory: ProviderFactory

  class CreationAction(creationConfig: ConsumableCreationConfig, isTestApplication: Boolean = false, addCompileRClass: Boolean) :
    BaseR8Task.CreationAction<R8Task, ConsumableCreationConfig>(creationConfig, isTestApplication, addCompileRClass) {
    override val type = R8Task::class.java
    override val name = computeTaskName("minify", "WithR8")

    override fun handleProvider(taskProvider: TaskProvider<R8Task>) {
      super.handleProvider(taskProvider)

      creationConfig.artifacts.setInitialProvider(taskProvider, R8Task::proguardSeedsOutput).on(InternalArtifactType.R8_MAPPING_SEEDS)
      creationConfig.artifacts.setInitialProvider(taskProvider, R8Task::proguardUsageOutput).on(InternalArtifactType.R8_MAPPING_USAGE)
      creationConfig.artifacts
        .setInitialProvider(taskProvider, R8Task::proguardConfigurationOutput)
        .on(InternalArtifactType.R8_MAPPING_CONFIGURATION)
      creationConfig.artifacts
        .setInitialProvider(taskProvider, R8Task::missingKeepRulesOutput)
        .on(InternalArtifactType.R8_MAPPING_MISSING_RULES)
      creationConfig.artifacts.setInitialProvider(taskProvider, R8Task::r8Metadata).on(InternalArtifactType.R8_METADATA)

      when {
        componentType.isAar -> {
          creationConfig.artifacts
            .setInitialProvider(taskProvider, R8Task::outputClasses)
            .withName("shrunkClasses.jar")
            .on(InternalArtifactType.SHRUNK_CLASSES)
        }

        componentType.isApk -> {
          creationConfig as ApkCreationConfig
          creationConfig.artifacts.use(taskProvider).wiredWith(R8Task::outputDex).toAppendTo(InternalMultipleArtifactType.DEX)
        }

        else -> error("Unexpected component type: $componentType")
      }

      creationConfig.artifacts
        .setInitialProvider(taskProvider, R8Task::r8ConfigurationAnalyzerDataOutput)
        .on(InternalArtifactType.R8_MAPPING_CONFIGURATION_ANALYZER_DATA)

      creationConfig.artifacts
        .setInitialProvider(taskProvider, R8Task::r8ConfigurationAnalyzerReportOutput)
        .on(InternalArtifactType.R8_MAPPING_CONFIGURATION_ANALYZER_REPORT)

      creationConfig.artifacts
        .use(taskProvider)
        .wiredWithFiles(R8Task::resourcesJar, R8Task::outputResources)
        .toTransform(InternalArtifactType.MERGED_JAVA_RES)

      if ((creationConfig as? ApplicationCreationConfig)?.runResourceShrinking() == true) {
        creationConfig.artifacts
          .setInitialProvider(taskProvider) { (it as BaseR8Task).resourceShrinkingParams.shrunkResourcesOutputDir }
          .on(InternalArtifactType.SHRUNK_RESOURCES_PROTO_FORMAT)
        creationConfig.artifacts
          .setInitialProvider(taskProvider) { (it as BaseR8Task).resourceShrinkingParams.logFile }
          .on(InternalArtifactType.R8_MAPPING_RESOURCES)
      }

      if ((creationConfig as? ApplicationCreationConfig)?.shrinkingWithDynamicFeatures == true) {
        creationConfig.artifacts.setInitialProvider(taskProvider, R8Task::featureDexDir).on(InternalArtifactType.FEATURE_DEX)

        creationConfig.artifacts
          .setInitialProvider(taskProvider, R8Task::featureJavaResourceOutputDir)
          .on(InternalArtifactType.FEATURE_SHRUNK_JAVA_RES)

        if (creationConfig.runResourceShrinking()) {
          creationConfig.artifacts
            .setInitialProvider(taskProvider) { (it as BaseR8Task).resourceShrinkingParams.featureShrunkResourcesOutputDir }
            .on(InternalArtifactType.FEATURE_SHRUNK_RESOURCES_PROTO_FORMAT)
        }
      }

      if (creationConfig is ApkCreationConfig) {
        when {
          creationConfig.dexing.needsMainDexListForBundle -> {
            creationConfig.artifacts
              .setInitialProvider(taskProvider, R8Task::mainDexListOutput)
              .withName("mainDexList.txt")
              .on(InternalArtifactType.MAIN_DEX_LIST_FOR_BUNDLE)
          }
          creationConfig.dexing.dexingType.isLegacyMultiDex -> {
            creationConfig.artifacts
              .setInitialProvider(taskProvider, R8Task::mainDexListOutput)
              .withName("mainDexList.txt")
              .on(InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST)
          }
        }
      }
      if (creationConfig is VariantCreationConfig && !creationConfig.debuggable) {
        creationConfig.artifacts
          .use(taskProvider)
          .wiredWithFiles(R8Task::inputArtProfile, R8Task::outputArtProfile)
          .toTransform(InternalArtifactType.R8_ART_PROFILE)
      }
    }

    override fun configure(task: R8Task) {
      super.configure(task)

      task.enableR8ConfigurationAnalyzerReport.setDisallowChanges(
        creationConfig.services.projectOptions.getProvider(BooleanOption.R8_ENABLE_KEEP_RADIUS_REPORT)
      )
    }
  }

  override fun doTaskAction() {
    // verify r8 gradual settings
    verifyGradualShrinkingConfiguration()

    val output: Property<out FileSystemLocation> =
      when {
        componentType.orNull?.isAar == true -> outputClasses
        else -> outputDex
      }
    // Check for duplicate java resourcesJar if there are dynamic features. We allow duplicate
    // META-INF/services/** entries.
    val featureJavaResourceJarsList = featureJavaResourceJars.toList()
    if (featureJavaResourceJarsList.isNotEmpty()) {
      val paths: MutableSet<String> = mutableSetOf()
      featureJavaResourceJarsList.plus(resourcesJar.asFile.get()).forEach { file ->
        ZipArchive(file.toPath()).use { jar ->
          jar.listEntries().forEach { path ->
            if (!path.startsWith("META-INF/services/") && !paths.add(path)) {
              throw RuntimeException(
                "Multiple dynamic-feature and/or base APKs will contain entries " +
                  "with the same path, '$path', which can cause unexpected " +
                  "behavior or errors at runtime. Please consider using " +
                  "android.packagingOptions in the dynamic-feature and/or " +
                  "application modules to ensure that only one of the APKs " +
                  "contains this path."
              )
            }
          }
        }
      }
    }

    val logger = LoggerWrapper.getLogger(R8Task::class.java)
    logger.info(
      """
                |R8 is a new Android code shrinker. If you experience any issues, please file a bug at
                |https://issuetracker.google.com, using 'Shrinker (R8)' as component name.
                |Current version is: ${ShrinkerVersion.R8.asString()}.
                |"""
        .trimMargin()
    )

    // If inputArtProfile exists but artProfileRewriting is false, we need to copy it over
    // to outputArtProfile.
    val inputArtProfileFile = inputArtProfile.orNull?.asFile
    if (inputArtProfileFile?.exists() == true && artProfileRewriting.get() == false) {
      outputArtProfile.orNull?.asFile?.let { FileUtils.copyFile(inputArtProfileFile, it) }
    }

    if (resourceShrinkingParams.enabled.get()) {
      resourceShrinkingParams.saveOutputBuiltArtifactsMetadata()
    }

    val workerAction = { it: R8Runnable.Params ->
      it.bootClasspath.from(bootClasspath.toList())
      it.mainDexListFiles.from(getCombinedMainDexListFiles())
      it.mainDexRulesFiles.from(getCombinedMainDexRulesFiles())
      it.mainDexListOutput.set(mainDexListOutput.orNull?.asFile)
      it.proguardConfigurationFiles.set(
        reconcileDefaultProguardFile(obtainKeepRules(), extractedDefaultProguardFile, failOnMissingProguardFiles.get())
      )
      it.inputProguardMapping.set(
        if (testedMappingFile.isEmpty) {
          null
        } else {
          testedMappingFile.singleFile
        }
      )
      it.proguardConfigurations.set(proguardConfigurations)
      it.legacyMultiDexEnabled.set(legacyMultiDexEnabled)
      it.referencedInputs.from((referencedClasses + referencedResources).toList())
      it.classes.from(
        if (shrinkingWithDynamicFeatures.get() && !hasAllAccessTransformers.get()) {
          listOf(baseJar.get().asFile)
        } else {
          classes.toList()
        }
      )
      it.resourcesJar.set(resourcesJar)
      if (enableR8ConfigurationAnalyzerReport.get()) {
        it.r8ConfigurationAnalyzerDataOutput.set(r8ConfigurationAnalyzerDataOutput.get().asFile)
        it.r8ConfigurationAnalyzerReportOutput.set(r8ConfigurationAnalyzerReportOutput.get().asFile)
      }
      it.mappingFile.set(mappingFile.get().asFile)
      it.mappingPartitionFile.set(mappingPartitionFile.get().asFile)
      it.proguardSeedsOutput.set(proguardSeedsOutput.get().asFile)
      it.proguardUsageOutput.set(proguardUsageOutput.get().asFile)
      it.proguardConfigurationOutput.set(proguardConfigurationOutput.get().asFile)
      it.missingKeepRulesOutput.set(missingKeepRulesOutput.get().asFile)
      it.output.set(output.get().asFile)
      it.outputResources.set(outputResources.get().asFile)
      it.featureClassJars.from(featureClassJars.toList())
      it.featureJavaResourceJars.from(featureJavaResourceJarsList)
      it.featureDexDir.set(featureDexDir.asFile.orNull)
      it.featureJavaResourceOutputDir.set(featureJavaResourceOutputDir.asFile.orNull)
      it.libConfiguration.set(coreLibDesugarConfig.orNull)
      it.errorFormatMode.set(errorFormatMode.get())
      if (artProfileRewriting.get()) {
        it.inputArtProfile.set(inputArtProfile)
        it.outputArtProfile.set(outputArtProfile)
      }
      it.inputProfileForDexStartupOptimization.set(inputProfileForDexStartupOptimization)
      it.r8Metadata.set(r8Metadata)
      it.toolConfig.set(toolParameters.toToolConfig())
      it.resourceShrinkingConfig.set(resourceShrinkingParams.toConfig())
      it.partialShrinkingIncludes.set(aggregatePartialShrinkingConfig())
      // Note: Build service can only be passed in Gradle worker non-isolation mode
      if (executionOptions.get().runInSeparateProcess) {
        it.r8ThreadPoolSizeIfIsolationMode.set(r8ThreadPoolSize)
      } else {
        it.r8D8ThreadPoolBuildServiceIfNonIsolationMode.set(r8D8ThreadPoolBuildService)
      }
    }
    if (executionOptions.get().runInSeparateProcess) {
      workerExecutor
        .processIsolation { spec ->
          spec.forkOptions { forkOptions ->
            forkOptions.jvmArgs(executionOptions.get().jvmArgs)
            // Also copy over system properties (see b/380110863).
            // Once we have a list of R8-specific system properties (tracked at b/383727630),
            // we'll copy over those properties only (and also define those properties as
            // task inputs).
            forkOptions.systemProperties(System.getProperties().mapKeys { it.key.toString() })
          }
        }
        .submit(R8Runnable::class.java, workerAction)
    } else {
      workerExecutor.noIsolation().submit(R8Runnable::class.java, workerAction)
    }
  }

  companion object {
    fun shrink(
      bootClasspath: List<File>,
      mainDexListFiles: List<File>,
      mainDexRulesFiles: List<File>,
      mainDexListOutput: File?,
      legacyMultiDexEnabled: Boolean,
      referencedInputs: List<File>,
      classes: List<File>,
      resourcesJar: File,
      keepRuleWithOrigins: List<KeepRuleFile>,
      inputProguardMapping: File?,
      proguardConfigurations: MutableList<String>,
      r8ConfigurationAnalyzerDataOutput: File?,
      r8ConfigurationAnalyzerReportOutput: File?,
      mappingFile: File,
      mappingPartitionFile: File,
      proguardSeedsOutput: File,
      proguardUsageOutput: File,
      proguardConfigurationOutput: File,
      missingKeepRulesOutput: File,
      output: File,
      outputResources: File,
      featureClassJars: List<File>,
      featureJavaResourceJars: List<File>,
      featureDexDir: File?,
      featureJavaResourceOutputDir: File?,
      libConfiguration: String?,
      errorFormatMode: com.android.build.gradle.options.SyncOptions.ErrorFormatMode,
      inputArtProfile: File?,
      outputArtProfile: File?,
      inputProfileForDexStartupOptimization: File?,
      r8Metadata: File?,
      toolConfig: ToolConfig,
      resourceShrinkingConfig: ResourceShrinkingConfig?,
      partialShrinkingIncludes: PartialShrinking?,
      r8ThreadPool: ExecutorService,
    ) {
      val logger = LoggerWrapper.getLogger(R8Task::class.java)

      resourceShrinkingConfig?.let {
        if (!resourceShrinkingConfig.nonFinalResIds && resourceShrinkingConfig.optimizedShrinking) {
          throw IllegalStateException(
            "Optimized resource shrinking requires non-final IDs.\n" +
              "Suggestion: opt back in to non-final resource IDs by setting " +
              "android.nonFinalResIds=true in gradle.properties.\n" +
              "Alternative: temporarily opt out of optimized resource shrinking " +
              "until you're ready to migrate by setting " +
              "android.r8.optimizedResourceShrinking=false"
          )
        }
      }

      FileUtils.deleteIfExists(outputResources)
      if (toolConfig.r8OutputType == R8OutputType.CLASSES) {
        FileUtils.deleteIfExists(output)
      } else {
        FileUtils.cleanOutputDir(output)
        featureDexDir?.let { FileUtils.cleanOutputDir(it) }
        featureJavaResourceOutputDir?.let { FileUtils.cleanOutputDir(it) }
      }

      val proguardOutputFiles =
        ProguardOutputFiles(
          mappingFile.toPath(),
          mappingPartitionFile.toPath(),
          proguardSeedsOutput.toPath(),
          proguardUsageOutput.toPath(),
          proguardConfigurationOutput.toPath(),
          missingKeepRulesOutput.toPath(),
        )

      val proguardOutputReports =
        if (r8ConfigurationAnalyzerDataOutput != null && r8ConfigurationAnalyzerReportOutput != null) {
          ProguardOutputReports(r8ConfigurationAnalyzerDataOutput.toPath(), r8ConfigurationAnalyzerReportOutput.toPath())
        } else null

      val proguardConfig =
        ProguardConfig(
          keepRuleWithOrigins,
          inputProguardMapping?.toPath(),
          proguardConfigurations,
          proguardOutputFiles,
          proguardOutputReports,
        )

      val mainDexListConfig =
        if (legacyMultiDexEnabled) {
          com.android.builder.dexing.MainDexListConfig(
            mainDexRulesFiles.map { it.toPath() },
            mainDexListFiles.map { it.toPath() },
            getPlatformRules(),
            mainDexListOutput?.toPath(),
          )
        } else {
          com.android.builder.dexing.MainDexListConfig()
        }

      // When invoking R8 we filter out missing files. E.g. javac output may not exist if
      // there are no Java sources. See b/151605314 for details.
      runR8(
        filterMissingFiles(classes, logger),
        output.toPath(),
        resourcesJar.toPath(),
        outputResources.toPath(),
        bootClasspath.map { it.toPath() },
        filterMissingFiles(referencedInputs, logger),
        toolConfig,
        proguardConfig,
        mainDexListConfig,
        resourceShrinkingConfig,
        MessageReceiverImpl(errorFormatMode, Logging.getLogger(R8Runnable::class.java)),
        featureClassJars.map { it.toPath() },
        featureJavaResourceJars.map { it.toPath() },
        featureDexDir?.toPath(),
        featureJavaResourceOutputDir?.toPath(),
        libConfiguration,
        inputArtProfile?.toPath(),
        outputArtProfile?.toPath(),
        inputProfileForDexStartupOptimization?.toPath(),
        r8Metadata?.toPath(),
        partialShrinkingIncludes,
        r8ThreadPool,
      )
    }

    private fun filterMissingFiles(files: List<File>, logger: LoggerWrapper): List<Path> {
      return files.mapNotNull { file ->
        if (file.exists()) file.toPath()
        else {
          logger.verbose("$file is ignored as it does not exist.")
          null
        }
      }
    }
  }

  abstract class R8Runnable : WorkAction<R8Runnable.Params> {

    abstract class Params : WorkParameters {
      abstract val bootClasspath: ConfigurableFileCollection
      abstract val mainDexListFiles: ConfigurableFileCollection
      abstract val mainDexRulesFiles: ConfigurableFileCollection
      abstract val mainDexListOutput: RegularFileProperty
      abstract val legacyMultiDexEnabled: Property<Boolean>
      abstract val referencedInputs: ConfigurableFileCollection
      abstract val classes: ConfigurableFileCollection
      abstract val resourcesJar: RegularFileProperty
      abstract val proguardConfigurationFiles: ListProperty<KeepRuleFile>
      abstract val inputProguardMapping: RegularFileProperty
      abstract val proguardConfigurations: ListProperty<String>
      abstract val r8ConfigurationAnalyzerDataOutput: RegularFileProperty
      abstract val r8ConfigurationAnalyzerReportOutput: RegularFileProperty
      abstract val mappingFile: RegularFileProperty
      abstract val mappingPartitionFile: RegularFileProperty
      abstract val proguardSeedsOutput: RegularFileProperty
      abstract val proguardUsageOutput: RegularFileProperty
      abstract val proguardConfigurationOutput: RegularFileProperty
      abstract val missingKeepRulesOutput: RegularFileProperty
      abstract val output: RegularFileProperty
      abstract val outputResources: RegularFileProperty
      abstract val featureClassJars: ConfigurableFileCollection
      abstract val featureJavaResourceJars: ConfigurableFileCollection
      abstract val featureDexDir: DirectoryProperty
      abstract val featureJavaResourceOutputDir: DirectoryProperty
      abstract val libConfiguration: Property<String>
      abstract val errorFormatMode: Property<com.android.build.gradle.options.SyncOptions.ErrorFormatMode>
      abstract val inputArtProfile: RegularFileProperty
      abstract val outputArtProfile: RegularFileProperty
      abstract val inputProfileForDexStartupOptimization: RegularFileProperty
      abstract val r8Metadata: RegularFileProperty
      abstract val toolConfig: Property<ToolConfig>
      abstract val resourceShrinkingConfig: Property<ResourceShrinkingConfig>
      abstract val partialShrinkingIncludes: Property<PartialShrinking>
      abstract val r8ThreadPoolSizeIfIsolationMode: Property<Int> // Set iff in Gradle worker isolation mode
      abstract val r8D8ThreadPoolBuildServiceIfNonIsolationMode:
        Property<R8D8ThreadPoolBuildService> // Set iff in Gradle worker non-isolation mode
    }

    override fun execute() {
      // In Gradle worker isolation mode, use a new thread pool for each R8 task.
      // In non-isolation mode, use the shared thread pool for all R8 tasks.
      val isolationMode = parameters.r8ThreadPoolSizeIfIsolationMode.isPresent
      val r8ThreadPool =
        if (isolationMode) {
          R8D8ThreadPoolBuildService.newThreadPool(parameters.r8ThreadPoolSizeIfIsolationMode.get())
        } else {
          parameters.r8D8ThreadPoolBuildServiceIfNonIsolationMode.get().threadPool
        }
      try {
        shrink(
          parameters.bootClasspath.files.toList(),
          parameters.mainDexListFiles.files.toList(),
          parameters.mainDexRulesFiles.files.toList(),
          parameters.mainDexListOutput.orNull?.asFile,
          parameters.legacyMultiDexEnabled.get(),
          parameters.referencedInputs.files.toList(),
          parameters.classes.files.toList(),
          parameters.resourcesJar.asFile.get(),
          parameters.proguardConfigurationFiles.get(),
          parameters.inputProguardMapping.orNull?.asFile,
          parameters.proguardConfigurations.get(),
          parameters.r8ConfigurationAnalyzerDataOutput.orNull?.asFile,
          parameters.r8ConfigurationAnalyzerReportOutput.orNull?.asFile,
          parameters.mappingFile.get().asFile,
          parameters.mappingPartitionFile.get().asFile,
          parameters.proguardSeedsOutput.get().asFile,
          parameters.proguardUsageOutput.get().asFile,
          parameters.proguardConfigurationOutput.get().asFile,
          parameters.missingKeepRulesOutput.get().asFile,
          parameters.output.get().asFile,
          parameters.outputResources.get().asFile,
          parameters.featureClassJars.files.toList(),
          parameters.featureJavaResourceJars.files.toList(),
          parameters.featureDexDir.orNull?.asFile,
          parameters.featureJavaResourceOutputDir.orNull?.asFile,
          parameters.libConfiguration.orNull,
          parameters.errorFormatMode.get(),
          parameters.inputArtProfile.orNull?.asFile,
          parameters.outputArtProfile.orNull?.asFile,
          parameters.inputProfileForDexStartupOptimization.orNull?.asFile,
          parameters.r8Metadata.orNull?.asFile,
          parameters.toolConfig.get(),
          parameters.resourceShrinkingConfig.orNull,
          parameters.partialShrinkingIncludes.orNull,
          r8ThreadPool,
        )
      } finally {
        // In isolation mode, we use a separate thread pool, so we need to close it now.
        // In non-isolation mode, we use a shared thread pool, and we will close it in the
        // build service.
        if (isolationMode) {
          r8ThreadPool.doClose()
        }
      }
    }
  }
}
