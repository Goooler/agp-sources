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

import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.ToolExecutionOptions
import com.android.build.gradle.internal.manifest.parseManifest
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.DUPLICATE_CLASSES_CHECK
import com.android.build.gradle.internal.scope.Java8LangSupport
import com.android.build.gradle.internal.services.R8ClassloaderBuildService
import com.android.build.gradle.internal.services.R8D8ThreadPoolBuildService
import com.android.build.gradle.internal.services.R8MaxParallelTasksBuildService
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.utils.getDesugarLibConfig
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.SyncOptions
import com.android.build.gradle.tasks.PackageAndroidArtifact.Companion.THROW_ON_ERROR_ISSUE_REPORTER
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.KeepRuleFile
import com.android.builder.dexing.MainDexListConfig
import com.android.builder.dexing.PartialShrinking
import com.android.builder.dexing.PartialShrinkingConfig
import com.android.builder.dexing.PartialShrinkingIncludeAll
import com.android.builder.dexing.ProguardConfig
import com.android.builder.dexing.R8OutputType
import com.android.builder.dexing.ResourceShrinkingConfig
import com.android.builder.dexing.ToolConfig
import com.android.builder.dexing.runR8
import com.android.ide.common.blame.MessageReceiver
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault

/** Base task for R8 invocations, sharing common inputs, outputs, and creation actions. */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.OPTIMIZATION)
abstract class BaseR8Task(projectLayout: ProjectLayout) : ProguardConfigurableTask(projectLayout) {

  @get:InputFile @get:PathSensitive(PathSensitivity.NONE) @get:Optional abstract val multiDexKeepFile: RegularFileProperty

