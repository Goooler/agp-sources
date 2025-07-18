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

package com.android.builder.model.v2.models

/**
 * Defines all dependencies for a test suite.
 *
 * Each test suite can have 1 to many source folders, and each source folder can have its
 * own set of dependencies. Use [TestSuiteSource.name] and [BaseTestSuiteSourceIdentity.name] to
 * match sources and sources dependencies.
 */
interface TestSuiteDependencies {

    /**
     * The collection of dependencies per test sources.
     */
    val sourcesDependencies: Collection<TestSuiteSourceDependencies>
}
