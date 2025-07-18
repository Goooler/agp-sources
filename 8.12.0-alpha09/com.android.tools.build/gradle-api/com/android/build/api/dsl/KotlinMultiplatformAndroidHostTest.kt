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

package com.android.build.api.dsl

interface KotlinMultiplatformAndroidHostTest {
    /**
     * Whether unmocked methods from android.jar should throw exceptions or return default
     * values (i.e. zero or null).
     *
     * See [Test Your App](https://developer.android.com/studio/test/index.html) for details.
     */
    var isReturnDefaultValues: Boolean

    /**
     * Enables unit tests to use Android resources, assets, and manifests.
     *
     * If you set this property to <code>true</code>, the plugin performs resource, asset,
     * and manifest merging before running your unit tests. Your tests can then inspect a file
     * called `com/android/tools/test_config.properties` on the classpath, which is a Java
     * properties file with the following keys:
     *
     * `android_resource_apk`: the path to the APK-like zip file containing merged resources, which
     * includes all the resources from the current subproject and all its dependencies.
     * This property is available by default, or if the Gradle property
     * `android.enableUnitTestBinaryResources` is set to `true`.
     *
     * `android_merged_resources`: the path to the directory containing merged resources, which
     * includes all the resources from the current subproject and all its dependencies.
     * This property is available only if the Gradle property
     * `android.enableUnitTestBinaryResources` is set to `false`.
     *
     * `android_merged_assets`: the path to the directory containing merged assets. For app
     * subprojects, the merged assets directory contains assets from the current subproject and its
     * dependencies. For library subprojects, the merged assets directory contains only assets from
     * the current subproject.
     *
     * `android_merged_manifest`: the path to the merged manifest file. Only app subprojects have
     * the manifest merged from their dependencies. Library subprojects do not include manifest
     * components from their dependencies.
     *
     * `android_custom_package`: the package name of the final R class. If you modify the
     * application ID in your build scripts, this package name may not match the `package` attribute
     * in the final app manifest.
     *
     * Note that starting with version 3.5.0, if the Gradle property
     * `android.testConfig.useRelativePath` is set to `true`, the paths above will be relative paths
     * (relative to the current project directory, not the root project directory); otherwise,
     * they will be absolute paths. Prior to version 3.5.0, the paths are all absolute paths.
     */
    var isIncludeAndroidResources: Boolean

    /**
     * Specifies unit test code coverage data collection by configuring the JacocoPlugin.
     *
     * When enabled, the Jacoco plugin is applied and coverage data is collected
     * by the Jacoco plugin. This can avoid unwanted build time instrumentation required to collect
     * coverage data from other test types such as connected tests.
     */
    var enableCoverage: Boolean

    /**
     * Configures all aspects regarding target sdk for host tests, see [TargetSdkSpec] for available options.
     */
    fun targetSdk(action: TargetSdkSpec.() -> Unit)
}
