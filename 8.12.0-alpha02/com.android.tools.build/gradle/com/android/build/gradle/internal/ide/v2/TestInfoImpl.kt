/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.v2

import com.android.builder.model.v2.ide.TestInfo
import java.io.File
import java.io.Serializable

/**
 * Implementation of [TestInfo] for serialization via the Tooling API.
 */
data class TestInfoImpl(
    override val animationsDisabled: Boolean,
    override val execution: TestInfo.Execution?,
    override val additionalRuntimeApks: Collection<File>,
    override val instrumentedTestTaskName: String
) : TestInfo, Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }
}
