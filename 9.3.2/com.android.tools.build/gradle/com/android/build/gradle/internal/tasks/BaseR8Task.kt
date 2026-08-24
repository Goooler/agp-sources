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
import com.android.build.gradle.internal.services.R8D8ThreadPoolBuildService
import com.android.build.gradle.internal.services.R8MaxParallelTasksBuildService
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.utils.getDesugarLibConfig
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.SyncOptions
import com.android.build.gradle.tasks.PackageAndroidArtifact.Companion.THROW_ON_ERROR_ISSUE_REPORTER
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.PartialShrinking
import com.android.builder.dexing.PartialShrinkingConfig
import com.android.builder.dexing.PartialShrinkingIncludeAll
import com.android.builder.dexing.R8OutputType
import com.android.builder.dexing.ToolConfig
import java.io.File
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