  @get:InputFile @get:PathSensitive(PathSensitivity.NONE) @get:Optional abstract val multiDexKeepProguard: RegularFileProperty

  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) abstract val mainDexRulesFiles: ConfigurableFileCollection

  @get:Classpath abstract val bootClasspath: ConfigurableFileCollection

  @get:Classpath abstract val r8Classpath: ConfigurableFileCollection

  @get:ServiceReference abstract val r8ClassloaderBuildService: Property<R8ClassloaderBuildService>

  @get:Internal abstract val errorFormatMode: Property<SyncOptions.ErrorFormatMode>

  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) abstract val duplicateClassesCheck: ConfigurableFileCollection

  @get:Input
  lateinit var proguardConfigurations: MutableList<String>
    protected set

  @get:Input abstract val legacyMultiDexEnabled: Property<Boolean>

  @get:Internal abstract val executionOptions: Property<ToolExecutionOptions>

  @get:Input @get:Optional abstract val coreLibDesugarConfig: Property<String>

  @get:Nested abstract val toolParameters: R8ToolParameters

  @get:Input @get:Optional abstract val gradualShrinkingEnabled: Property<Boolean>

  @get:Input @get:Optional abstract val gradualShrinkingPackages: SetProperty<String>

  @get:ServiceReference abstract val r8D8ThreadPoolBuildService: Property<R8D8ThreadPoolBuildService>

  @get:Internal abstract val r8ThreadPoolSize: Property<Int>

  @get:Input abstract val failOnMissingProguardFiles: Property<Boolean>

  @get:Optional @get:Classpath abstract val featureClassJars: ConfigurableFileCollection

  @get:Optional @get:Classpath abstract val featureJavaResourceJars: ConfigurableFileCollection

  @get:Optional @get:Classpath abstract val baseJar: RegularFileProperty

  @get:Input abstract val artProfileRewriting: Property<Boolean>

  @get:Optional @get:PathSensitive(PathSensitivity.NAME_ONLY) @get:InputFiles abstract val inputArtProfile: RegularFileProperty

  @get:Optional
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  @get:InputFiles
  abstract val inputProfileForDexStartupOptimization: RegularFileProperty

  @get:Nested abstract val resourceShrinkingParams: R8ResourceShrinkingParameters

  @Internal
  protected fun getCombinedMainDexListFiles(): List<File> =
    mutableListOf<File>().also {
      if (multiDexKeepFile.isPresent) {
        it.add(multiDexKeepFile.get().asFile)
      }
    }

  @Internal
  protected fun getCombinedMainDexRulesFiles(): List<File> =
    mutableListOf<File>().also {
      it.addAll(mainDexRulesFiles.toList())
      if (multiDexKeepProguard.isPresent) {
        it.add(multiDexKeepProguard.get().asFile)
      }
    }

  /** Returns the program classes input for R8, accounting for dynamic feature splits. */
  @Internal
  protected fun getProgramClasses(): List<File> =
    if (shrinkingWithDynamicFeatures.get() && !hasAllAccessTransformers.get()) {
      listOf(baseJar.get().asFile)
    } else {
      classes.toList()
    }

  protected fun verifyGradualShrinkingConfiguration() {
    if (gradualShrinkingEnabled.orNull == true && gradualShrinkingPackages.get().isEmpty()) {
      throw RuntimeException("Wrong configuration. optimization.packageScope is an empty set, at least one package must be specified.")
    }
  }

  // Merge creation config included/excluded patterns with package.txt with merged R8 packages
  protected fun aggregatePartialShrinkingConfig(): PartialShrinking? {
    if (gradualShrinkingEnabled.orNull != true) return null

    // load from files and from new gradual r8 dsl
    val packages = gradualShrinkingPackages.get().toList()
    if (packages.contains("**")) return PartialShrinkingIncludeAll

    return PartialShrinkingConfig(packages)
  }

  abstract class CreationAction<TaskT : BaseR8Task, CreationConfigT : ConsumableCreationConfig>
  @JvmOverloads
  constructor(creationConfig: CreationConfigT, isTestApplication: Boolean = false, addCompileRClass: Boolean) :
    ProguardConfigurableTask.CreationAction<TaskT, CreationConfigT>(creationConfig, isTestApplication, addCompileRClass) {
    protected val proguardConfigurations: MutableList<String> = mutableListOf()
    private var disableTreeShaking: Boolean = false
    private var disableMinification: Boolean = false

    override fun configure(task: TaskT) {
      super.configure(task)

      val artifacts = creationConfig.artifacts

      useR8D8BuildServices(task, creationConfig.services)
      task.r8ThreadPoolSize.setDisallowChanges(creationConfig.services.projectOptions.get(IntegerOption.R8_THREAD_POOL_SIZE)!!)

      setBootClasspathForCodeShrinker(task)

      task.proguardConfigurations = proguardConfigurations
      task.errorFormatMode.set(SyncOptions.getErrorFormatMode(creationConfig.services.projectOptions))
      task.executionOptions.setDisallowChanges(creationConfig.global.settingsOptions.executionProfile?.r8Options)
      task.failOnMissingProguardFiles.setDisallowChanges(
        creationConfig.services.projectOptions.get(BooleanOption.FAIL_ON_MISSING_PROGUARD_FILES)
      )
      task.legacyMultiDexEnabled.setDisallowChanges(
        creationConfig is ApkCreationConfig && creationConfig.dexing.dexingType == DexingType.LEGACY_MULTIDEX
      )

      if (creationConfig is VariantCreationConfig) {
        task.artProfileRewriting.set(true)
        if (!creationConfig.debuggable) {
          task.inputProfileForDexStartupOptimization.set(artifacts.get(InternalArtifactType.MERGED_STARTUP_PROFILE))
        }
      } else {
        task.artProfileRewriting.set(false)
      }

      if (creationConfig is ApkCreationConfig) {
        task.duplicateClassesCheck.from(artifacts.get(DUPLICATE_CLASSES_CHECK))
        task.mainDexRulesFiles.from(artifacts.getAll(MultipleArtifact.MULTIDEX_KEEP_PROGUARD))
        if (creationConfig.dexing.dexingType.isLegacyMultiDex) {
          task.mainDexRulesFiles.from(artifacts.get(InternalArtifactType.LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES))
        }
        task.multiDexKeepFile.setDisallowChanges(creationConfig.dexing.multiDexKeepFile)
        if (creationConfig.dexing.isCoreLibraryDesugaringEnabled) {
          task.coreLibDesugarConfig.set(getDesugarLibConfig(creationConfig.services))
        }

        if ((creationConfig as? ApplicationCreationConfig)?.shrinkingWithDynamicFeatures == true) {
          creationConfig.artifacts.setTaskInputToFinalProduct(InternalArtifactType.MODULE_AND_RUNTIME_DEPS_CLASSES, task.baseJar)
          task.featureClassJars.from(
            creationConfig.variantDependencies.getArtifactFileCollection(
              AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
              AndroidArtifacts.ArtifactScope.PROJECT,
              AndroidArtifacts.ArtifactType.REVERSE_METADATA_CLASSES,
            )
          )
          task.featureJavaResourceJars.from(
            creationConfig.variantDependencies.getArtifactFileCollection(
              AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
              AndroidArtifacts.ArtifactScope.PROJECT,
              AndroidArtifacts.ArtifactType.REVERSE_METADATA_JAVA_RES,
            )
          )
        }
      }

      task.baseJar.disallowChanges()
      task.featureClassJars.disallowChanges()
      task.featureJavaResourceJars.disallowChanges()

      if (!creationConfig.services.projectOptions[StringOption.R8_VERSION_OVERRIDE].isNullOrBlank()) {
        creationConfig.services.r8FromMaven?.r8Classpath?.let { task.r8Classpath.from(it) }
      }
      task.r8Classpath.disallowChanges()

      if ((creationConfig as? ApplicationCreationConfig)?.runResourceShrinking() == true) {
        task.resourceShrinkingParams.initialize(creationConfig)
      } else {
        task.resourceShrinkingParams.enabled.setDisallowChanges(false)
      }

      task.toolParameters.let {
        it.minSdkVersion.setDisallowChanges(
          if (creationConfig is ApkCreationConfig) {
            creationConfig.dexing.minSdkVersionForDexing
          } else {
            creationConfig.minSdk.apiLevel
          }
        )
        it.debuggable.setDisallowChanges(creationConfig.debuggable)
        it.fullMode.setDisallowChanges(creationConfig.services.projectOptions[BooleanOption.FULL_R8])
        it.strictFullModeForKeepRules.setDisallowChanges(
          creationConfig.services.projectOptions[BooleanOption.R8_STRICT_FULL_MODE_FOR_KEEP_RULES]
        )
        it.packagedManifestDirectory.setDisallowChanges(creationConfig.artifacts.get(InternalArtifactType.PACKAGED_MANIFESTS))
        it.r8OutputType.setDisallowChanges(
          if (componentType.isAar) {
            R8OutputType.CLASSES
          } else {
            R8OutputType.DEX
          }
        )
        it.mainDexListDisallowed.set(creationConfig.services.projectOptions.get(BooleanOption.R8_MAIN_DEX_LIST_DISALLOWED))
        it.disableTreeShaking.setDisallowChanges(disableTreeShaking)
        it.disableMinification.setDisallowChanges(disableMinification)
        it.disableDesugaring.setDisallowChanges(
          !(creationConfig is ApkCreationConfig && creationConfig.dexing.java8LangSupportType == Java8LangSupport.R8)
        )
      }

      // for validation purposes
      task.gradualShrinkingEnabled.setDisallowChanges(
        creationConfig.optimizationCreationConfig.minifiedEnabled && creationConfig.optimizationCreationConfig.packageScopeEnabled
      )

      if (creationConfig.optimizationCreationConfig.packageScopeEnabled) {
        task.gradualShrinkingPackages.setDisallowChanges(creationConfig.optimizationCreationConfig.includePackages)
      }
    }

    override fun keep(keep: String) {
      proguardConfigurations.add("-keep $keep")
    }

    override fun keepAttributes() {
      proguardConfigurations.add("-keepattributes *")
    }

    override fun dontWarn(dontWarn: String) {
      proguardConfigurations.add("-dontwarn $dontWarn")
    }

    private fun setBootClasspathForCodeShrinker(task: TaskT) {
      val javaTarget = creationConfig.global.compileOptions.targetCompatibility

      task.bootClasspath.from(creationConfig.global.fullBootClasspath)
      when {
        javaTarget.isJava9Compatible ->
          task.bootClasspath.from(creationConfig.global.versionedSdkLoader.flatMap { it.coreForSystemModulesProvider })
        javaTarget.isJava8Compatible ->
          task.bootClasspath.from(creationConfig.global.versionedSdkLoader.flatMap { it.coreLambdaStubsProvider })
      }
    }

    private fun useR8D8BuildServices(task: TaskT, services: TaskCreationServices) {
      task.usesService(R8MaxParallelTasksBuildService.RegistrationAction(task.project, services.projectOptions).execute())
      task.r8D8ThreadPoolBuildService.setDisallowChanges(
        R8D8ThreadPoolBuildService.RegistrationAction(task.project, services.projectOptions).execute()
      )
      task.r8ClassloaderBuildService.setDisallowChanges(R8ClassloaderBuildService.RegistrationAction(task.project).execute())
    }
  }
}

