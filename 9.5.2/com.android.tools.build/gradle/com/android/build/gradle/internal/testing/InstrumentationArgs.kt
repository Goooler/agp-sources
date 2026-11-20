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

internal const val INSTRUMENTATION_ARGS_KEY = "android-test.instrumentation-args"

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
