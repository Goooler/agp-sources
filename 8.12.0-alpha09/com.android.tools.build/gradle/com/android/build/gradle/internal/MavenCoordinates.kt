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

package com.android.build.gradle.internal

import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkConstants.androidxPrivacySandboxActivityVersion
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkConstants.androidxPrivacySandboxSdkUiVersion
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkConstants.androidxPrivacySandboxVersion

/**
 * Enums representing Maven coordinates that have usages
 */
enum class MavenCoordinates (val group: String, val artifact: String,  val defaultVersion: String) {
    ANDROIDX_PRIVACYSANDBOX_ACTIVITY_ACTIVITY_CORE(
        "androidx.privacysandbox.activity",
        "activity-core",
        androidxPrivacySandboxActivityVersion
    ),
    ANDROIDX_PRIVACYSANDBOX_ACTIVITY_ACTIVITY_PROVIDER(
        "androidx.privacysandbox.activity",
        "activity-provider",
        androidxPrivacySandboxActivityVersion
    ),
    ANDROIDX_PRIVACYSANDBOX_ACTIVITY_ACTIVITY_CLIENT(
        "androidx.privacysandbox.activity",
        "activity-client",
        androidxPrivacySandboxActivityVersion
    ),
    ANDROIDX_PRIVACYSANDBOX_TOOLS_TOOLS_APIGENERATOR(
        "androidx.privacysandbox.tools",
        "tools-apigenerator",
        androidxPrivacySandboxVersion
    ),
    ANDROIDX_PRIVACYSANDBOX_TOOLS_TOOLS_APIPACKAGER(
        "androidx.privacysandbox.tools",
        "tools-apipackager",
        androidxPrivacySandboxVersion
    ),
    ANDROIDX_PRIVACYSANDBOX_UI_UI_CORE(
        "androidx.privacysandbox.ui",
        "ui-core",
        androidxPrivacySandboxSdkUiVersion
    ),
    ANDROIDX_CORE_CORE_KTX(
        "androidx.core",
        "core-ktx",
        "1.13.0"
    ),
    ANDROIDX_PRIVACYSANDBOX_UI_UI_CLIENT(
        "androidx.privacysandbox.ui",
        "ui-client",
        androidxPrivacySandboxSdkUiVersion
    ),
    ORG_JETBRAINS_KOTLIN_KOTLIN_COMPILER_EMBEDDABLE(
        "org.jetbrains.kotlin",
        "kotlin-compiler-embeddable",
        "2.1.20"
    ),
    ORG_JETBRAINS_KOTLIN_KOTLIN_STDLIB(
        "org.jetbrains.kotlin",
        "kotlin-stdlib",
        "1.7.20-RC"
    ),
    ORG_JETBRAINS_KOTLINX_KOTLINX_COROUTINES_ANDROID(
    "org.jetbrains.kotlinx",
    "kotlinx-coroutines-android",
    "1.7.1"
    )
    ;

    fun withVersion(newVersion: String): String = "$group:$artifact:$newVersion"
    override fun toString(): String = "$group:$artifact:$defaultVersion"

}