/** Similar to [ToolConfig] but containing Gradle types. */
abstract class R8ToolParameters {

  @get:Input abstract val minSdkVersion: Property<Int>

  @get:Input abstract val debuggable: Property<Boolean>

  @get:Input abstract val disableTreeShaking: Property<Boolean>

  @get:Input abstract val disableMinification: Property<Boolean>

  @get:Input abstract val disableDesugaring: Property<Boolean>

  @get:Input abstract val fullMode: Property<Boolean>

  @get:Input abstract val strictFullModeForKeepRules: Property<Boolean>

  /** Used to compute [ToolConfig.isolatedSplits] */
  @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) @get:Optional abstract val packagedManifestDirectory: DirectoryProperty

  @get:Input abstract val r8OutputType: Property<R8OutputType>

  @get:Input abstract val mainDexListDisallowed: Property<Boolean>

  fun toToolConfig() =
    ToolConfig(
      minSdkVersion = minSdkVersion.get(),
      debuggable = debuggable.get(),
      disableTreeShaking = disableTreeShaking.get(),
      disableMinification = disableMinification.get(),
      disableDesugaring = disableDesugaring.get(),
      fullMode = fullMode.get(),
      strictFullModeForKeepRules = strictFullModeForKeepRules.get(),
      isolatedSplits = getIsolatedSplitsValue(),
      r8OutputType = r8OutputType.get(),
      mainDexListDisallowed = mainDexListDisallowed.get(),
    )

  private fun getIsolatedSplitsValue(): Boolean? {
    if (!packagedManifestDirectory.isPresent) return null

    val packagedManifests =
      BuiltArtifactsLoaderImpl().load(packagedManifestDirectory)?.elements
        ?: error("Failed to load manifests from: ${packagedManifestDirectory.get().asFile}")

    val isolatedSplitsValues: Set<Boolean?> =
      packagedManifests
        .map {
          parseManifest(
              File(it.outputFile).readText(),
              it.outputFile,
              manifestFileRequired = true,
              manifestParsingAllowedProvider = null, // Always allow manifest parsing as this should be called only in
              // the execution phase
              THROW_ON_ERROR_ISSUE_REPORTER,
            )
            .isolatedSplits
        }
        .toSet()

    return when (isolatedSplitsValues.size) {
      0 -> error("No manifests found in: ${packagedManifestDirectory.get().asFile}")
      1 -> isolatedSplitsValues.single()
      else -> error("Multiple isolatedSplits values found in ${packagedManifestDirectory.get().asFile}: $isolatedSplitsValues")
    }
  }
}

