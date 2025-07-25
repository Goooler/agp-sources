/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidCompilationBuilderImpl
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidLibraryExtensionImpl
import com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin.Companion.DEPRECATED_ANDROID_EXTENSION_ON_KOTLIN_EXTENSION_NAME
import com.android.utils.appendCapitalized
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.external.ExternalKotlinCompilationDescriptor
import org.jetbrains.kotlin.gradle.plugin.mpp.external.createCompilation

@OptIn(ExternalKotlinTargetApi::class)
internal class KotlinMultiplatformAndroidCompilationFactory(
    private val project: Project,
    private val target: KotlinMultiplatformAndroidLibraryTargetImpl,
    private val kotlinExtension: KotlinMultiplatformExtension,
    private val androidExtension: KotlinMultiplatformAndroidLibraryExtensionImpl
): NamedDomainObjectFactory<KotlinMultiplatformAndroidCompilation> {

    @Suppress("INVISIBLE_MEMBER")
    override fun create(name: String): KotlinMultiplatformAndroidCompilationImpl {
        val compilationType = when (name) {
            KmpAndroidCompilationType.MAIN.defaultCompilationName -> KmpAndroidCompilationType.MAIN
            androidExtension.androidTestOnJvmBuilder?.compilationName -> KmpAndroidCompilationType.HOST_TEST
            androidExtension.androidTestOnDeviceBuilder?.compilationName -> KmpAndroidCompilationType.DEVICE_TEST
            else -> throw IllegalStateException(
                "Kotlin multiplatform android plugin doesn't support creating arbitrary " +
                        "compilations. Only three types of compilations are supported:\n" +
                        "  * main compilation (named \"${KmpAndroidCompilationType.MAIN.defaultCompilationName}\"),\n" +
                        "  * host test compilation (use `kotlin.$DEPRECATED_ANDROID_EXTENSION_ON_KOTLIN_EXTENSION_NAME.withHostTest {}` to enable),\n" +
                        "  * device test compilation (use `kotlin.$DEPRECATED_ANDROID_EXTENSION_ON_KOTLIN_EXTENSION_NAME.withDeviceTest {}` to enable)."
            )
        }

        val compilationBuilder = when (compilationType) {
            KmpAndroidCompilationType.MAIN -> KotlinMultiplatformAndroidCompilationBuilderImpl(
                compilationType
            )
            KmpAndroidCompilationType.HOST_TEST -> androidExtension.androidTestOnJvmBuilder!!
            KmpAndroidCompilationType.DEVICE_TEST -> androidExtension.androidTestOnDeviceBuilder!!
        }

        val isTestComponent = compilationType != KmpAndroidCompilationType.MAIN

        return target.createCompilation<KotlinMultiplatformAndroidCompilationImpl> {
            compilationName = name
            defaultSourceSet = kotlinExtension.sourceSets.getByName(
                compilationBuilder.defaultSourceSetName
            )
            compilationFactory = ExternalKotlinCompilationDescriptor.CompilationFactory { delegate ->
                when(compilationType) {
                    KmpAndroidCompilationType.MAIN ->
                        KotlinMultiplatformAndroidCompilationImpl(delegate)

                    KmpAndroidCompilationType.HOST_TEST ->
                        KotlinMultiplatformAndroidHostTestCompilationImpl(
                            androidExtension.androidTestOnJvmOptions!!, delegate
                        )

                    KmpAndroidCompilationType.DEVICE_TEST ->
                        KotlinMultiplatformAndroidDeviceTestCompilationImpl(
                            androidExtension.androidTestOnDeviceOptions!!, delegate
                        )
                }
            }
            compileTaskName = "compile".appendCapitalized(
                target.targetName.appendCapitalized(name)
            )

            if (isTestComponent) {
                compilationAssociator = ExternalKotlinCompilationDescriptor.CompilationAssociator { auxiliary, main ->
                    // When associating a test compilation with a main compilation, we add a
                    // dependency from the configurations of the test components on the main project
                    // later. But we still need to add implementation and compileOnly dependencies
                    // from the main compilation to the test compilation to be consistent with the
                    // behaviour of the other kotlin targets.
                    if (main.compilationName == KmpAndroidCompilationType.MAIN.defaultCompilationName) {
                        auxiliary.compileDependencyConfigurationName.addAllDependenciesFromOtherConfigurations(
                            project,
                            main.implementationConfigurationName,
                            main.compileOnlyConfigurationName
                        )
                    } else {
                        ExternalKotlinCompilationDescriptor.CompilationAssociator.default.associate(
                            auxiliary,
                            main
                        )
                    }
                }
            }
            sourceSetTreeClassifierV2 = compilationBuilder.getSourceSetTreeClassifier()
        }
    }

    private fun String.addAllDependenciesFromOtherConfigurations(
        project: Project,
        vararg configurationNames: String
    ) {
        project.configurations.named(this).configure { receiverConfiguration ->
            receiverConfiguration.dependencies.addAllLater(
                project.objects.listProperty(Dependency::class.java).apply {
                    set(
                        project.provider {
                            configurationNames
                                .map { project.configurations.getByName(it) }
                                .flatMap { it.allDependencies }
                        }
                    )
                }
            )
        }
    }
}
