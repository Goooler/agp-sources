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
internal const val ATTR_MESSAGE = "message"

/** Keys for `<property>` tags within the report. */
internal const val KEY_MODULE_PATH = "modulePath"
internal const val KEY_TEST_SUITE_NAME = "testSuiteName"
internal const val KEY_TESTED_VARIANT_NAME = "testedVariantName"
internal const val KEY_TEST_TARGET = "testTarget"
internal const val KEY_DETAIL_PATH = "androidx.report.detailPath"
internal const val INTERNAL_REPORT_KEY_PREFIX = "androidx.report."
internal const val SCREENSHOT_PROPERTY_PREFIX = "PreviewScreenshot."
internal const val BACKUP_RESTORE_PROPERTY_PREFIX = "BackupRestore."

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

/** File and directory names for test report sidecars and XML results. */
internal const val EXT_XML = "xml"
internal const val EXT_JSON = "json"
internal const val JUNIT_XML_FILE_PREFIX = "TEST-"
internal const val SUITE_DETAILS_DIR_NAME = "suite-details"
internal const val SUITE_ENVIRONMENT_FILE_NAME = "suite-environment.json"
internal const val SUITE_ENVIRONMENT_FILE_PREFIX = "suite-environment_"

/** Suite report adapter identifiers and metadata. */
internal const val ADAPTER_ID_SCREENSHOT = "screenshot"
internal const val ADAPTER_ID_BACKUP_RESTORE = "backupRestore"
internal const val ADAPTER_ID_DEFAULT = "default"
internal const val SUITE_TYPE_BACKUP_RESTORE = "androidx.test.backup"
internal const val BACKUP_RESTORE_DOCS_URL = "https://developer.android.com/identity/data/testing"

/** Size caps for captured streams and inline log content. */
internal const val MAX_STREAM_CHARS = 262_144
internal const val MAX_INLINE_LOG_BYTES = 524_288

internal const val AGGREGATED_TEST_SUITE_NAME = "Aggregated"

const val UNKNOWN_TARGET = "unknown_target"
