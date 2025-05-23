/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.api.component.analytics

import com.android.build.api.variant.LifecycleTasks
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import javax.inject.Inject

open class AnalyticsEnabledLifecycleTasks @Inject constructor(
    open val delegate: LifecycleTasks,
    val stats: GradleBuildVariant.Builder,
): LifecycleTasks {

    override fun registerPreBuild(vararg objects: Any) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.REGISTER_PRE_BUILD_VALUE
        delegate.registerPreBuild(*objects)
    }

    override fun registerPreInstallation(vararg objects: Any) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.REGISTER_APK_INSTALLATION_VALUE
        delegate.registerPreInstallation(*objects)
    }
}
