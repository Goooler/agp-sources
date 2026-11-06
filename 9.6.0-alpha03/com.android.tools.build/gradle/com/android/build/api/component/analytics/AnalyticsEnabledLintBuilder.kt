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

import com.android.build.api.variant.LintBuilder
import com.android.build.api.variant.LintReportsBuilder
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import javax.inject.Inject

open class AnalyticsEnabledLintBuilder @Inject constructor(val delegate: LintBuilder, val stats: GradleBuildVariant.Builder) : LintBuilder {

  override val reports: LintReportsBuilder
    get() {
      return AnalyticsEnabledLintReportsBuilder(delegate.reports, stats)
    }
}

open class AnalyticsEnabledLintReportsBuilder(val delegate: LintReportsBuilder, val stats: GradleBuildVariant.Builder) :
  LintReportsBuilder {

  override var enableReportWithoutDependencies: Boolean
    get() = delegate.enableReportWithoutDependencies
    set(value) {
      stats.variantApiAccessBuilder.addVariantAccessBuilder().type = VariantMethodType.LINT_REPORTS_ENABLE_REPORT_WITHOUT_DEPENDENCIES_VALUE
      delegate.enableReportWithoutDependencies = value
    }

  override var enableReportWithDependencies: Boolean
    get() = delegate.enableReportWithDependencies
    set(value) {
      stats.variantApiAccessBuilder.addVariantAccessBuilder().type = VariantMethodType.LINT_REPORTS_ENABLE_REPORT_WITH_DEPENDENCIES_VALUE
      delegate.enableReportWithDependencies = value
    }
}