internal fun getR8ClassLoader(
  r8Classpath: ConfigurableFileCollection,
  r8ClassloaderBuildService: Property<R8ClassloaderBuildService>,
): ClassLoader? {
  return if (!r8Classpath.isEmpty) {
    if (r8ClassloaderBuildService.isPresent) {
      r8ClassloaderBuildService.get().getClassLoader(r8Classpath.files)
    } else {
      R8ClassloaderBuildService.createClassLoader(r8Classpath.files)
    }
  } else {
    null
  }
}

/**
 * Dynamically invokes [runR8] via the provided isolated [r8ClassLoader] when present, or calls [runR8] directly in AGP's ClassLoader when
 * [r8ClassLoader] is null.
 *
 * When an isolated [r8ClassLoader] is used, `builder-r8` is loaded inside the child ClassLoader alongside the unbundled R8 JARs. Because
 * classes loaded by different ClassLoaders are distinct types at runtime, AGP configuration objects (such as [ToolConfig],
 * [ProguardConfig], etc.) must be instantiated using classes loaded from [r8ClassLoader] before being passed into child `runR8`.
 */
internal fun invokeRunR8(
  r8ClassLoader: ClassLoader?,
  inputClasses: Collection<Path>,
  output: Path?,
  inputJavaResJar: Path,
  outputResources: Path?,
  libraries: Collection<Path>,
  classpath: Collection<Path>,
  toolConfig: ToolConfig,
  proguardConfig: ProguardConfig,
  mainDexListConfig: MainDexListConfig,
  resourceShrinkingConfig: ResourceShrinkingConfig?,
  messageReceiver: MessageReceiver,
  featureClassJars: Collection<Path>,
  featureJavaResourceJars: Collection<Path>,
  featureDexDir: Path?,
  featureJavaResourceOutputDir: Path?,
  libConfiguration: String? = null,
  inputArtProfile: Path? = null,
  outputArtProfile: Path? = null,
  inputProfileForDexStartupOptimization: Path? = null,
  r8Metadata: Path? = null,
  partialShrinking: PartialShrinking? = null,
  r8ExecutorService: ExecutorService? = null,
) {
  if (r8ClassLoader != null) {
    try {
      val r8ToolClass = r8ClassLoader.loadClass("com.android.builder.dexing.R8Tool")
      val runR8Method = r8ToolClass.methods.first { it.name == "runR8" }

      // Classes loaded in r8ClassLoader are distinct types from parent AGP classes. Convert
      // configuration objects across the ClassLoader boundary to child-loaded types.
      val childToolConfig = toChildToolConfig(toolConfig, r8ClassLoader)
      val childProguardConfig = toChildProguardConfig(proguardConfig, r8ClassLoader)
      val childMainDexListConfig = toChildMainDexListConfig(mainDexListConfig, r8ClassLoader)
      val childResourceShrinkingConfig = toChildResourceShrinkingConfig(resourceShrinkingConfig, r8ClassLoader)
      val childPartialShrinking = toChildPartialShrinking(partialShrinking, r8ClassLoader)

      runR8Method.invoke(
        null,
        inputClasses,
        output,
        inputJavaResJar,
        outputResources,
        libraries,
        classpath,
        childToolConfig,
        childProguardConfig,
        childMainDexListConfig,
        childResourceShrinkingConfig,
        messageReceiver,
        featureClassJars,
        featureJavaResourceJars,
        featureDexDir,
        featureJavaResourceOutputDir,
        libConfiguration,
        inputArtProfile,
        outputArtProfile,
        inputProfileForDexStartupOptimization,
        r8Metadata,
        childPartialShrinking,
        r8ExecutorService,
      )
    } catch (e: InvocationTargetException) {
      val target = e.targetException ?: e
      when (target) {
        is LinkageError,
        is NoSuchMethodException,
        is NoSuchFieldException -> {
          throw RuntimeException(
            "Failed to execute R8 via isolated ClassLoader. The configured R8 version may be " +
              "incompatible with this version of the Android Gradle Plugin: ${target.message}",
            target,
          )
        }
        else -> throw target
      }
    }
  } else {
    runR8(
      inputClasses,
      output,
      inputJavaResJar,
      outputResources,
      libraries,
      classpath,
      toolConfig,
      proguardConfig,
      mainDexListConfig,
      resourceShrinkingConfig,
      messageReceiver,
      featureClassJars,
      featureJavaResourceJars,
      featureDexDir,
      featureJavaResourceOutputDir,
      libConfiguration,
      inputArtProfile,
      outputArtProfile,
      inputProfileForDexStartupOptimization,
      r8Metadata,
      partialShrinking,
      r8ExecutorService,
    )
  }
}

