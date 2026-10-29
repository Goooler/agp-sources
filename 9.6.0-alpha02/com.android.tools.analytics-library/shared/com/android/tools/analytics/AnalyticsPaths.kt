/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.analytics

import com.android.prefs.AbstractAndroidLocations
import com.android.utils.NullLogger
import java.io.File
import java.nio.file.Paths

/** Helpers to get paths used to configure analytics reporting. */
object AnalyticsPaths {

  private var androidSettingsHomeDirectoryOverride: String? = null
  private var androidSettingsSpoolDirectoryOverride: String? = null

  /** Overrides the android settings home directory for tests */
  fun overrideAndroidSettingsHomeDirectory(directory: String) {
    androidSettingsHomeDirectoryOverride = directory
  }

  /** Clears the override of the android settings home directory */
  fun restoreAndroidSettingsHomeDirectory() {
    androidSettingsHomeDirectoryOverride = null
  }

  /** Overrides the android settings spool directory */
  @JvmStatic
  fun overrideAndroidSettingsSpoolDirectory(directory: String) {
    androidSettingsSpoolDirectoryOverride = directory
  }

  @JvmStatic
  fun restoreAndroidSettingsSpoolDirectory() {
    androidSettingsSpoolDirectoryOverride = null
  }

  /** Gets the spooling directory used for temporary storage of analytics data. */
  @JvmStatic
  val spoolDirectory: String
    get() {
      androidSettingsSpoolDirectoryOverride?.let {
        return it
      }
      return Paths.get(getAndEnsureAndroidSettingsHome(), "metrics", "spool").toString()
    }

  /** Gets the directory used to store android related settings (usually ~/.android). */
  @JvmStatic
  fun getAndEnsureAndroidSettingsHome(): String {
    val prefsRoot = getAndroidSettingsHome()

    File(prefsRoot).mkdirs()
    return prefsRoot
  }

  @JvmStatic
  private fun getAndroidSettingsHome(): String {
    androidSettingsHomeDirectoryOverride?.let {
      return it
    }
    return AndroidLocations().computeAndroidFolder().toString()
  }

  private class AndroidLocations :
    AbstractAndroidLocations(environmentProvider = Environment.instance, logger = NullLogger.getLogger(), silent = true)
}
