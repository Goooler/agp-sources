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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.ApplicationAndroidResources
import com.android.build.api.dsl.ApplicationBuildFeatures
import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.ComposeOptions
import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.DependenciesExtension
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig
import com.android.builder.core.LibraryRequest
import com.android.repository.Revision
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.declarative.dsl.model.annotations.Configuring

open class BaseAppModuleExtensionInternal(
    dslServices: DslServices,
    bootClasspathConfig: BootClasspathConfig,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    private val publicExtensionImpl: ApplicationExtensionImpl,
    stats: GradleBuildProject.Builder?
) : BaseAppModuleExtension (
    dslServices,
    bootClasspathConfig,
    buildOutputs,
    sourceSetManager,
    publicExtensionImpl,
    stats
) {
    @Deprecated("Use dependencies{} block inside build type and product flavors")
    val dependenciesDcl: DependenciesExtension by lazy {
        dslServices.newInstance(DependenciesExtension::class.java)
    }

    @Deprecated("Use dependencies{} block inside build type and product flavors")
    @Configuring
    fun dependenciesDcl(configure: DependenciesExtension.() -> Unit) {
        configure.invoke(dependenciesDcl)
    }

    override val buildTypes: NamedDomainObjectContainer<DeclarativeBuildType>
        get() = publicExtensionImpl.buildTypes as NamedDomainObjectContainer<DeclarativeBuildType>

    override val productFlavors: NamedDomainObjectContainer<DeclarativeProductFlavor>
        get() = publicExtensionImpl.productFlavors as NamedDomainObjectContainer<DeclarativeProductFlavor>
}

/** The `android` extension for base feature module (application plugin).  */
open class BaseAppModuleExtension(
    dslServices: DslServices,
    bootClasspathConfig: BootClasspathConfig,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    private val publicExtensionImpl: ApplicationExtensionImpl,
    stats: GradleBuildProject.Builder?
) : AppExtension(
    dslServices,
    bootClasspathConfig,
    buildOutputs,
    sourceSetManager,
    true,
    stats
), InternalApplicationExtension by publicExtensionImpl {

    // Overrides to make the parameterized types match, due to BaseExtension being part of
    // the previous public API and not wanting to paramerterize that.
    override val buildTypes: NamedDomainObjectContainer<out BuildType>
        get() = publicExtensionImpl.buildTypes as NamedDomainObjectContainer<BuildType>

    override val defaultConfig: DefaultConfig
        get() = publicExtensionImpl.defaultConfig as DefaultConfig

    override val productFlavors: NamedDomainObjectContainer<out ProductFlavor>
        get() = publicExtensionImpl.productFlavors as NamedDomainObjectContainer<ProductFlavor>

    override val sourceSets: NamedDomainObjectContainer<AndroidSourceSet>
        get() = publicExtensionImpl.sourceSets

    override val composeOptions: ComposeOptions = publicExtensionImpl.composeOptions

    override val bundle: BundleOptions = publicExtensionImpl.bundle as BundleOptions

    override val flavorDimensionList: MutableList<String>
        get() = flavorDimensions

    override val buildToolsRevision: Revision
        get() = Revision.parseRevision(buildToolsVersion, Revision.Precision.MICRO)

    override val libraryRequests: MutableCollection<LibraryRequest>
        get() = publicExtensionImpl.libraryRequests

    override val androidResources: ApplicationAndroidResources
        get() = publicExtensionImpl.androidResources

    override val buildFeatures: ApplicationBuildFeatures
        get() = publicExtensionImpl.buildFeatures

    @Configuring
    override fun defaultConfig(action: ApplicationDefaultConfig.() -> Unit) {
        action.invoke(defaultConfig)
    }

    @Configuring
    override fun buildFeatures(action: ApplicationBuildFeatures.() -> Unit) {
        action.invoke(buildFeatures)
    }

    //TODO(b/421964815): remove the support for groovy space assignment(e.g `compileSdk 24`).
    @Deprecated(
        "To be removed after Gradle drops space assignment support",
        ReplaceWith("compileSdk {}")
    )
    open fun compileSdk(version: Int) {
        compileSdk = version
    }
}
