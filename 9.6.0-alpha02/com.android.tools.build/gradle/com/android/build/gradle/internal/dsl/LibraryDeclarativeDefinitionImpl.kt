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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.AndroidLibrarySourceSet
import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.ComposeOptions
import com.android.build.api.dsl.DataBinding
import com.android.build.api.dsl.DeclarativeLibraryBuildType
import com.android.build.api.dsl.DeclarativeLibraryFlavor
import com.android.build.api.dsl.DependenciesExtension
import com.android.build.api.dsl.ExternalNativeBuild
import com.android.build.api.dsl.LibraryAndroidResources
import com.android.build.api.dsl.LibraryBuildFeatures
import com.android.build.api.dsl.LibraryBuildType
import com.android.build.api.dsl.LibraryDeclarativeDefinition
import com.android.build.api.dsl.LibraryDefaultConfig
import com.android.build.api.dsl.LibraryInstallation
import com.android.build.api.dsl.LibraryProductFlavor
import com.android.build.api.dsl.LibraryPublishing
import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.Packaging
import com.android.build.api.dsl.Prefab
import com.android.build.api.dsl.Splits
import com.android.build.api.dsl.TestCoverage
import com.android.build.api.dsl.TestOptions
import com.android.build.api.dsl.ViewBinding
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.services.DeclarativeDslServicesImpl
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.model.v2.ide.ProjectType
import java.util.function.Supplier
import javax.inject.Inject
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition

abstract class LibraryDeclarativeDefinitionImpl
@Inject
constructor(objectFactory: ObjectFactory, providerFactory: ProviderFactory, layout: ProjectLayout) :
  TestedDeclarativeExtensionImpl<LibraryBuildType, LibraryDefaultConfig, LibraryProductFlavor>(
    DeclarativeDslServicesImpl(objectFactory, providerFactory, layout, ProjectType.LIBRARY)
  ),
  LibraryDeclarativeDefinition {

  @get:Incubating override val aidlPackagedList: MutableCollection<String> = mutableListOf()

  override val androidResources: LibraryAndroidResources =
    dslServices.newDecoratedInstance(
      LibraryAndroidResourcesImpl::class.java,
      dslServices,
      dslServices.projectOptions[BooleanOption.BUILD_FEATURE_ANDROID_RESOURCES],
    )

  override val buildFeatures: LibraryBuildFeatures =
    dslServices.newDecoratedInstance(LibraryBuildFeaturesImpl::class.java, Supplier { androidResources }, dslServices)

  override val buildTypes: NamedDomainObjectContainer<DeclarativeLibraryBuildType> =
    dslServices.domainObjectContainer(DeclarativeBuildType::class.java, DeclarativeBuildTypeFactory(dslServices, ComponentTypeImpl.LIBRARY))
      as NamedDomainObjectContainer<DeclarativeLibraryBuildType>

  override fun NamedDomainObjectContainer<DeclarativeLibraryBuildType>.debug(action: DeclarativeLibraryBuildType.() -> Unit) {
    getByName("debug", action)
  }

  override fun NamedDomainObjectContainer<DeclarativeLibraryBuildType>.release(action: DeclarativeLibraryBuildType.() -> Unit) {
    getByName("release", action)
  }

  override val compileOptions: CompileOptions =
    dslServices.newDecoratedInstance(com.android.build.gradle.internal.CompileOptions::class.java, dslServices)

  override val composeOptions: ComposeOptions = dslServices.newInstance(ComposeOptionsImpl::class.java, dslServices)

  override val dataBinding: DataBinding =
    dslServices.newDecoratedInstance(DataBindingOptions::class.java, Supplier { buildFeatures }, dslServices)

  override val viewBinding: ViewBinding =
    dslServices.newDecoratedInstance(ViewBindingOptionsImpl::class.java, Supplier { buildFeatures }, dslServices)

  override val testCoverage: TestCoverage = dslServices.newInstance(JacocoOptions::class.java)

  override val testOptions: TestOptions =
    dslServices.newInstance(com.android.build.gradle.internal.dsl.TestOptions::class.java, dslServices)

  override val splits: Splits = dslServices.newDecoratedInstance(com.android.build.gradle.internal.dsl.Splits::class.java, dslServices)

  @get:HiddenInDefinition
  override val sourceSets: NamedDomainObjectContainer<AndroidLibrarySourceSet> =
    dslServices.domainObjectContainer(AndroidLibrarySourceSet::class.java)

  override val lint: Lint = dslServices.newDecoratedInstance(LintImpl::class.java, dslServices)

  override val installation: LibraryInstallation = dslServices.newDecoratedInstance(LibraryInstallationImpl::class.java, dslServices)

  override val packaging: Packaging = dslServices.newDecoratedInstance(PackagingOptions::class.java, dslServices)

  override val productFlavors: NamedDomainObjectContainer<DeclarativeLibraryFlavor> =
    dslServices.domainObjectContainer(DeclarativeProductFlavor::class.java, DeclarativeProductFlavorFactory(dslServices))
      as NamedDomainObjectContainer<DeclarativeLibraryFlavor>

  override val defaultConfig: LibraryDefaultConfig =
    dslServices.newDecoratedInstance(DefaultConfig::class.java, "defaultConfig", dslServices)

  override val signingConfigs: NamedDomainObjectContainer<ApkSigningConfig> =
    dslServices.domainObjectContainer(SigningConfig::class.java, SigningConfigFactory(dslServices, null))
      as NamedDomainObjectContainer<ApkSigningConfig>

  override val externalNativeBuild: ExternalNativeBuild =
    dslServices.newDecoratedInstance(com.android.build.gradle.internal.dsl.ExternalNativeBuild::class.java, dslServices)

  @get:Incubating
  override val prefab: NamedDomainObjectContainer<Prefab> =
    dslServices.domainObjectContainer(Prefab::class.java, PrefabModuleFactory(dslServices))

  override val publishing: LibraryPublishing = dslServices.newDecoratedInstance(LibraryPublishingImpl::class.java, dslServices)

  override var resourcePrefix: String?
    get() = androidResources.resourcePrefix.ifEmpty { null }
    set(value) {
      androidResources.resourcePrefix = value ?: ""
    }

  override val dependencies: DependenciesExtension = dslServices.newInstance(DependenciesExtension::class.java)
}
