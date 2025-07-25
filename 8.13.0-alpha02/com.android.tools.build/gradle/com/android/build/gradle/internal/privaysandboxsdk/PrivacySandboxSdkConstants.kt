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

package com.android.build.gradle.internal.privaysandboxsdk

object PrivacySandboxSdkConstants {
    /**
     * Privacy sandbox doesn't have variants. However, some tasks e.g. lint may be required a variant
     * name to be provided. In this case, we have a default name to be used when this is required.
     */
    const val DEFAULT_VARIANT_NAME = "main"
    const val androidxPrivacySandboxActivityVersion = "1.0.0-alpha02"
    const val androidxPrivacySandboxVersion = "1.0.0-alpha13"
    const val androidxPrivacySandboxSdkRuntimeVersion = "1.0.0-alpha17"
    const val androidxPrivacySandboxSdkUiVersion = "1.0.0-alpha16"
    const val androidxPrivacySandboxLibraryPluginVersion = "1.0.0-alpha02"
}
