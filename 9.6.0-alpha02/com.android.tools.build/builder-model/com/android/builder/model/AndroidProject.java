/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.builder.model;

/**
 * Entry point for the model of the Android Projects. This models a single module, whether the
 * module is an app project, a library project, a Instant App feature project, an instantApp bundle
 * project, or a dynamic feature split project.
 */
public interface AndroidProject {
    // **************************************************************
    // **************************************************************
    // DO *NOT* ADD NEW VALUES HERE.
    // ADD VALUES TO InjectedProperties.kt only.
    //
    // Properties in this file are related to model v1 only.
    // **************************************************************
    // **************************************************************

    //  Injectable properties to use with -P
    // Sent by Studio 1.0 ONLY
    String PROPERTY_BUILD_MODEL_ONLY = "android.injected.build.model.only";
    // Sent by Studio 1.1+
    String PROPERTY_BUILD_MODEL_ONLY_ADVANCED = "android.injected.build.model.only.advanced";
    // Sent by Studio 2.4+. The value of the prop is a monotonically increasing integer.
    // see MODEL_LEVEL_* constants
    String PROPERTY_BUILD_MODEL_ONLY_VERSIONED = "android.injected.build.model.only.versioned";
    // Sent by Studio 2.4+. Additional model feature trigger on a case by case basis
    // Value is simply true to enable.
    String PROPERTY_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES =
            "android.injected.build.model.feature.full.dependencies";

    /**
     * Was a property to disable source download during model sync, which is no longer done by AGP.
     *
     * @deprecated Only has effect in AGP 3.5. Android Studio 3.6 onwards downloads sources using an
     *     injected gradle tooling model and model builder.
     */
    @Deprecated
    String PROPERTY_BUILD_MODEL_DISABLE_SRC_DOWNLOAD =
            "android.injected.build.model.disable.src.download";

    // Studio injects this in order to allow 3P tooling to get the Studio version used to launch
    // build/sync, see b/280831521.
    String PROPERTY_ANDROID_STUDIO_VERSION = "android.studio.version";

    // deprecated. Kept here so that newer Studio can still inject it for older plugin
    // but newer plugin don't do anything different based on this property.
    @SuppressWarnings("unused")
    String PROPERTY_GENERATE_SOURCES_ONLY = "android.injected.generateSourcesOnly";

    /**
     * Used to ask AGP to avoid task registration (if possible) during sync, in order to improve
     * sync performance. See http://b/303832930 for details.
     */
    String PROPERTY_AVOID_TASK_REGISTRATION = "android.injected.avoid.task.registration";

    String ARTIFACT_MAIN = "_main_";
    String ARTIFACT_ANDROID_TEST = "_android_test_";
    String ARTIFACT_UNIT_TEST = "_unit_test_";
    String ARTIFACT_TEST_FIXTURES = "_test_fixtures_";

    int GENERATION_ORIGINAL = 1;
    int GENERATION_COMPONENT = 2; // component plugin is not supported since 3.5

    int MODEL_LEVEL_0_ORIGINAL = 0 ; // studio 1.0, no support for SyncIssue
    int MODEL_LEVEL_1_SYNC_ISSUE = 1; // studio 1.1+, with SyncIssue
    //int MODEL_LEVEL_2_DONT_USE = 2; // Don't use this. Go level 1 to level 3 when ready.
    int MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD =
            3; // Model for 3.0 with no variant output in import sync model.
    int MODEL_LEVEL_4_NEW_DEP_MODEL = 4;
    int MODEL_LEVEL_LATEST = MODEL_LEVEL_4_NEW_DEP_MODEL;

}
