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

import com.android.build.api.dsl.AndroidSourceSet
import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.ApplicationAndroidResources
import com.android.build.api.dsl.ApplicationBuildFeatures
import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.ApplicationDeclarativeDefinition
import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.ApplicationInstallation
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.ApplicationPublishing
import com.android.build.api.dsl.Bundle
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.ComposeOptions
import com.android.build.api.dsl.DataBinding
import com.android.build.api.dsl.DeclarativeApplicationBuildType
import com.android.build.api.dsl.DeclarativeApplicationFlavor
import com.android.build.api.dsl.DependenciesExtension
import com.android.build.api.dsl.DependenciesInfo
import com.android.build.api.dsl.ExternalNativeBuild
import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.Packaging
import com.android.build.api.dsl.Splits
import com.android.build.api.dsl.TestCoverage
import com.android.build.api.dsl.TestOptions
import com.android.build.api.dsl.ViewBinding
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.dsl.decorator.ApplicationInstallationImpl
import com.android.build.gradle.internal.services.DeclarativeDslServicesImpl
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.model.v2.ide.ProjectType
import java.util.function.Supplier
import javax.inject.Inject
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory

abstract class ApplicationDeclarativeDefinitionImpl
@Inject
constructor(objectFactory: ObjectFactory, providerFactory: ProviderFactory, layout: ProjectLayout) :
  TestedDeclarativeExtensionImpl<ApplicationBuildType, ApplicationDefaultConfig, ApplicationProductFlavor>(
    DeclarativeDslServicesImpl(objectFactory, providerFactory, layout, ProjectType.APPLICATION)
  ),
  ApplicationDeclarativeDefinition {

  override val dependenciesInfo: DependenciesInfo = dslServices.newDecoratedInstance(DependenciesInfoImpl::class.java, dslServices)

  @Suppress("UNCHECKED_CAST")
  override val buildTypes: NamedDomainObjectContainer<DeclarativeApplicationBuildType> =
    dslServices.domainObjectContainer(
      DeclarativeBuildType::class.java,
      DeclarativeBuildTypeFactory(dslServices, ComponentTypeImpl.BASE_APK),
    ) as NamedDomainObjectContainer<DeclarativeApplicationBuildType>

  override fun NamedDomainObjectContainer<ApplicationBuildType>.debug(action: DeclarativeApplicationBuildType.() -> Unit) {
    @Suppress("UNCHECKED_CAST") (this as NamedDomainObjectContainer<DeclarativeApplicationBuildType>).getByName("debug", action)
  }

  override fun NamedDomainObjectContainer<ApplicationBuildType>.release(action: DeclarativeApplicationBuildType.() -> Unit) {
    @Suppress("UNCHECKED_CAST") (this as NamedDomainObjectContainer<DeclarativeApplicationBuildType>).getByName("release", action)
  }

  override val bundle: Bundle = dslServices.newDecoratedInstance(BundleOptions::class.java, dslServices)

  override val dynamicFeatures: MutableSet<String> = mutableSetOf()

  override val assetPacks: MutableSet<String> = mutableSetOf()

  override val publishing: ApplicationPublishing = dslServices.newDecoratedInstance(ApplicationPublishingImpl::class.java, dslServices)

  override val androidResources: ApplicationAndroidResources =
    dslServices.newDecoratedInstance(ApplicationAndroidResourcesImpl::class.java, dslServices)

  private val buildFeatures: ApplicationBuildFeatures =
    dslServices.newDecoratedInstance(ApplicationBuildFeaturesImpl::class.java, dslServices)

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

  override val sourceSets: NamedDomainObjectContainer<AndroidSourceSet> = dslServices.domainObjectContainer(AndroidSourceSet::class.java)

  override val lint: Lint = dslServices.newDecoratedInstance(LintImpl::class.java, dslServices)

  override val installation: ApplicationInstallation =
    dslServices.newDecoratedInstance(ApplicationInstallationImpl::class.java, dslServices)

  override val packaging: Packaging = dslServices.newDecoratedInstance(PackagingOptions::class.java, dslServices)

  @Suppress("UNCHECKED_CAST")
  override val productFlavors: NamedDomainObjectContainer<DeclarativeApplicationFlavor> =
    dslServices.domainObjectContainer(DeclarativeProductFlavor::class.java, DeclarativeProductFlavorFactory(dslServices))
      as NamedDomainObjectContainer<DeclarativeApplicationFlavor>

  override val defaultConfig: ApplicationDefaultConfig =
    dslServices.newDecoratedInstance(DefaultConfig::class.java, "defaultConfig", dslServices)

  @Suppress("UNCHECKED_CAST")
  override val signingConfigs: NamedDomainObjectContainer<out ApkSigningConfig> =
    dslServices.domainObjectContainer(SigningConfig::class.java, SigningConfigFactory(dslServices, null))
      as NamedDomainObjectContainer<ApkSigningConfig>

  override val externalNativeBuild: ExternalNativeBuild =
    dslServices.newDecoratedInstance(com.android.build.gradle.internal.dsl.ExternalNativeBuild::class.java, dslServices)

  override val dependencies: DependenciesExtension = dslServices.newInstance(DependenciesExtension::class.java)
}
