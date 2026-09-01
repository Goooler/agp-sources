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

package com.android.build.gradle.internal.testsuites.impl

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.features.DexingImpl
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.InternalSources
import com.android.build.api.variant.impl.AndroidResourcesImpl
import com.android.build.api.variant.impl.ApkPackagingImpl
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.api.variant.impl.LayeredSourceDirectoriesImpl
import com.android.build.api.variant.impl.SigningConfigImpl
import com.android.build.api.variant.impl.TestSuiteSourceContainer
import com.android.build.gradle.internal.api.TestApkTestSuiteSourceSet
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.TargetSdkAwareConfig
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.component.features.DexingCreationConfig
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.Option
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.build.gradle.options.getOption
import com.android.builder.core.ComponentTypeImpl
import com.android.utils.appendCapitalized
import java.io.File
import org.gradle.api.JavaVersion

class TestSuiteApkCreationConfig(val testSuite: TestSuiteCreationConfig, val sourceContainer: TestSuiteSourceContainer) :
  ApkCreationConfig,
  ConsumableCreationConfig by testSuite.testedVariant,
  TargetSdkAwareConfig by (testSuite.testedVariant as TargetSdkAwareConfig) {

  override val name: String
    get() = sourceContainer.identifier

  override val baseName: String
    get() = sourceContainer.identifier

  override val artifacts: ArtifactsImpl
    get() = sourceContainer.artifacts

  override val testOnlyApk: Boolean
    get() = true

  override val shouldPackageProfilerDependencies: Boolean
    get() = false

  override val advancedProfilingTransforms: List<String>
    get() = emptyList()

  override val packaging: ApkPackaging by lazy {
    val testedApkConfig = testSuite.testedVariant as? ApkCreationConfig
    if (testedApkConfig != null) {
      testedApkConfig.packaging
    } else {
      val extension = sourceContainer.project.extensions.findByType(CommonExtension::class.java)
      val dslPackaging = extension?.packaging ?: error("Packaging options not found")
      ApkPackagingImpl(dslPackaging, testSuite.variantServices, testSuite.testedVariant.minSdk.apiLevel)
    }
  }

  override val signingConfig: SigningConfigImpl? by lazy {
    val testedApkConfig = testSuite.testedVariant as? ApkCreationConfig
    if (testedApkConfig != null) {
      testedApkConfig.signingConfig
    } else {
      val extension = sourceContainer.project.extensions.findByType(CommonExtension::class.java)
      val dslSigningConfig = extension?.signingConfigs?.findByName("debug") as? com.android.build.gradle.internal.dsl.SigningConfig
      dslSigningConfig?.let { SigningConfigImpl(it, testSuite.variantServices, testSuite.testedVariant.minSdk.apiLevel, targetApi = null) }
    }
  }

  override val variantDependencies: VariantDependencies by lazy {
    val classpath = sourceContainer.suiteSourceClasspath
    VariantDependencies(
      variantName = name,
      componentType = ComponentTypeImpl.TEST_APK,
      compileClasspath = classpath.compileClasspath,
      runtimeClasspath = classpath.runtimeClasspath,
      lintChecksClasspath = testSuite.testedVariant.variantDependencies.lintChecksClasspath,
      sourceSetRuntimeConfigurations = listOf(classpath.runtimeClasspath),
      sourceSetImplementationConfigurations = emptyList(),
      elements = emptyMap(),
      providedClasspath = testSuite.testedVariant.variantDependencies.runtimeClasspath,
      annotationProcessorConfiguration = null,
      reverseMetadataValuesConfiguration = null,
      testedVariant = testSuite.testedVariant,
      project = sourceContainer.project,
      projectOptions = testSuite.services.projectOptions,
      isSelfInstrumenting = false,
      sourceSetConfigurationsMap = emptyMap(),
    )
  }

  override val dexing: DexingCreationConfig by lazy {
    DexingImpl(
      component = this,
      multiDexEnabledFromDsl = null,
      multiDexProguardFile = null,
      multiDexKeepFile = null,
      internalServices = testSuite.variantServices,
    )
  }

  override val enableApiModeling: Boolean
    get() =
      (testSuite.testedVariant as? ApkCreationConfig)?.enableApiModeling
        ?: (testSuite.services.projectOptions.get(OptionalBooleanOption.ENABLE_API_MODELING_AND_GLOBAL_SYNTHETICS)
          ?: !testSuite.testedVariant.debuggable)

  override val enableGlobalSynthetics: Boolean
    get() =
      (testSuite.testedVariant as? ApkCreationConfig)?.enableGlobalSynthetics
        ?: run {
          val enableForDebug = testSuite.services.projectOptions.get(BooleanOption.ENABLE_GLOBAL_SYNTHETICS_FOR_ALL_DEBUG_BUILDS)
          val legacyBehavior = !testSuite.testedVariant.debuggable || isJavaLanguageLevelAbove14()
          testSuite.services.projectOptions.get(OptionalBooleanOption.ENABLE_API_MODELING_AND_GLOBAL_SYNTHETICS)
            ?: (enableForDebug || legacyBehavior)
        }

  private fun isJavaLanguageLevelAbove14(): Boolean {
    return testSuite.global.compileOptions.sourceCompatibility.isCompatibleWith(JavaVersion.VERSION_14) &&
      testSuite.global.compileOptions.targetCompatibility.isCompatibleWith(JavaVersion.VERSION_14)
  }

  override val androidResources: AndroidResourcesImpl
    get() = testSuite.testedVariant.androidResources ?: error("Android resources are required for Test Suite APK")

  override val isForceAotCompilation: Boolean
    get() = false

  override val taskContainer: MutableTaskContainer by lazy { MutableTaskContainer() }

  override val sources: InternalSources by lazy { TestSuiteSources(testSuite.testedVariant.sources, sourceContainer) }

  override val paths: VariantPathHelper by lazy {
    TestSuitePathHelper(
      sourceContainer = sourceContainer,
      testedVariantPathHelper = testSuite.testedVariant.paths,
      componentIdentity = testSuite.testedVariant,
      projectOptionsLookup = testSuite.services.projectOptions::getOption,
      fileCreator = testSuite.services::file,
    )
  }

  override fun computeTaskNameInternal(prefix: String, suffix: String): String = prefix.appendCapitalized(name, suffix)

  override fun computeTaskNameInternal(prefix: String): String = prefix.appendCapitalized(name)
}

