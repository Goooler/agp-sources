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

package com.android.build.gradle.internal.testsuites.impl

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.LifecycleTasksImpl
import com.android.build.api.variant.impl.TestSuiteSourceContainer
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.creationconfig.JavaPreCompileTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.provider.Provider

class JavaPreCompileTaskCreationConfigImpl(
  val testSuite: TestSuiteCreationConfig,
  val sourceContainer: TestSuiteSourceContainer,
  override val services: TaskCreationServices,
) : JavaPreCompileTaskCreationConfig {

  override val annotationProcessorArtifacts: ArtifactCollection?
    get() = null

  override val finalListOfClassNames: Provider<List<String>> = services.provider { emptyList() }
  override val kspProcessorArtifacts: ArtifactCollection?
    get() = null

  override val name: String
    get() = sourceContainer.identifier

  override val taskContainer: MutableTaskContainer = MutableTaskContainer()
  override val artifacts: ArtifactsImpl
    get() = sourceContainer.artifacts

  override val global: GlobalTaskCreationConfig
    get() = testSuite.global

  override val lifecycleTasks: LifecycleTasksImpl
    get() = testSuite.testedVariant.lifecycleTasks
}
