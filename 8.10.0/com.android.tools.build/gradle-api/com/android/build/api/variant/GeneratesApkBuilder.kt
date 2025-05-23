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

package com.android.build.api.variant

import org.gradle.api.Incubating

/**
 * Configuration-time properties [ComponentBuilder] that produce APKs.
 */
interface GeneratesApkBuilder {

    /**
     * Sets the APK debuggable flag.
     */
    @get:Deprecated(
        message="Other plugins can change this value, it is not safe to read it at this stage",
        level = DeprecationLevel.ERROR
    )
    var debuggable: Boolean

    /**
     * Sets the target SDK Version for this variant as an integer API level.
     * Setting this will override previous calls of [targetSdk] and [targetSdkPreview] setters.
     * Only one of [targetSdk] and [targetSdkPreview] should be set.
     *
     * It is not safe to read this value. Use [GeneratesApk.targetSdk] instead.
     */
    var targetSdk: Int?

    /**
     * Sets the target SDK Version for this variant as an integer API level.
     * Setting this will override previous calls of [targetSdk] and [targetSdkPreview] setters.
     * Only one of [targetSdk] and [targetSdkPreview] should be set.
     *
     * It is not safe to read this value. Use [GeneratesApk.targetSdk] instead.
     */
    var targetSdkPreview: String?

    /**
     * Sets whether multi-dex is enabled for this variant.
     *
     * This can be null, in which case the default value is used.
     *
     * It is not safe to read the value of this property as other plugins that were applied
     * later can change this value so there is no guarantee you would get the final value.
     * To get the final value, use the [AndroidComponentsExtension.onVariants] API :
     * ```kotlin
     * onVariants { variant ->
     *   variant.dexing.isMultiDexEnabled
     * }
     * ```
     * Note the a [RuntimeException] will be thrown at Runtime if a java or groovy code tries
     * to read the property value.
     */
    @get:Incubating
    @get:Deprecated(
        message="Other plugins can change this value, it is not safe to read it at this stage",
        level = DeprecationLevel.ERROR
    )
    @set:Incubating
    var enableMultiDex: Boolean?
}
