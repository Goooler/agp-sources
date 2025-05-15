/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.build.api.dsl.AgpTestSuiteInputParameters
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.testsuites.JUnitEngineSpec
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.internal.utils.toImmutableSet

class JUnitEngineSpecImpl(
    junitEngineSpec: com.android.build.api.dsl.JUnitEngineSpec,
    variantServices: VariantServices
): JUnitEngineSpec {

    override val includeEngines: Set<String> =
        junitEngineSpec.includeEngines.toImmutableSet()

    override val inputs: List<AgpTestSuiteInputParameters> =
        junitEngineSpec.inputs.toImmutableList()
}
