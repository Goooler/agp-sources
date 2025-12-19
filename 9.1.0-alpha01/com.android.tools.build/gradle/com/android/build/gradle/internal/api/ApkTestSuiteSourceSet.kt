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

package com.android.build.gradle.internal.api

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.build.api.variant.SourceDirectories
import com.android.build.api.variant.impl.FileBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.gradle.internal.services.VariantServices
import java.io.File

class ApkTestSuiteSourceSet (
    private val sourceSetName: String,
    private val variantServices: VariantServices,
): TestSuiteSourceSet.TestApk {

    private val javaSourcesFolder = FlatSourceDirectoriesImpl(
        sourceSetName,
        variantServices,
        null,
    ).also {
        it.addSource(FileBasedDirectoryEntryImpl(
            name = sourceSetName,
            directory = File(variantServices.projectInfo.projectDirectory.asFile, "src/$sourceSetName/java"),
            filter = null,
            isUserAdded = false,
            shouldBeAddedToIdeModel = true
        ))
    }

    private val kotlinSourcesFolder = FlatSourceDirectoriesImpl(
        sourceSetName,
        variantServices,
        null,
    ).also {
        it.addSource(FileBasedDirectoryEntryImpl(
            name = sourceSetName,
            directory = File(variantServices.projectInfo.projectDirectory.asFile, "src/$sourceSetName/kotlin"),
            filter = null,
            isUserAdded = false,
            shouldBeAddedToIdeModel = true
        ))
    }

    private val resourcesSourcesFolder = FlatSourceDirectoriesImpl(
        sourceSetName,
        variantServices,
        null,
    ).also {
        it.addSource(FileBasedDirectoryEntryImpl(
            name = sourceSetName,
            directory = File(variantServices.projectInfo.projectDirectory.asFile, "src/$sourceSetName/resources"),
            filter = null,
            isUserAdded = false,
            shouldBeAddedToIdeModel = true
        ))
    }

    override fun manifestFile(): File? {
        val manifest = File(
            variantServices.projectInfo.projectDirectory.asFile,
            "src/$sourceSetName/$FN_ANDROID_MANIFEST_XML"
        )
        return manifest.takeIf { it.exists() }
    }

    override fun java(): FlatSourceDirectoriesImpl = javaSourcesFolder

    override fun kotlin(): FlatSourceDirectoriesImpl = kotlinSourcesFolder

    override fun resources(): FlatSourceDirectoriesImpl = resourcesSourcesFolder
}
