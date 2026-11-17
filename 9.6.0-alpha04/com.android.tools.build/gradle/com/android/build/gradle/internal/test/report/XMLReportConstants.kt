/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.test.report

/** XML element attributes found in test reports. */
internal const val ATTR_NAME = "name"
internal const val ATTR_VALUE = "value"
internal const val ATTR_CLASSNAME = "classname"
internal const val ATTR_TIME = "time"

/** Keys for generic `<property>` tags within the report. */
internal const val KEY_MODULE_PATH = "modulePath"
internal const val KEY_TEST_SUITE_NAME = "testSuiteName"
internal const val KEY_TESTED_VARIANT_NAME = "testedVariantName"
internal const val KEY_TEST_TARGET = "testTarget"
internal const val KEY_SUITE_TYPE = "suiteType"
internal const val KEY_APPLICATION_ID = "applicationId"
internal const val KEY_APPLICATION_NAME = "applicationName"
internal const val KEY_VERSION_NAME = "versionName"
internal const val KEY_VERSION_CODE = "versionCode"
internal const val KEY_TARGET_SDK_VERSION = "targetSdkVersion"
internal const val KEY_AGP_VERSION = "agpVersion"
internal const val KEY_GRADLE_VERSION = "gradleVersion"
internal const val KEY_TEST_LIBRARY_VERSION = "testLibraryVersion"
internal const val KEY_RUNNER = "runner"

internal const val INTERNAL_REPORT_KEY_PREFIX = "androidx.report."
internal const val KEY_REPORT_SCHEMA_VERSION = "${INTERNAL_REPORT_KEY_PREFIX}schemaVersion"
internal const val KEY_REPORT_SUITE_TYPE = "${INTERNAL_REPORT_KEY_PREFIX}suiteType"
internal const val KEY_REPORT_SOURCE = "${INTERNAL_REPORT_KEY_PREFIX}source"
internal const val REPORT_ATTR_PREFIX = "${INTERNAL_REPORT_KEY_PREFIX}attr."
internal const val REPORT_ACTOR_PREFIX = "${INTERNAL_REPORT_KEY_PREFIX}actor."
internal const val REPORT_STEP_PREFIX = "${INTERNAL_REPORT_KEY_PREFIX}step."
internal const val REPORT_CALLOUT_PREFIX = "${INTERNAL_REPORT_KEY_PREFIX}callout."
internal const val REPORT_ARTIFACT_PREFIX = "${INTERNAL_REPORT_KEY_PREFIX}artifact."
internal const val REPORT_FILE_PREFIX = "${INTERNAL_REPORT_KEY_PREFIX}file."

internal val BASE_SUITE_ENVIRONMENT_KEYS =
  setOf(
    KEY_SUITE_TYPE,
    KEY_APPLICATION_ID,
    KEY_APPLICATION_NAME,
    KEY_VERSION_NAME,
    KEY_VERSION_CODE,
    KEY_TARGET_SDK_VERSION,
    KEY_AGP_VERSION,
    KEY_GRADLE_VERSION,
    KEY_TEST_LIBRARY_VERSION,
    KEY_RUNNER,
  )

/** XML element tags found in test reports. */
internal const val TAG_TESTSUITE = "testsuite"
internal const val TAG_TESTSUITES = "testsuites"
internal const val TAG_PROPERTIES = "properties"
internal const val TAG_PROPERTY = "property"
internal const val TAG_TESTCASE = "testcase"
internal const val TAG_SKIPPED = "skipped"
internal const val TAG_FAILURE = "failure"
internal const val TAG_ERROR = "error"
internal const val TAG_SYSTEM_OUT = "system-out"
internal const val TAG_SYSTEM_ERR = "system-err"

/** Test result status values. */
internal const val STATUS_PASS = "pass"
internal const val STATUS_SKIPPED = "skipped"
internal const val STATUS_FAIL = "fail"

/** Test case names that JUnit runners use for class-level failures rather than real test methods. */
internal val SYNTHETIC_TEST_NAMES = setOf("initializationError", "executionError", "classMethod")

/** File and directory names for test report attachments and XML results. */
internal const val EXT_XML = "xml"
internal const val JUNIT_XML_FILE_PREFIX = "TEST-"
internal const val ATTACHMENTS_DIR_NAME = "attachments"
internal const val ATTACHMENT_MARKER_PREFIX = "[[ATTACHMENT|"
internal const val ATTACHMENT_MARKER_SUFFIX = "]]"

/** Fallback suite report adapter identifier. */
internal const val ADAPTER_ID_DEFAULT = "default"

/** Size caps for captured streams and inline log content. */
internal const val MAX_STREAM_CHARS = 262_144
internal const val MAX_INLINE_LOG_BYTES = 524_288

/** Name of the Markdown test report written next to the HTML report's `index.html`. */
const val MARKDOWN_REPORT_FILE_NAME = "report.md"

internal const val AGGREGATED_TEST_SUITE_NAME = "Aggregated"

const val UNKNOWN_TARGET = "unknown_target"
