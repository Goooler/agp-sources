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

package com.android.build.gradle.internal.plugins

import com.android.SdkConstants
import com.android.build.api.dsl.AiPackExtension
import com.android.build.gradle.internal.dsl.AiPackExtensionImpl
import com.android.build.gradle.internal.tasks.AssetPackManifestGenerationTask
import com.android.build.gradle.internal.tasks.UsesAnalytics
import com.android.build.gradle.internal.utils.maybeRegister
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.Plugin
import org.gradle.api.Project

class AiPackPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val aiPackExtension = project.extensions.create(AiPackExtension::class.java, "aiPack", AiPackExtensionImpl::class.java)

        val manifestGenerationTaskProvider = project.tasks.register(
            "generateAiPackManifest",
            AssetPackManifestGenerationTask::class.java
        ) { manifestGenerationTask ->
            UsesAnalytics.ConfigureAction.configure(manifestGenerationTask)
            manifestGenerationTask.manifestFile.setDisallowChanges(
                project.layout.buildDirectory.get().dir(
                    SdkConstants.FD_INTERMEDIATES
                ).dir("ai_pack_manifest").file(SdkConstants.FN_ANDROID_MANIFEST_XML)
            )
            manifestGenerationTask.aiPack.setDisallowChanges(true)
            manifestGenerationTask.packName.setDisallowChanges(aiPackExtension.packName)
            manifestGenerationTask.deliveryType.setDisallowChanges(aiPackExtension.dynamicDelivery.deliveryType)
            manifestGenerationTask.instantDeliveryType.setDisallowChanges(aiPackExtension.dynamicDelivery.instantDeliveryType)
            manifestGenerationTask.aiModelDependencyName.setDisallowChanges(aiPackExtension.modelDependency.aiModelName)
            manifestGenerationTask.aiModelDependencyPackageName.setDisallowChanges(aiPackExtension.modelDependency.aiModelPackageName)
        }

        project.configurations.maybeRegister("packElements") {
            isCanBeConsumed = true
        }
        project.configurations.maybeRegister("manifestElements") {
            isCanBeConsumed = true
        }
        project.artifacts.add("manifestElements", manifestGenerationTaskProvider.flatMap { it.manifestFile })
        project.artifacts.add("packElements", project.layout.projectDirectory.dir("src/main/assets"))
    }
}
