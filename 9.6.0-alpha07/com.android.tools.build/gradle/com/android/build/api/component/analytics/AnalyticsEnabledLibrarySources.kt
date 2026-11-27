/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.build.api.variant.LibrarySources
import com.android.build.api.variant.SourceDirectories
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import javax.inject.Inject
import org.gradle.api.model.ObjectFactory

open class AnalyticsEnabledLibrarySources
@Inject
constructor(delegate: LibrarySources, stats: GradleBuildVariant.Builder, objectFactory: ObjectFactory) :
  AnalyticsEnabledSources<LibrarySources>(delegate, stats, objectFactory), LibrarySources {

  override val aarKeepRules: SourceDirectories.Flat?
    get() =
      delegate.aarKeepRules?.let {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
          VariantPropertiesMethodType.SOURCES_AAR_KEEP_RULES_ACCESS_VALUE
        return objectFactory.newInstance(AnalyticsEnabledFlat::class.java, it, stats, objectFactory)
      }
}
