/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.api.variant

import org.gradle.api.Incubating

/**
 * [Variant] that optionally have [DeviceTest] components like [AndroidTest].
 */
interface HasDeviceTests {

    /**
     * Variant's [Map] of [DeviceTest] configurations, or empty if all devices tests (like android
     * tests) are disabled for this variant.
     *
     *  @return a [Map] which keys are unique names within the tested variant like
     * [DeviceTestBuilder.ANDROID_TEST_TYPE] and the values are [DeviceTest] for that host
     * test suite.
     */
    val deviceTests: Map<String, DeviceTest>
}
