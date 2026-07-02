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

package com.android.builder.merge

import com.android.zipflinger.ZipSource

interface FileMergerInputNonIncremental : FileMergerInput {

  /**
   * Opens a path as a ZipFlinger source. Open must be called first.
   *
   * @param path the path
   * @return the [com.android.zipflinger.Source] or [com.android.zipflinger.ZipSource] as resolved by the path
   */
  fun openAsZipSource(path: String): ZipSource
}