@Suppress("UNCHECKED_CAST")
private fun toChildToolConfig(toolConfig: ToolConfig, classLoader: ClassLoader): Any {
  val toolConfigClass = classLoader.loadClass("com.android.builder.dexing.ToolConfig")
  val r8OutputTypeClass = classLoader.loadClass("com.android.builder.dexing.R8OutputType")
  val r8OutputType = java.lang.Enum.valueOf(r8OutputTypeClass as Class<out Enum<*>>, toolConfig.r8OutputType.name)
  val constructor = toolConfigClass.constructors.first { it.parameterCount == 10 }
  return constructor.newInstance(
    toolConfig.minSdkVersion,
    toolConfig.debuggable,
    toolConfig.disableTreeShaking,
    toolConfig.disableMinification,
    toolConfig.disableDesugaring,
    toolConfig.fullMode,
    toolConfig.strictFullModeForKeepRules,
    toolConfig.isolatedSplits,
    r8OutputType,
    toolConfig.mainDexListDisallowed,
  )
}

private fun toChildMainDexListConfig(config: MainDexListConfig, classLoader: ClassLoader): Any {
  val mainDexListConfigClass = classLoader.loadClass("com.android.builder.dexing.MainDexListConfig")
  val constructor = mainDexListConfigClass.constructors.first { it.parameterCount == 4 }
  return constructor.newInstance(config.mainDexRulesFiles, config.mainDexListFiles, config.mainDexRules, config.mainDexListOutput)
}

