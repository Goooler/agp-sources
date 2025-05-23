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

package com.android.build.api.component.analytics

import com.android.build.api.variant.AnnotationProcessor
import com.android.build.api.variant.JavaCompilation
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.artifacts.Configuration
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class AnalyticsEnabledJavaCompilation @Inject constructor(
    open val delegate: JavaCompilation,
    val stats: GradleBuildVariant.Builder,
    val objectFactory: ObjectFactory
) : JavaCompilation {

    override val annotationProcessor: AnnotationProcessor by lazy(LazyThreadSafetyMode.SYNCHRONIZED){
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.ANNOTATION_PROCESSOR_VALUE

        objectFactory.newInstance(
            AnalyticsEnabledAnnotationProcessor::class.java,
            delegate.annotationProcessor,
            stats,
        )
    }

    override val annotationProcessorConfiguration: Configuration
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.ANNOTATION_PROCESSOR_CONFIGURATION_VALUE
            return delegate.annotationProcessorConfiguration
        }
}
