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

package com.android.build.gradle.internal.plugins

import com.android.build.api.dsl.Lint

internal fun Lint.applySettings(settingsLint: Lint) {
  abortOnError = settingsLint.abortOnError
  absolutePaths = settingsLint.absolutePaths
  checkAllWarnings = settingsLint.checkAllWarnings
  checkDependencies = settingsLint.checkDependencies
  checkGeneratedSources = settingsLint.checkGeneratedSources
  checkReleaseBuilds = settingsLint.checkReleaseBuilds
  checkTestSources = settingsLint.checkTestSources
  explainIssues = settingsLint.explainIssues
  htmlReport = settingsLint.htmlReport
  ignoreTestFixturesSources = settingsLint.ignoreTestFixturesSources
  ignoreTestSources = settingsLint.ignoreTestSources
  ignoreWarnings = settingsLint.ignoreWarnings
  noLines = settingsLint.noLines
  quiet = settingsLint.quiet
  sarifReport = settingsLint.sarifReport
  showAll = settingsLint.showAll
  textReport = settingsLint.textReport
  warningsAsErrors = settingsLint.warningsAsErrors
  xmlReport = settingsLint.xmlReport

  checkOnly.addAll(settingsLint.checkOnly)
  disable.addAll(settingsLint.disable)
  enable.addAll(settingsLint.enable)
  error.addAll(settingsLint.error)
  fatal.addAll(settingsLint.fatal)
  informational.addAll(settingsLint.informational)
  warning.addAll(settingsLint.warning)

  settingsLint.baseline?.let { baseline = it }
  settingsLint.lintConfig?.let { lintConfig = it }

  settingsLint.targetSdk?.let { targetSdk = it }
  settingsLint.targetSdkPreview?.let { targetSdkPreview = it }
}