private fun toChildKeepRuleFile(keepRule: KeepRuleFile, classLoader: ClassLoader): Any {
  return when (keepRule) {
    is KeepRuleFile.MavenOrigin -> {
      val clazz = classLoader.loadClass("com.android.builder.dexing.KeepRuleFile\$MavenOrigin")
      val ctor = clazz.constructors.first { it.parameterTypes.size == 5 && it.parameterTypes.all { p -> p == String::class.java } }
      ctor.newInstance(keepRule.displayName, keepRule.group, keepRule.module, keepRule.version, keepRule.file.toString())
    }
    is KeepRuleFile.AgpInternalOrigin -> {
      val clazz = classLoader.loadClass("com.android.builder.dexing.KeepRuleFile\$AgpInternalOrigin")
      val ctor = clazz.constructors.first { it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java }
      ctor.newInstance(keepRule.file.toString())
    }
    is KeepRuleFile.LocalProjectOrigin -> {
      val clazz = classLoader.loadClass("com.android.builder.dexing.KeepRuleFile\$LocalProjectOrigin")
      val ctor = clazz.constructors.first { it.parameterTypes.size == 3 && it.parameterTypes.all { p -> p == String::class.java } }
      ctor.newInstance(keepRule.buildId, keepRule.projectPath, keepRule.file.toString())
    }
    is KeepRuleFile.GeneratedOrigin -> {
      val clazz = classLoader.loadClass("com.android.builder.dexing.KeepRuleFile\$GeneratedOrigin")
      val ctor = clazz.constructors.first { it.parameterTypes.size == 2 && it.parameterTypes.all { p -> p == String::class.java } }
      ctor.newInstance(keepRule.origin, keepRule.file.toString())
    }
    is KeepRuleFile.WithoutOrigin -> {
      val clazz = classLoader.loadClass("com.android.builder.dexing.KeepRuleFile\$WithoutOrigin")
      val ctor = clazz.constructors.first { it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java }
      ctor.newInstance(keepRule.file.toString())
    }
  }
}

