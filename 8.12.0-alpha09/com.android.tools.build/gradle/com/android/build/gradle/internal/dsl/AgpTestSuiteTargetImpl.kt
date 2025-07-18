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

import com.android.build.api.dsl.AgpTestSuiteTarget
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test

open class AgpTestSuiteTargetImpl(
    val testSuite: AgpTestSuiteImpl,
    private val _name: String
): AgpTestSuiteTarget {

    override val targetDevices: MutableList<String> = mutableListOf()

    @Suppress("UnstableApiUsage")
    override fun getBinaryResultsDirectory(): Provider<Directory> {
        throw RuntimeException("Not implemented")
    }

    override fun getName(): String = _name
}
