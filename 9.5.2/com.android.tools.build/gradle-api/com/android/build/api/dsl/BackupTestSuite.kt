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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * A test suite to configure and run Automated Backup and Restore tests.
 *
 * A [BackupTestSuite] configures the execution environment, JUnit 5 test engine, instrumentation runners, and dual source container
 * dependency graphs required to execute automated backup and restore tests on a target device or emulator.
 *
 * Automated Backup and Restore testing uses a hybrid orchestration model:
 * 1. **Host-side test orchestrator (configured via [hostJar])**: Executes on the host machine (JVM) using JUnit 5 (Jupiter), driving device
 *    state via ADB, triggering cloud or local backup passes, clearing app data, initiating restores, and verifying broadcast signals.
 * 2. **On-device test harness (configured via [testApk])**: Runs inside an instrumented Android environment to seed local storage state
 *    (SharedPreferences, Room/SQLite, files, Keystore) prior to backup, and asserts post-restore data integrity.
 *
 * Example usage:
 * ```
 * android {
 *     testOptions {
 *         backupTests {
 *             create("myBackupTest") {
 *                 backupTestLibraryVersion = "1.0.0-alpha01"
 *                 targetVariants.add("debug")
 *
 *                 hostJar {
 *                     dependencies {
 *                         implementation(project(":custom-backup-test-helpers"))
 *                         implementation("com.google.truth:truth:1.4.2")
 *                     }
 *                 }
 *                 testApk {
 *                     dependencies {
 *                         implementation("androidx.room:room-testing:2.6.1")
 *                     }
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
@Suppress("UnstableApiUsage")
@Incubating
interface BackupTestSuite {

  /**
   * The version of the automated backup test framework.
   *
   * Specifies the version of the `androidx.test.backup:backup-host` and `androidx.test.backup:backup` artifacts to automatically inject
   * into the test suite's host and device execution classpaths.
   *
   * This property is optional. If omitted, the Android Gradle Plugin will default to the latest compatible framework version.
   */
  @get:Incubating @set:Incubating var backupTestLibraryVersion: String?

  /**
   * Defines which build variants this test suite targets.
   *
   * If left empty, all buildable variants in the module will be tested.
   */
  @get:Incubating val targetVariants: MutableList<String>

  /**
   * Configures the host-side (JVM) test component for this test suite.
   *
   * Dependencies declared inside this block are added to the host JVM test execution classpath. This enables host-side tests to access
   * custom test helpers, assertion libraries (e.g., Google Truth), and mock frameworks.
   *
   * @param action Lambda to configure the [TestSuiteHostJarSpec].
   */
  @Incubating fun hostJar(action: TestSuiteHostJarSpec.() -> Unit)

  /**
   * Configures the on-device (APK) test component for this test suite.
   *
   * Dependencies declared inside this block are compiled into the on-device test helper APK. This enables on-device actions to access
   * application database schemas, Room testing utilities, and custom instrumentation runners.
   *
   * @param action Lambda to configure the [TestSuiteTestApkSpec].
   */
  @Incubating fun testApk(action: TestSuiteTestApkSpec.() -> Unit)
}
