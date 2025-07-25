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

package com.android.build.gradle.internal.testsuites

import org.gradle.api.Incubating
import org.gradle.api.Named

/**
 * Test suite target builder.
 */
@Incubating
interface TestSuiteTargetBuilder: Named {

    @get:Incubating
    @set:Incubating
    var enable: Boolean

    /**
     * Targeted devices for this test suite in this variant.
     *
     * AGP may choose to allocate multiple Test tasks for running the test suites depending on the
     * targeted devices, so the list of devices must be fixed before the
     * [com.android.build.api.variant.AndroidComponentsExtension.onVariants] run.
     */
    @get:Incubating
    val targetDevices: MutableList<String>
}
