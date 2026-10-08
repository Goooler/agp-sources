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

package com.android.build.api.dsl

import com.android.build.api.variant.AndroidLibraryModuleModel
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.features.binding.Definition

/**
 * A declarative definition for the Android Library Gradle Plugin.
 *
 * This interface contains all properties and functions from [LibraryExtension] and its parent interfaces.
 */
@Incubating
/** @suppress */
interface LibraryDeclarativeDefinition : CommonDeclarativeDefinition, TestedDeclarativeDefinition, Definition<AndroidLibraryModuleModel> {

  /** Aidl files to package in the aar. */
  @get:HiddenInDefinition @get:Incubating val aidlPackagedList: MutableCollection<String>?

  /** Specifies options related to the processing of Android Resources. */
  @get:Incubating val androidResources: LibraryAndroidResources

  /** A list of build features that can be enabled or disabled on the Android Project. */
  @get:Incubating val buildFeatures: LibraryBuildFeatures

  /** Encapsulates all build type configurations for this project. */
  @get:Incubating val buildTypes: NamedDomainObjectContainer<DeclarativeLibraryBuildType>

  @Incubating fun NamedDomainObjectContainer<DeclarativeLibraryBuildType>.debug(action: DeclarativeLibraryBuildType.() -> Unit)

  @Incubating fun NamedDomainObjectContainer<DeclarativeLibraryBuildType>.release(action: DeclarativeLibraryBuildType.() -> Unit)

  /** Specifies Java compiler options. */
  @get:Incubating val compileOptions: CompileOptions

  @get:Incubating val composeOptions: ComposeOptions

  /** Specifies options for the Data Binding Library. */
  @get:Incubating val dataBinding: DataBinding

  /** Specifies options for the View Binding Library. */
  @get:Incubating val viewBinding: ViewBinding

  /** Configure the gathering of code-coverage from tests. */
  @get:Incubating val testCoverage: TestCoverage

  /** Specifies options for how the Android plugin should run local and instrumented tests. */
  @get:Incubating val testOptions: TestOptions

  /** Specifies configurations for building multiple APKs or APK splits. */
  @get:Incubating val splits: Splits

  /** Encapsulates source set configurations for all variants. */
  @get:HiddenInDefinition @get:Incubating val sourceSets: NamedDomainObjectContainer<AndroidLibrarySourceSet>

  /** Specifies options for the lint tool. */
  @get:Incubating val lint: Lint

  /** Specifies options for the Android Debug Bridge (ADB). */
  @get:Incubating val installation: LibraryInstallation

  @get:Incubating val packaging: Packaging

  /** Encapsulates all product flavors configurations for this project. */
  @get:Incubating val productFlavors: NamedDomainObjectContainer<DeclarativeLibraryFlavor>

  /** Specifies defaults for variant properties. */
  @get:Incubating val defaultConfig: LibraryDefaultConfig

  /** Encapsulates signing configurations. */
  @get:Incubating val signingConfigs: NamedDomainObjectContainer<ApkSigningConfig>

  /** Specifies options for external native build using CMake or ndk-build. */
  @get:Incubating val externalNativeBuild: ExternalNativeBuild

  /** container of Prefab options */
  @get:Incubating val prefab: NamedDomainObjectContainer<Prefab>

  /** Customizes publishing build variant artifacts from library module to a Maven repository. */
  @get:Incubating val publishing: LibraryPublishing

  @get:Incubating val dependencies: DependenciesExtension
}
