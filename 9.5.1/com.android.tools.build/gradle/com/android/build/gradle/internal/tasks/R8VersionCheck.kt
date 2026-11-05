/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.dependency.ShrinkerVersion
import com.android.builder.dexing.R8Version
import com.android.builder.errors.IssueReporter

/**
 * Emits an [IssueReporter.Type.R8_VERSION_MISMATCH] sync warning if the configured or classpath R8 version is older than
 * [R8Version.VERSION_AGP_WAS_SHIPPED_WITH].
 */
fun checkIfR8VersionMatches(issueReporter: IssueReporter, currentR8Version: String? = null) {
  // Compiler inlines constants, so this retrieves R8 version at compile time (from AGP).
  // This may differ from the R8 version available at runtime.
  val versionAgpWasShippedWith = ShrinkerVersion.tryParse(R8Version.VERSION_AGP_WAS_SHIPPED_WITH) ?: return

  // Check dynamically configured R8 version from r8FromMaven (if provided and older)
  if (currentR8Version != null) {
    val configuredVersion = ShrinkerVersion.tryParse(currentR8Version)
    if (configuredVersion != null && configuredVersion < versionAgpWasShippedWith) {
      issueReporter.reportWarning(
        IssueReporter.Type.R8_VERSION_MISMATCH,
        R8VersionCheckException(versionAgpWasShippedWith, configuredVersion),
      )
      return
    }
  }

  // Check R8 version present on the buildscript / runtime classpath
  try {
    val versionInClasspath = ShrinkerVersion.tryParse(R8Version.getVersionString()) ?: return
    if (versionInClasspath < versionAgpWasShippedWith) {
      issueReporter.reportWarning(
        IssueReporter.Type.R8_VERSION_MISMATCH,
        R8VersionCheckException(versionAgpWasShippedWith, versionInClasspath),
      )
    }
  } catch (e: NoSuchMethodError) {
    issueReporter.reportWarning(IssueReporter.Type.R8_VERSION_MISMATCH, R8VersionCheckException())
  }
}

class R8VersionCheckException(minimumRequired: ShrinkerVersion? = null, foundVersion: ShrinkerVersion? = null) :
  Exception(
    "Your project includes ${foundVersion?.asString()?.let { "version $it" } ?: "an old version"} of R8, " +
      "while Android Gradle Plugin was shipped with ${minimumRequired?.asString() ?: "a newer one"}. " +
      "This can lead to unexpected issues."
  )
