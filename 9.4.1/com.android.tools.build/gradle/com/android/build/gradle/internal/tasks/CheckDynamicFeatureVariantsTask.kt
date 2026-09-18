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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclaration
import com.android.build.gradle.options.BooleanOption
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * Execution-time verification task that checks if dynamic feature modules have matching build types and product flavors with the
 * application module.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.VERIFICATION)
abstract class CheckDynamicFeatureVariantsTask : NonIncrementalTask() {

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val featureMetadataFiles: ConfigurableFileCollection

  @get:Input @get:Optional abstract val appBuildType: Property<String>

  @get:Input abstract val appProductFlavors: MapProperty<String, String>

  /** Whether variant mismatches should cause build failures (true) or log warnings (false). */
  @get:Input abstract val enforceVariantMatching: Property<Boolean>

  /** A fake output directory, used for task dependencies and UP-TO-DATE purposes. */
  @get:OutputDirectory abstract val fakeOutputDir: DirectoryProperty

  override fun doTaskAction() {
    val metadataFiles = featureMetadataFiles.asFileTree.matching { it.include(FeatureSplitDeclaration.PERSISTED_FILE_NAME) }.files

    val appBuildTypeValue = appBuildType.orNull
    val appFlavorsMap = appProductFlavors.get()
    val mismatches = mutableListOf<String>()

    for (file in metadataFiles) {
      val declaration = FeatureSplitDeclaration.load(file)

      if (declaration.buildType != null && appBuildTypeValue != null && declaration.buildType != appBuildTypeValue) {
        mismatches.add(
          "Module ${declaration.modulePath} has build type '${declaration.buildType}' which does not match application build type '$appBuildTypeValue'."
        )
      }

      // Check exact 1:1 product flavor parity across all flavor dimensions defined by either the app or dynamic feature.
      // Even if missingDimensionStrategy is used during Gradle dependency resolution, we enforce that all variants
      // match 1:1 between the application and dynamic feature modules.
      val allDimensions = appFlavorsMap.keys + declaration.productFlavors.keys
      for (dimension in allDimensions) {
        val appFlavor = appFlavorsMap[dimension]
        val featureFlavor = declaration.productFlavors[dimension]
        if (appFlavor != null && featureFlavor != null) {
          if (appFlavor != featureFlavor) {
            mismatches.add(
              "Module ${declaration.modulePath} has product flavor '$featureFlavor' for dimension '$dimension' which does not match application product flavor '$appFlavor'."
            )
          }
        } else if (appFlavor != null && featureFlavor == null) {
          mismatches.add(
            "Module ${declaration.modulePath} is missing product flavor dimension '$dimension' which is defined by the application with value '$appFlavor'."
          )
        } else if (appFlavor == null && featureFlavor != null) {
          mismatches.add(
            "Module ${declaration.modulePath} defines product flavor '$featureFlavor' for dimension '$dimension' which is not defined by the application."
          )
        }
      }
    }

    if (mismatches.isNotEmpty()) {
      val message = mismatches.joinToString(separator = "\n")
      if (enforceVariantMatching.get()) {
        throw GradleException(message)
      } else {
        logger.warn(message)
      }
    }
  }

  class CreationAction(creationConfig: ComponentCreationConfig) :
    VariantTaskCreationAction<CheckDynamicFeatureVariantsTask, ComponentCreationConfig>(creationConfig, dependsOnPreBuildTask = false) {

    override val name: String
      get() = computeTaskName("check", "DynamicFeatureVariants")

    override val type: Class<CheckDynamicFeatureVariantsTask>
      get() = CheckDynamicFeatureVariantsTask::class.java

    override fun configure(task: CheckDynamicFeatureVariantsTask) {
      super.configure(task)

      task.featureMetadataFiles.from(
        creationConfig.variantDependencies.getArtifactFileCollection(
          AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
          AndroidArtifacts.ArtifactScope.PROJECT,
          AndroidArtifacts.ArtifactType.REVERSE_METADATA_FEATURE_DECLARATION,
        )
      )
      creationConfig.buildType?.let { task.appBuildType.set(it) }
      task.appProductFlavors.set(creationConfig.productFlavors.toMap())
      task.enforceVariantMatching.set(
        creationConfig.services.projectOptions.getProvider(BooleanOption.ENFORCE_DYNAMIC_FEATURE_VARIANT_MATCHING)
      )
      task.fakeOutputDir.set(
        creationConfig.services.projectInfo.intermediatesDirectory.map {
          it.dir("check-dynamic-feature-variants").dir(creationConfig.dirName)
        }
      )
    }
  }
}
