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

package com.android.build.gradle.internal.ide.kmp

import com.android.build.gradle.internal.dsl.DependencySelectionImpl
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidLibraryExtensionImpl
import com.android.builder.model.v2.models.kmp.KotlinMultiplatformAndroidDependencySelection
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder

/**
 * Tooling API model builder for [KotlinMultiplatformAndroidDependencySelection] on projects applying
 * `com.android.kotlin.multiplatform.library`.
 */
internal class KotlinMultiplatformAndroidDependencySelectionModelBuilder(
  private val androidExtension: KotlinMultiplatformAndroidLibraryExtensionImpl
) : ToolingModelBuilder {

  override fun canBuild(modelName: String): Boolean = modelName == KotlinMultiplatformAndroidDependencySelection::class.java.name

  override fun buildAll(modelName: String, project: Project): KotlinMultiplatformAndroidDependencySelection {
    if (!canBuild(modelName)) {
      throw RuntimeException("Does not support model '$modelName'")
    }
    val localDependencySelection = androidExtension.localDependencySelection as DependencySelectionImpl

    return KotlinMultiplatformAndroidDependencySelectionImpl(
      selectBuildTypeFrom = ArrayList(localDependencySelection.selectBuildTypeFrom.get()),
      productFlavorSelection =
        LinkedHashMap<String, List<String>>().apply {
          localDependencySelection.getDimensions().forEach { (dimension, flavors) ->
            put(dimension, ArrayList(flavors))
          }
        },
    )
  }
}
