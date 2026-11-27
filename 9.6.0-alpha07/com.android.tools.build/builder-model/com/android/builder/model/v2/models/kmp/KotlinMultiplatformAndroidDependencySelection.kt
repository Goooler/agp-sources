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

package com.android.builder.model.v2.models.kmp

import com.android.builder.model.v2.AndroidModel

/**
 * Lightweight configuration-time model exposing `localDependencySelection` preferences for an Android Kotlin Multiplatform library project
 * (`com.android.kotlin.multiplatform.library`).
 *
 * This model does not trigger dependency resolution and can be queried early during Gradle Sync (for example, to propagate variant
 * selection across multi-variant Android dependencies).
 */
interface KotlinMultiplatformAndroidDependencySelection : AndroidModel {

  /**
   * Ordered list of build type names configured via `localDependencySelection.selectBuildTypeFrom` that this project tries to select when
   * consuming multi-variant Android dependencies.
   */
  val selectBuildTypeFrom: List<String>

  /**
   * Map of product flavor dimension name to an ordered list of flavor names configured via
   * `localDependencySelection.productFlavorDimension` that this project tries to select when consuming multi-variant Android dependencies.
   */
  val productFlavorSelection: Map<String, List<String>>
}
