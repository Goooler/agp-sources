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

package com.android.build.api.dsl

import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

interface KotlinMultiplatformAndroidLibraryTarget:
    KotlinMultiplatformAndroidTarget,
    KotlinTarget,
    KotlinMultiplatformAndroidLibraryExtension,
    HasConfigurableKotlinCompilerOptions<KotlinJvmCompilerOptions> {

    override val compilations: NamedDomainObjectContainer<KotlinMultiplatformAndroidCompilation>

    /**
     * Enables compilation of java sources.
     */
    override fun withJava()
}


@Deprecated("Use KotlinMultiplatformAndroidLibraryTarget. This interface will be removed in AGP 9.0")
interface KotlinMultiplatformAndroidTarget: KotlinTarget, KotlinMultiplatformAndroidLibraryExtension {
    override val compilations: NamedDomainObjectContainer<KotlinMultiplatformAndroidCompilation>

    /**
     * Enables compilation of java sources.
     */
    fun withJava()
}

@Incubating
fun KotlinMultiplatformExtension.androidLibrary(
    action: KotlinMultiplatformAndroidLibraryTarget.() -> Unit
) {
    (this as ExtensionAware).extensions.findByType(
        KotlinMultiplatformAndroidLibraryTarget::class.java
    )?.action() ?: throw IllegalStateException(
        "You need to apply the " +
            "`com.android.kotlin.multiplatform.library` plugin before accessing the android target."
    )
}
