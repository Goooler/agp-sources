/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.component.impl

import com.android.build.api.variant.AnnotationProcessor
import com.android.build.api.variant.JavaCompilation
import com.android.build.gradle.api.JavaCompileOptions
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.services.VariantServices
import org.gradle.api.artifacts.Configuration

class JavaCompilationImpl(
    javaCompileOptionsSetInDSL: JavaCompileOptions,
    dataBindingEnabled: Boolean,
    internalServices: VariantServices,
    variantDependencies: VariantDependencies,
): JavaCompilation {

    override val annotationProcessor: AnnotationProcessor =
        AnnotationProcessorImpl(
            javaCompileOptionsSetInDSL.annotationProcessorOptions,
            dataBindingEnabled,
            internalServices
        )

    override val annotationProcessorConfiguration: Configuration =
        variantDependencies.annotationProcessorConfiguration!!
}