private fun toChildProguardConfig(config: ProguardConfig, classLoader: ClassLoader): Any {
  val proguardConfigClass = classLoader.loadClass("com.android.builder.dexing.ProguardConfig")
  val proguardOutputFilesClass = classLoader.loadClass("com.android.builder.dexing.ProguardOutputFiles")
  val proguardOutputReportsClass = classLoader.loadClass("com.android.builder.dexing.ProguardOutputReports")

  val outputFiles =
    config.proguardOutputFiles?.let { files ->
      proguardOutputFilesClass.constructors
        .first { it.parameterCount == 6 }
        .newInstance(
          files.proguardMapOutput,
          files.proguardPartitionMapOutput,
          files.proguardSeedsOutput,
          files.proguardUsageOutput,
          files.proguardConfigurationOutput,
          files.missingKeepRules,
        )
    }
  val outputReports =
    config.proguardOutputReports?.let { reports ->
      proguardOutputReportsClass.constructors
        .first { it.parameterCount == 2 }
        .newInstance(reports.r8ConfigurationAnalyzerDataOutput, reports.r8ConfigurationAnalyzerReportOutput)
    }

  val childKeepRules = config.keepRuleWithOrigins.map { toChildKeepRuleFile(it, classLoader) }

  val constructor = proguardConfigClass.constructors.first { it.parameterCount == 5 }
  return constructor.newInstance(childKeepRules, config.proguardMapInput, config.proguardConfigurations, outputFiles, outputReports)
}

private fun toChildResourceShrinkingConfig(config: ResourceShrinkingConfig?, classLoader: ClassLoader): Any? {
  if (config == null) return null
  val configClass = classLoader.loadClass("com.android.builder.dexing.ResourceShrinkingConfig")
  val shrinkOutputClass = classLoader.loadClass("com.android.builder.dexing.ResourceShrinkingConfig\$ShrinkOutput")

  val shrinkOutput =
    config.shrinkOutput?.let { so ->
      val ctor = shrinkOutputClass.constructors.first { it.parameterCount == 2 }
      ctor.newInstance(so.shrunkResourcesOutputFiles, so.featureShrunkResourcesOutputDir)
    }

  val constructor = configClass.constructors.first { it.parameterCount == 7 }
  return constructor.newInstance(
    config.linkedResourcesInputFiles,
    config.mergedNotCompiledResourcesInputDirs,
    config.featureLinkedResourcesInputFiles,
    config.optimizedShrinking,
    config.nonFinalResIds,
    config.logFile,
    shrinkOutput,
  )
}

private fun toChildPartialShrinking(config: PartialShrinking?, classLoader: ClassLoader): Any? {
  if (config == null) return null
  return when (config) {
    is PartialShrinkingIncludeAll -> {
      val clazz = classLoader.loadClass("com.android.builder.dexing.PartialShrinkingIncludeAll")
      clazz.getField("INSTANCE").get(null)
    }
    is PartialShrinkingConfig -> {
      val clazz = classLoader.loadClass("com.android.builder.dexing.PartialShrinkingConfig")
      val ctor = clazz.constructors.first { it.parameterCount == 1 }
      ctor.newInstance(config.includedPatterns)
    }
  }
}
