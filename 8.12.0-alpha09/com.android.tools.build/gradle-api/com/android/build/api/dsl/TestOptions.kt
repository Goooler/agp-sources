/*
 * Copyright (C) 2019 The Android Open Source Project
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

import org.gradle.api.Action
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer

/** Options for running tests. */
interface TestOptions {
    /** Options for controlling unit tests execution. */
    val unitTests: UnitTestOptions

    /** Options for controlling unit tests execution. */
    fun unitTests(action: UnitTestOptions.() -> Unit)

    /** Name of the results' directory. */
    var resultsDir: String?

    /** Name of the reports' directory. */
    var reportDir: String?

    /**
     * Disables animations during instrumented tests you run from the command line.
     *
     * If you set this property to `true`, running instrumented tests with Gradle from the command
     * line executes `am instrument` with the `--no-window-animation` flag.
     * By default, this property is set to `false`.
     *
     * This property does not affect tests that you run using Android Studio. To learn more about
     * running tests from the command line, see
     * [Test from the Command Line](https://d.android.com/studio/test/command-line.html).
     */
    var animationsDisabled: Boolean

    /**
     * List of test devices for this project for use with the Unified Test Platform
     *
     * These APIs are experimental and may change without notice.
     *
     * [devices] is deprecated, use [managedDevices.devices] to specify Gradle Managed Devices
     */
    @Deprecated("devices is deprecated in testOptions, use managedDevices.devices instead")
    @get:Incubating
    val devices: ExtensiblePolymorphicDomainObjectContainer<Device>

    /**
     * List of DeviceGroups that can be run through connected check, using the Unified Test
     * Platform.
     *
     * DeviceGroups with individual devices are added automatically, with the same name of the
     * individual device.
     *
     * These APIs are experimental and may change without notice.
     *
     * [deviceGroups] is deprecated, use [managedDevices.groups] to specify groups of
     * Gradle Managed Devices.
     */
    @Deprecated(
        "deviceGroups is deprecated in testOptions, use managedDevices.groups instead"
    )
    @get:Incubating
    val deviceGroups: NamedDomainObjectContainer<DeviceGroup>

    /**
     * Configures Gradle Managed Devices for use in testing with the Unified test platform.
     */
    @get:Incubating
    val managedDevices: ManagedDevices

    /**
     * Configures Gradle Managed Devices for use in testing with the Unified test platform.
     */
    @Incubating
    fun managedDevices(action: ManagedDevices.() -> Unit)

    /**
     * Specifies whether to use on-device test orchestration.
     *
     * If you want to [use Android Test Orchestrator](https://developer.android.com/training/testing/junit-runner.html#using-android-test-orchestrator)
     * you need to specify `"ANDROID_TEST_ORCHESTRATOR"`, as shown below.
     * By default, this property is set to `"HOST"`, which disables on-device orchestration.
     *
     * ```
     * android {
     *   testOptions {
     *     execution 'ANDROID_TEST_ORCHESTRATOR'
     *   }
     * }
     * ```
     *
     * since 3.0.0
     */
    var execution: String

    /**
     * Configures Android Emulator Grpc Access
     *
     * Android Emulator Grpc Access will make it possible to interact with the emulator over gRPC
     *
     * ```
     * android {
     *     emulatorControl {
     *       enable true
     *       secondsValid 180
     *       allowedEndpoints.addAll(
     *           "/android.emulation.control.EmulatorController/getStatus",
     *           "/android.emulation.control.EmulatorController/getVmState")
     *     }
     * }
     * ```
     */
    @get:Incubating
    val emulatorControl: EmulatorControl

    @Incubating
    fun emulatorControl(action: EmulatorControl.() -> Unit)

    /**
     * Configures Android Test Retention.
     *
     * Android Test Retention automatically takes emulator snapshots on test failures. It can only
     * work with Unified Test Platform (UTP).
     *
     * ```
     * android {
     *   testOptions {
     *     emulatorSnapshots {
     *       enableForTestFailures true
     *       maxSnapshotsForTestFailures 2
     *       compressSnapshots false
     *     }
     *   }
     * }
     * ```
     */
    /** @suppress */
    @get:Incubating
    @Deprecated("This feature is no longer developed and supported. API will be removed in AGP 9.0")
    val emulatorSnapshots: EmulatorSnapshots

    /** @suppress */
    @Incubating
    @Deprecated("This feature is no longer developed and supported. API will be removed in AGP 9.0")
    fun emulatorSnapshots(action: EmulatorSnapshots.() -> Unit)

    /** @suppress */
    @get:Incubating
    @Deprecated("This feature is no longer developed and supported. API will be removed in AGP 9.0")
    val failureRetention: FailureRetention

    /** @suppress */
    @Incubating
    @Deprecated("This feature is no longer developed and supported. API will be removed in AGP 9.0")
    fun failureRetention(action: FailureRetention.() -> Unit)

    /**
     * Specifies value that overrides target sdk version number for tests in libraries.
     * Default value is set to minSdk.
     * Important: Setting this value will cause an error for application and other module types.
     */
    var targetSdk: Int?

    /**
     * Specifies value that overrides target sdk preview number for tests in libraries.
     * Default value is empty.
     * Important: Setting this value will cause an error for application and other module types.
     */
    var targetSdkPreview: String?

    /**
     * Available test suites in this project.
     *
     * Test suites provide a way to define groups of tests that can be executed together.  Each
     * [AgpTestSuite] returned by this method will run against the variants identified by the
     * associated [AgpTestSuite.getTargets] targets.
     *
     * This differs from [unitTests], which uses dedicated source set folders for variant-specific
     * tests.  With [AgpTestSuite], you can create multiple suites to achieve variant-specific testing,
     * with each suite targeting specific variants or product flavors.
     *
     * TODO: Update example when hostTest vs deviceTest is surfaced in a subsequent CL.
     * For example, if your module has "red" and "blue" product flavors, you could create three test
     * suites:
     * ```
     * android {
     *     testOptions {
     *         suites {
     *             create("commonSuite") { ... }
     *             create("redSuite") { ... }
     *             create("blueSuite") { ... }
     *         }
     *     }
     * }
     * ```
     * see [AgpTestSuite] for details  on how to target each test suite to tested variants using the
     * [AgpTestSuite.targetVariants] API.
     *
     * Note: The built-in test suites "unitTests" and "androidTests" are not accessible through this
     * API. Attempting to create test suites with these reserved names will result in a configuration-time
     * exception.
     *
     * The types of test suites available depend on the other plugins applied to your project.
     */
    /** @suppress */
    @get:Incubating
    val suites: ExtensiblePolymorphicDomainObjectContainer<AgpTestSuite>
}
