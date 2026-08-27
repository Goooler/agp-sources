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
package com.android.sdklib;

import com.android.annotations.Nullable;

import com.google.common.base.Strings;

/** Utility methods for SDK versions. */
public class SdkVersionUtil {
    /**
     * Returns the {@link AndroidVersion} for a given version string, which is typically an API
     * level number, but can also be a codename for a <b>preview</b> platform. Note: This should
     * <b>not</b> be used to look up version names for build codes; for that, use {@link
     * SdkVersionInfo#getBuildCode(int, int)}. The primary difference between this method is that
     * {@link SdkVersionInfo#getBuildCode(int, int)} will return the final API number for a platform
     * (e.g. for "KITKAT" it will return 19) whereas this method will return the API number for the
     * codename as a preview platform (e.g. 18).
     *
     * @param apiOrPreviewName the version string
     * @param targets an optional array of installed targets, if available. If the version string
     *     corresponds to a code name, this is used to search for a corresponding API level.
     * @return an {@link AndroidVersion}, or null if the version could not be determined (e.g. an
     *     empty or invalid API number or an unknown code name)
     */
    @Nullable
    public static AndroidVersion getVersion(
            @Nullable String apiOrPreviewName, @Nullable IAndroidTarget[] targets) {
        if (Strings.isNullOrEmpty(apiOrPreviewName)) {
            return null;
        }

        if (Character.isDigit(apiOrPreviewName.charAt(0))
                || apiOrPreviewName.startsWith("canary-")) {
            try {
                return AndroidVersion.fromString(apiOrPreviewName);
            } catch (IllegalArgumentException e) {
                // Invalid version string
                return null;
            }
        }

        // Codename
        if (targets != null) {
            for (int i = targets.length - 1; i >= 0; i--) {
                IAndroidTarget target = targets[i];
                if (target.isPlatform()) {
                    AndroidVersion version = target.getVersion();
                    if (version.isPreview()
                            && apiOrPreviewName.equalsIgnoreCase(version.getCodename())) {
                        return new AndroidVersion(version.getApiLevel(), version.getCodename());
                    }
                }
            }
        }

        int api = SdkVersionInfo.getApiByPreviewName(apiOrPreviewName, false);
        if (api != -1) {
            return new AndroidVersion(api - 1, apiOrPreviewName);
        }

        // Must be a future SDK platform
        return new AndroidVersion(SdkVersionInfo.HIGHEST_KNOWN_API, apiOrPreviewName);
    }
}
