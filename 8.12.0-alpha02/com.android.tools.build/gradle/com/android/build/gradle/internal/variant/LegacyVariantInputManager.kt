/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.variant

import com.android.SdkConstants
import com.android.build.gradle.internal.DefaultConfigData
import com.android.build.gradle.internal.api.LazyAndroidSourceSet
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.BuildTypeFactory
import com.android.build.gradle.internal.dsl.DeclarativeBuildType
import com.android.build.gradle.internal.dsl.DeclarativeBuildTypeFactory
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.ProductFlavorFactory
import com.android.build.gradle.internal.dsl.DeclarativeProductFlavor
import com.android.build.gradle.internal.dsl.DeclarativeProductFlavorFactory
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.dsl.SigningConfigFactory
import com.android.build.gradle.internal.packaging.getDefaultDebugKeystoreLocation
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.BuilderConstants
import com.android.builder.core.ComponentType
import com.android.builder.core.ComponentTypeImpl
import org.gradle.api.NamedDomainObjectContainer

/**
 * Implementation of [AbstractVariantInputManager] with the legacy types for build types, flavors,
 * etc...
 */
class LegacyVariantInputManager(
    dslServices: DslServices,
    componentType: ComponentType,
    sourceSetManager: SourceSetManager
) : AbstractVariantInputManager<DefaultConfig, BuildType, ProductFlavor, SigningConfig>(
    dslServices,
    componentType,
    sourceSetManager
) {

    override val buildTypeContainer: NamedDomainObjectContainer<BuildType> = if (dslServices.projectOptions[BooleanOption.USE_DECLARATIVE_INTERFACES]) {
        dslServices.domainObjectContainer(
            DeclarativeBuildType::class.java, DeclarativeBuildTypeFactory(dslServices, componentType)
        ) as NamedDomainObjectContainer<BuildType>
    } else {
        dslServices.domainObjectContainer(
            BuildType::class.java, BuildTypeFactory(dslServices, componentType)
        )
    }


    override val productFlavorContainer: NamedDomainObjectContainer<ProductFlavor> = if (dslServices.projectOptions[BooleanOption.USE_DECLARATIVE_INTERFACES]) {
        dslServices.domainObjectContainer(
            DeclarativeProductFlavor::class.java, DeclarativeProductFlavorFactory(dslServices)
        ) as NamedDomainObjectContainer<ProductFlavor>
    } else {
        dslServices.domainObjectContainer(
            ProductFlavor::class.java, ProductFlavorFactory(dslServices)
        )
    }

    override val signingConfigContainer: NamedDomainObjectContainer<SigningConfig> =
        dslServices.domainObjectContainer(
            SigningConfig::class.java,
            SigningConfigFactory(
                dslServices,
                getBuildService(
                    dslServices.buildServiceRegistry,
                    AndroidLocationsBuildService::class.java
                ).get().getDefaultDebugKeystoreLocation()
            )
        )

    override val defaultConfig: DefaultConfig = dslServices.newDecoratedInstance(
        DefaultConfig::class.java,
        BuilderConstants.MAIN,
        dslServices
    )
    override val defaultConfigData: DefaultConfigData<DefaultConfig>

    init {
        var testFixturesSourceSet: LazyAndroidSourceSet? = null
        var androidTestSourceSet: LazyAndroidSourceSet? = null
        var unitTestSourceSet: LazyAndroidSourceSet? = null
        var screenshotTestSourceSet: LazyAndroidSourceSet? = null
        if (componentType.hasTestComponents) {
            androidTestSourceSet =
                sourceSetManager.setUpSourceSet(
                    ComponentType.ANDROID_TEST_PREFIX,
                    ComponentTypeImpl.ANDROID_TEST
                )
            unitTestSourceSet =
                sourceSetManager.setUpSourceSet(
                    ComponentType.UNIT_TEST_PREFIX,
                    ComponentTypeImpl.UNIT_TEST
                )
            testFixturesSourceSet =
                sourceSetManager.setUpSourceSet(
                    ComponentType.TEST_FIXTURES_PREFIX,
                    ComponentTypeImpl.TEST_FIXTURES
                )
            screenshotTestSourceSet =
                if (dslServices.projectOptions[BooleanOption.ENABLE_SCREENSHOT_TEST]) {
                    sourceSetManager.setUpSourceSet(
                        ComponentType.SCREENSHOT_TEST_PREFIX,
                        ComponentTypeImpl.SCREENSHOT_TEST
                    )
                } else null
        }

        defaultConfigData = DefaultConfigData(
            defaultConfig = defaultConfig,
            sourceSet = sourceSetManager.setUpSourceSet(SdkConstants.FD_MAIN, componentType).get(),
            testFixturesSourceSet = testFixturesSourceSet,
            androidTestSourceSet = androidTestSourceSet,
            unitTestSourceSet = unitTestSourceSet,
            screenshotTestSourceSet = screenshotTestSourceSet,
            lazySourceSetCreation = dslServices.projectOptions[BooleanOption.ENABLE_NEW_TEST_DSL]
        )

        // map the whenObjectAdded/whenObjectRemoved callbacks on the containers.

        signingConfigContainer.whenObjectAdded(this::addSigningConfig)
        signingConfigContainer.whenObjectRemoved {
            throw UnsupportedOperationException("Removing signingConfigs is not supported.")
        }

        buildTypeContainer.whenObjectAdded(this::addBuildType)
        buildTypeContainer.whenObjectRemoved {
            throw UnsupportedOperationException("Removing build types is not supported.")
        }

        productFlavorContainer.whenObjectAdded(this::addProductFlavor);
        productFlavorContainer.whenObjectRemoved {
            throw UnsupportedOperationException("Removing product flavors is not supported.")
        }

    }
}
