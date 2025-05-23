/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.gradle.internal.dsl.decorator.annotation.WithLazyInitialization
import com.android.builder.core.ToolsRevisionUtils

abstract class PrivacySandboxSdkExtensionImpl: InternalPrivacySandboxSdkExtension {

    override var namespace: String?
        get() = bundle.applicationId
        set(value) {
            bundle.applicationId = value
        }

    @WithLazyInitialization
    @Suppress("unused") // the call is injected by DslDecorator
    protected fun lazyInit() {
        buildToolsVersion = ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION.toString()
    }

}
