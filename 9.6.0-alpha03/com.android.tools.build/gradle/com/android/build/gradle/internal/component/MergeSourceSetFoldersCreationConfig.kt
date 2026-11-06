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

package com.android.build.gradle.internal.component

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.LifecycleTasksImpl
import com.android.build.api.variant.InternalSources
import com.android.build.api.variant.impl.AndroidResourcesImpl
import com.android.build.api.variant.impl.LayeredSourceDirectoriesImpl
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.VariantPathHelper

interface MergeSourceSetFoldersCreationConfig : TaskCreationConfig {
  val androidResources: AndroidResourcesImpl?
  val variantDependencies: VariantDependencies
  val inputSources: LayeredSourceDirectoriesImpl?
  val paths: VariantPathHelper
}

class ComponentBasedMergeSourceSetFoldersCreationConfig(
  private val config: ComponentCreationConfig,
  val sourcesLocator: (InternalSources) -> LayeredSourceDirectoriesImpl?,
) : MergeSourceSetFoldersCreationConfig {

  override val androidResources: AndroidResourcesImpl?
    get() = config.androidResources

  override val variantDependencies: VariantDependencies
    get() = config.variantDependencies

  override val inputSources: LayeredSourceDirectoriesImpl?
    get() = sourcesLocator.invoke(config.sources)

  override val paths: VariantPathHelper
    get() = config.paths

  override val name: String
    get() = config.name

  override val services: TaskCreationServices
    get() = config.services

  override val taskContainer: MutableTaskContainer
    get() = config.taskContainer

  override val artifacts: ArtifactsImpl
    get() = config.artifacts

  override val global: GlobalTaskCreationConfig
    get() = config.global

  override val lifecycleTasks: LifecycleTasksImpl
    get() = config.lifecycleTasks
}
