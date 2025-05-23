/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.core.ComponentType

/** Data about a variant that produce a Library bundle (.aar)  */
class LibraryVariantData(
    componentIdentity: ComponentIdentity,
    artifacts: ArtifactsImpl,
    services: VariantServices
) : BaseVariantData(
    componentIdentity,
    artifacts,
    services
), TestedVariantData {
    private val testVariants: MutableMap<ComponentType, TestVariantData> = mutableMapOf()

    override fun getTestVariantData(type: ComponentType): TestVariantData? {
        return testVariants[type]
    }

    override fun setTestVariantData(testVariantData: TestVariantData, type: ComponentType) {
        testVariants[type] = testVariantData
    }
}
