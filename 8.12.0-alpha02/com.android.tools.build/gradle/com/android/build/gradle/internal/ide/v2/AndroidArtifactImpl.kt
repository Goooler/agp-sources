/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.v2

import com.android.builder.model.v2.ide.AndroidArtifact
import com.android.builder.model.v2.ide.ApiVersion
import com.android.builder.model.v2.ide.BundleInfo
import com.android.builder.model.v2.ide.BytecodeTransformation
import com.android.builder.model.v2.ide.CodeShrinker
import com.android.builder.model.v2.ide.PrivacySandboxSdkInfo
import com.android.builder.model.v2.ide.TestInfo
import java.io.File
import java.io.Serializable
import java.util.Collections

/**
 * Implementation of [AndroidArtifact] for serialization via the Tooling API.
 */
data class AndroidArtifactImpl(
    override val minSdkVersion: ApiVersion,
    override val targetSdkVersionOverride: ApiVersion?,
    override val maxSdkVersion: Int?,

    override val signingConfigName: String?,
    override val isSigned: Boolean,

    override val applicationId: String?,

    override val abiFilters: Set<String>?,
    override val testInfo: TestInfo?,
    override val bundleInfo: BundleInfo?,
    override val codeShrinker: CodeShrinker?,

    override val compileTaskName: String?,
    override val assembleTaskName: String?,
    override val sourceGenTaskName: String?,
    override val resGenTaskName: String?,
    override val ideSetupTaskNames: Set<String>,

    override val generatedSourceFolders: Collection<File>,
    override val generatedResourceFolders: Collection<File>,
    override val classesFolders: Set<File>,
    override val assembleTaskOutputListingFile: File?,
    override val privacySandboxSdkInfo: PrivacySandboxSdkInfo?,
    override val desugaredMethodsFiles: Collection<File>,
    override val generatedClassPaths: Map<String, File>,
    override val bytecodeTransformations: Collection<BytecodeTransformation>,
    override val generatedAssetsFolders:  Collection<File>
) : AndroidArtifact, Serializable {

    @Deprecated("Was never used, removed in AGP 8.3")
    override val modelSyncFiles: Collection<Void> get() = Collections.emptyList()

    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 2L
    }
}
