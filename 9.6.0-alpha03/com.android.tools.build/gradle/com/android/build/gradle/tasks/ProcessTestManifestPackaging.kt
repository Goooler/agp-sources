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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.creationconfig.ProcessTestManifestCreationConfig
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.utils.FileUtils
import java.io.File
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MANIFEST)
abstract class ProcessTestManifestPackaging : NonIncrementalTask() {

  @get:InputFile @get:PathSensitive(PathSensitivity.NAME_ONLY) abstract val mergedManifest: RegularFileProperty

  @get:OutputDirectory abstract val packagedManifestOutputDirectory: DirectoryProperty

  @get:Input abstract val applicationId: Property<String>

  public override fun doTaskAction() {
    val manifestOutputFolder = packagedManifestOutputDirectory.get().asFile
    FileUtils.mkdirs(manifestOutputFolder)
    val manifestOutputFile = File(manifestOutputFolder, SdkConstants.ANDROID_MANIFEST_XML)

    FileUtils.copyFile(mergedManifest.get().asFile, manifestOutputFile)

    BuiltArtifactsImpl(
        BuiltArtifacts.Companion.METADATA_FILE_VERSION,
        InternalArtifactType.PACKAGED_MANIFESTS,
        applicationId.get(),
        variantName,
        listOf(BuiltArtifactImpl.Companion.make(manifestOutputFile.absolutePath)),
      )
      .saveToDirectory(packagedManifestOutputDirectory.get().asFile)
  }

  class CreationAction(creationConfig: ProcessTestManifestCreationConfig) :
    VariantTaskCreationAction<ProcessTestManifestPackaging, ProcessTestManifestCreationConfig>(creationConfig) {

    override val name: String
      get() = computeTaskName("process", "Manifest")

    override val type: Class<ProcessTestManifestPackaging>
      get() = ProcessTestManifestPackaging::class.java

    override fun handleProvider(taskProvider: TaskProvider<ProcessTestManifestPackaging>) {
      super.handleProvider(taskProvider)
      creationConfig.artifacts
        .setInitialProvider(taskProvider, ProcessTestManifestPackaging::packagedManifestOutputDirectory)
        .on(InternalArtifactType.PACKAGED_MANIFESTS)
    }

    override fun configure(task: ProcessTestManifestPackaging) {
      super.configure(task)
      creationConfig.artifacts.setTaskInputToFinalProduct(SingleArtifact.MERGED_MANIFEST, task.mergedManifest)
      task.applicationId.setDisallowChanges(creationConfig.applicationId)
    }
  }
}
