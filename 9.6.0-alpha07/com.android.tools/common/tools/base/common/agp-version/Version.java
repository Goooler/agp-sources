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

package com.android;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Version strings for Android Gradle plugin, Lint and related tools. */
public final class Version {
    public static final String TOOLS_VERSION;
    public static final String ANDROID_GRADLE_PLUGIN_VERSION;
    public static final String ANDROID_TOOLS_BASE_VERSION;
    public static final int BUILDER_MODEL_API_VERSION;
    public static final int BUILDER_NATIVE_MODEL_API_VERSION;
    public static final String LAST_STABLE_ANDROID_GRADLE_PLUGIN_VERSION;
    public static final Boolean IS_AGP_RELEASE_BRANCH;
    public static final String DEV_AGP_MAJOR_MINOR;
    public static final Map<String, String> AGP_COMPATIBLE_GRADLE_VERSIONS;

    static {
        Properties properties = new Properties();
        InputStream stream =
                new BufferedInputStream(Version.class.getResourceAsStream("version.properties"));
        try {
            try {
                properties.load(stream);
            } finally {
                stream.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ANDROID_GRADLE_PLUGIN_VERSION = properties.getProperty("buildVersion");
        LAST_STABLE_ANDROID_GRADLE_PLUGIN_VERSION =
                properties.getProperty("lastStableBuildVersion");
        TOOLS_VERSION = properties.getProperty("cmdlineToolsVersion");
        ANDROID_TOOLS_BASE_VERSION = properties.getProperty("baseVersion");
        BUILDER_MODEL_API_VERSION = Integer.parseInt(properties.getProperty("apiVersion"));
        BUILDER_NATIVE_MODEL_API_VERSION =
                Integer.parseInt(properties.getProperty("nativeApiVersion"));
        IS_AGP_RELEASE_BRANCH = Boolean.valueOf(properties.getProperty("isAgpReleaseBranch"));
        DEV_AGP_MAJOR_MINOR = properties.getProperty("devAgpMajorMinor");
        Map<String, String> compatibleGradleVersions = new LinkedHashMap<>();
        String rawCompatibleVersions = properties.getProperty("agpCompatibleGradleVersions");
        if (rawCompatibleVersions != null && !rawCompatibleVersions.isEmpty()) {
            for (String entry : rawCompatibleVersions.split(",")) {
                int separator = entry.indexOf('=');
                if (separator > 0) {
                    compatibleGradleVersions.put(
                            entry.substring(0, separator).trim(),
                            entry.substring(separator + 1).trim());
                }
            }
        }
        AGP_COMPATIBLE_GRADLE_VERSIONS = Collections.unmodifiableMap(compatibleGradleVersions);
    }

    private Version() {}
}
