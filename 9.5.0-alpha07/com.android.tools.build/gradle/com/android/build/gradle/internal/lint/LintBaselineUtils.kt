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

package com.android.build.gradle.internal.lint

import java.io.File

/**
 * The default baseline file name for standard (non-KMP) projects (`lint-baseline.xml`).
 *
 * In Kotlin Multiplatform projects, this also represents the legacy non-target-specific baseline file used as a fallback for backward
 * compatibility when target-specific baselines do not exist.
 */
const val LINT_BASELINE_FILE_NAME = "lint-baseline.xml"

/** The default target-specific baseline file name for Android targets in Kotlin Multiplatform projects (`lint-baseline-android.xml`). */
const val LINT_BASELINE_ANDROID_FILE_NAME = "lint-baseline-android.xml"

/** The default target-specific baseline file name for JVM targets in Kotlin Multiplatform projects (`lint-baseline-jvm.xml`). */
const val LINT_BASELINE_JVM_FILE_NAME = "lint-baseline-jvm.xml"

/**
 * Resolves the baseline file to use for lint reporting or model building.
 *
 * If a target-specific baseline is configured (e.g. `lint-baseline-android.xml` or `lint-baseline-jvm.xml`), it will be used if it exists.
 * If it does not exist, and no other target-specific baseline exists, but a legacy `lint-baseline.xml` file exists, it falls back to the
 * legacy baseline for backward compatibility. If any other target-specific baseline exists, the project has already migrated to
 * target-specific baselines, so it does not fall back to the stale legacy baseline.
 */
internal fun resolveBaselineFile(targetBaseline: File, legacyBaseline: File?): File {
  if ((legacyBaseline != null) && (targetBaseline.name != LINT_BASELINE_FILE_NAME)) {
    val projectDir = targetBaseline.parentFile
    val hasOtherTargetBaseline =
      projectDir
        ?.listFiles { _, name ->
          name.startsWith("lint-baseline-") && name.endsWith(".xml") && (name != targetBaseline.name)
        }
        ?.isNotEmpty() == true
    if (!targetBaseline.exists() && !hasOtherTargetBaseline && legacyBaseline.exists()) {
      return legacyBaseline
    }
  }
  return targetBaseline
}
