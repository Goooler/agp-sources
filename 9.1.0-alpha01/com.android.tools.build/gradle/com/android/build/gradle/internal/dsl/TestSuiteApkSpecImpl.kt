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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.AgpTestSuiteDependencies
import com.android.build.api.dsl.TestSuiteTestApkSpec
import com.android.build.gradle.internal.api.ApkTestSuiteSourceSet
import com.android.build.gradle.internal.api.TestSuiteSourceSet
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.testsuites.TestSuiteSourceCreationConfig
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

class TestSuiteApkSpecImpl @Inject internal constructor(
    objects: ObjectFactory,
    override val name: String,
): TestSuiteTestApkSpec, TestSuiteSourceCreationConfig {
    /**
     * PUBLIC APIs
     */
    override val dependencies: AgpTestSuiteDependencies = objects.newInstance(AgpTestSuiteDependencies::class.java)

    fun dependencies(action:Action<AgpTestSuiteDependencies>) {
        dependencies { action.execute(this) }
    }

    override fun dependencies(action: AgpTestSuiteDependencies.() -> Unit) {
        action.invoke(dependencies)
    }

    /**
     * INTERNAL APIs
     */
    override fun createTestSuiteSourceSet(variantServices: VariantServices): TestSuiteSourceSet {
        return ApkTestSuiteSourceSet(
            sourceSetName = name,
            variantServices = variantServices,
        )
    }
}
