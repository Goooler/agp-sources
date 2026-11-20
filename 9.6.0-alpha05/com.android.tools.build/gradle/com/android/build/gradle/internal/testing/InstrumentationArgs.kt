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

package com.android.build.gradle.internal.testing

// Input properties of android-test-engine. Keep in sync with `AndroidTestConfigurationKeys` and `AgpTestSuiteInput` in the engine.
internal const val INSTRUMENTATION_ARGS_KEY = "android-test.instrumentation-args"
internal const val INSTRUMENTATION_RUNNER_CLASS_KEY = "android-test.instrumentation-runner-class"
internal const val TEST_PACKAGE_ID_KEY = "android-test.test-package-id"
internal const val INSTRUMENTATION_TARGET_PACKAGE_ID_KEY = "android-test.instrumentation-target-package-id"
internal const val UNINSTALL_AFTER_TESTS_KEY = "android-test.uninstall-after-tests"
internal const val USE_TEST_STORAGE_SERVICE_KEY = "android-test.use-test-storage-service"
internal const val TEST_COVERAGE_ENABLED_KEY = "android-test.is-test-coverage-enabled"
internal const val COVERAGE_FILE_ON_DEVICE_KEY = "android-test.coverage-file-on-device"
internal const val COVERAGE_DIR_ON_DEVICE_KEY = "android-test.coverage-dir-on-device"
internal const val COVERAGE_TYPE_KEY = "com.android.agp.test.COVERAGE_TYPE"

/**
 * Formats instrumentation runner arguments as comma-separated `key=value` entries for [INSTRUMENTATION_ARGS_KEY], escaping `,` and `=` in
 * keys, `,` in values, and `\` when followed by `\`, `,`, or `=`, or at the end of a key or value. Escaping `\` only before delimiters (or
 * at the end) keeps comma-free values such as `tests_regex` unchanged and readable by older engines.
 *
 * Keep in sync with `AndroidTestConfiguration.parseInstrumentationArgs` in
 * `com.android.tools.androidtest.testengine.config.AndroidTestConfiguration`.
 */
internal fun formatInstrumentationArgs(args: Map<String, String>): String =
  args.entries.joinToString(",") { (k, v) -> "${escapeInstrumentationArgKey(k)}=${escapeInstrumentationArgValue(v)}" }

private fun escapeBackslashes(value: String): String =
  buildString(value.length) {
    value.forEachIndexed { index, c ->
      append(c)
      if (c == '\\' && (index == value.lastIndex || value[index + 1] in "\\,=")) {
        append('\\')
      }
    }
  }

private fun escapeInstrumentationArgKey(key: String): String = escapeBackslashes(key).replace(",", "\\,").replace("=", "\\=")

private fun escapeInstrumentationArgValue(value: String): String = escapeBackslashes(value).replace(",", "\\,")