class TestSuiteSources(val delegate: InternalSources, val sourceContainer: TestSuiteSourceContainer) : InternalSources by delegate {
  override val assets: LayeredSourceDirectoriesImpl?
    get() = null

  override val res: LayeredSourceDirectoriesImpl?
    get() = null

  override val jniLibs: LayeredSourceDirectoriesImpl?
    get() = null

  override val resources: FlatSourceDirectoriesImpl?
    get() = (sourceContainer.source as? TestApkTestSuiteSourceSet)?.resources
}

class TestSuitePathHelper(
  val sourceContainer: TestSuiteSourceContainer,
  val testedVariantPathHelper: VariantPathHelper,
  componentIdentity: ComponentIdentity,
  projectOptionsLookup: (Option<*>) -> Any?,
  fileCreator: (Any) -> File,
) :
  VariantPathHelper(
    buildDirectory = testedVariantPathHelper.buildDirectory,
    componentIdentity = componentIdentity,
    componentType = ComponentTypeImpl.TEST_APK,
    directorySegmentsLambda = { emptyList() },
    baseNameLambda = { sourceContainer.identifier },
    baseNameWithSplitsLambda = { splitName -> "main-$splitName" },
    projectOptionsLookup = projectOptionsLookup,
    fileCreator = fileCreator,
  ) {
  override val dirName: String
    get() = sourceContainer.identifier

  override val baseName: String
    get() = sourceContainer.identifier
}
