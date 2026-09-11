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

package com.android.build.api.variant

import org.gradle.api.Incubating

/**
 * Build-time configuration for lint reports.
 *
 * This object is accessible on [LintBuilder] via [LintBuilder.reports].
 */
@Incubating
interface LintReportsBuilder {
  /**
   * Whether the local lint report task (which excludes dependencies) should be enabled for this variant.
   *
   * Only has an effect if [VariantBuilder.enableLint] is also true and the Gradle property android.experimental.lint.lintReportAggregation
   * is enabled.
   *
   * Defaults to `true`.
   */
  @get:Incubating @set:Incubating var enableReportWithoutDependencies: Boolean

  /**
   * Whether the lint report task that includes dependencies should be enabled for this variant.
   *
   * Only has an effect if [VariantBuilder.enableLint] is also true and the Gradle property android.experimental.lint.lintReportAggregation
   * is enabled.
   *
   * Defaults to `true`.
   */
  @get:Incubating @set:Incubating var enableReportWithDependencies: Boolean
}
