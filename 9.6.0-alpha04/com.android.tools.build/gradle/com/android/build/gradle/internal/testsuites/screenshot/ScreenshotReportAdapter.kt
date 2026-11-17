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

package com.android.build.gradle.internal.testsuites.screenshot

import com.android.build.gradle.internal.test.report.AdaptedTestCase
import com.android.build.gradle.internal.test.report.STATUS_PASS
import com.android.build.gradle.internal.test.report.SuiteAdapterContext
import com.android.build.gradle.internal.test.report.SuiteDescriptor
import com.android.build.gradle.internal.test.report.SuiteReportAdapter
import com.android.build.gradle.internal.test.report.VariantTestResult
import com.android.build.gradle.internal.test.report.declaredSuiteType
import com.android.build.gradle.internal.test.report.extractDetailRecordFields
import com.android.build.gradle.internal.test.report.filterUserProperties

internal const val ADAPTER_ID_SCREENSHOT = "screenshot"
internal const val SCREENSHOT_PROPERTY_PREFIX = "PreviewScreenshot."

/** Adapts Compose Preview Screenshot test results for the HTML report. */
internal object ScreenshotReportAdapter : SuiteReportAdapter {
  override val adapterId: String = ADAPTER_ID_SCREENSHOT
  override val suiteOwnedPropertyPrefixes: Set<String> = setOf(SCREENSHOT_PROPERTY_PREFIX)

  private const val KEY_DIFF_PERCENT = "PreviewScreenshot.diffPercent"
  private const val KEY_PREVIEW_NAME = "PreviewScreenshot.previewName"
  private const val KEY_METHOD_NAME = "PreviewScreenshot.methodName"
  private const val KEY_REF_IMAGE_PATH = "PreviewScreenshot.refImagePath"
  private const val KEY_NEW_IMAGE_PATH = "PreviewScreenshot.newImagePath"
  private const val KEY_DIFF_IMAGE_PATH = "PreviewScreenshot.diffImagePath"

  override fun matches(context: SuiteAdapterContext): Boolean = context.rawProperties.keys.any { it.startsWith(SCREENSHOT_PROPERTY_PREFIX) }

  override fun adapt(context: SuiteAdapterContext): AdaptedTestCase {
    val common = extractDetailRecordFields(context.detailRecord)
    val filteredProps =
      filterUserProperties(context.rawProperties) { key ->
        !key.startsWith(SCREENSHOT_PROPERTY_PREFIX)
      }
    val suiteType = context.declaredSuiteType
    val descriptor =
      SuiteDescriptor(
        adapterId = ADAPTER_ID_SCREENSHOT,
        suiteType = suiteType,
        summaryColumns = common.dynamicColumns,
      )

    return AdaptedTestCase(
      result =
        VariantTestResult(
          status = STATUS_PASS,
          adapterId = ADAPTER_ID_SCREENSHOT,
          suiteType = suiteType,
          diffPercent = context.rawProperties[KEY_DIFF_PERCENT],
          previewName = context.rawProperties[KEY_PREVIEW_NAME],
          methodName = context.rawProperties[KEY_METHOD_NAME],
          refImagePath = context.rawProperties[KEY_REF_IMAGE_PATH],
          newImagePath = context.rawProperties[KEY_NEW_IMAGE_PATH],
          diffImagePath = context.rawProperties[KEY_DIFF_IMAGE_PATH],
          properties = filteredProps,
          summaryAttributes = common.summaryAttributes,
          sourceLocation = common.sourceLocation,
          callout = common.callout,
          actors = common.actors,
          steps = common.steps,
        ),
      suiteDescriptor = descriptor,
    )
  }
}
