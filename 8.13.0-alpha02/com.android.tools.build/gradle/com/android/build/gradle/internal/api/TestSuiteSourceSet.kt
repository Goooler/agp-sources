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

package com.android.build.gradle.internal.api

import com.android.build.api.variant.SourceDirectories
import com.android.builder.model.v2.models.SourceType
import org.gradle.api.Incubating

/**
 * Simplistic version of a test suite source set.
 *
 * Most versions will only have a single folder but more complicated use cases including a full
 * android source set is possible in the future.
 */
/** @suppress */
@Incubating
sealed interface TestSuiteSourceSet{

    val type:  SourceType

    @Incubating
    interface Assets: TestSuiteSourceSet {
        @Incubating
        fun get(): SourceDirectories.Flat

        override val type: SourceType
            get() = SourceType.ASSETS
    }

    @Incubating
    interface HostJar: TestSuiteSourceSet {
        @Incubating
        fun get(): SourceDirectories.Flat

        override val type: SourceType
            get() = SourceType.HOST_JAR
    }

    @Incubating
    interface TestApk: TestSuiteSourceSet {
        @Incubating
        fun getByName(name: String): SourceDirectories.Flat

        override val type: SourceType
            get() = SourceType.TEST_APK
    }
}
