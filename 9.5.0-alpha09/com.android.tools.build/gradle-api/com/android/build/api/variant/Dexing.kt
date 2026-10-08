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

package com.android.build.api.variant

import org.gradle.api.Incubating
import org.gradle.api.file.RegularFileProperty

/**
 * Settings related to how dex files are produced.
 *
 * To change any of these settings :
 * ```kotlin
 * androidComponents {
 *     beforeVariants { variant ->
 *         variant.dexing...
 *     }
 * }
 * ```
 */
@Incubating
interface Dexing {

  /**
   * @deprecated Legacy multidex (minSdk <= 19) is deprecated in AGP 9.4 and will be unsupported in AGP 11. Native multidex is always
   *   supported for minSdk 21+.
   */
  @Deprecated(
    message =
      "Legacy multidex (minSdk <= 19) is deprecated in AGP 9.4 and will be unsupported in AGP 11. Native multidex is always supported for minSdk 21+."
  )
  val isMultiDexEnabled: Boolean

  /**
   * If set, will point to the multiDex proguard file
   *
   * Note that the [GeneratesApkBuilder.enableMultiDex] must be set to true for this property to be used.
   *
   * @deprecated Legacy multidex (minSdk <= 19) is deprecated in AGP 9.4 and will be unsupported in AGP 11. Native multidex is always
   *   supported for minSdk 21+ and does not require main dex class configuration.
   */
  @Deprecated(
    message =
      "Legacy multidex (minSdk <= 19) is deprecated in AGP 9.4 and will be unsupported in AGP 11. Native multidex is always supported for minSdk 21+ and does not require main dex class configuration."
  )
  val multiDexKeepProguard: RegularFileProperty

  /**
   * If set, will point to a text file that specifies additional classes that will be compiled into the main dex file.
   *
   * Note that the [GeneratesApkBuilder.enableMultiDex] must be set to true for this property to be used.
   *
   * @deprecated Legacy multidex (minSdk <= 19) is deprecated in AGP 9.4 and will be unsupported in AGP 11. Native multidex is always
   *   supported for minSdk 21+ and does not require main dex class configuration.
   */
  @Deprecated(
    message =
      "Legacy multidex (minSdk <= 19) is deprecated in AGP 9.4 and will be unsupported in AGP 11. Native multidex is always supported for minSdk 21+ and does not require main dex class configuration."
  )
  val multiDexKeepFile: RegularFileProperty
}
