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
import com.android.build.api.component.impl.getMainTargetSdkVersion
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.Component
import com.android.build.api.variant.InternalSources
import com.android.build.api.variant.JavaCompilation
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.api.variant.impl.LayeredSourceDirectoriesImpl
import com.android.build.api.variant.impl.TestSuiteSourceContainer
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.getOption
import com.android.builder.core.ComponentType
import com.android.utils.appendCapitalized
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test

class TestSuiteHostJarCreationConfig(val testSuite: TestSuiteCreationConfig, val sourceContainer: TestSuiteSourceContainer) :
  HostTestCreationConfig, Component by (testSuite.testedVariant as Component), ComponentCreationConfig by testSuite.testedVariant {

  override val componentType: ComponentType
    get() = com.android.builder.core.ComponentTypeImpl.UNIT_TEST

  // HostTestCreationConfig
  override val hostTestName: String
    get() = testSuite.name

  override fun runTestTaskConfigurationActions(testTask: TaskProvider<out Test>) {
    // Handled by HostJarTestSuiteTaskManager
  }

  override val codeCoverageEnabled: Boolean
    get() = false

  // HostTest
  override val androidResourcesIncluded: Boolean
    get() = testSuite.androidResourcesIncluded

  override fun configureTestTask(action: (Test) -> Unit) {
    // No-op
  }

  // TestComponent
  override val manifestPlaceholders: MapProperty<String, String> by lazy {
    services.mapProperty(String::class.java, String::class.java).also { it.set(emptyMap<String, String>()) }
  }

  // TestComponentCreationConfig
  override fun <T> onTestedVariant(action: (VariantCreationConfig) -> T): T {
    return action(testSuite.testedVariant)
  }

  // NestedComponentCreationConfig
  override val mainVariant: VariantCreationConfig
    get() = testSuite.testedVariant

  // TestCreationConfig
  override val targetSdkVersion: AndroidVersion
    get() = getMainTargetSdkVersion()

  override val instrumentationRunner: Provider<String>
    get() = testSuite.services.provider { "androidx.test.runner.AndroidJUnitRunner" }

  override val testedApplicationId: Provider<String>
    get() = testSuite.testedVariant.applicationId

  // ComponentCreationConfig overrides
  override val name: String
    get() = sourceContainer.identifier

  override val artifacts: ArtifactsImpl
    get() = sourceContainer.artifacts

  override val taskContainer: MutableTaskContainer by lazy { MutableTaskContainer() }

  override val sources: InternalSources by lazy { TestSuiteHostJarSources(testSuite.testedVariant.sources, sourceContainer) }

  override val paths: VariantPathHelper by lazy {
    TestSuitePathHelper(
      sourceContainer = sourceContainer,
      testedVariantPathHelper = testSuite.testedVariant.paths,
      componentIdentity = testSuite.testedVariant,
      projectOptionsLookup = testSuite.services.projectOptions::getOption,
      fileCreator = testSuite.services::file,
    )
  }

  override fun computeTaskNameInternal(prefix: String, suffix: String): String = prefix.appendCapitalized(name, suffix)

  override fun computeTaskNameInternal(prefix: String): String = prefix.appendCapitalized(name)

  // Ambiguity resolution from multiple delegation
  override val compileClasspath: FileCollection
    get() = testSuite.testedVariant.compileClasspath

  override val debuggable: Boolean
    get() = testSuite.testedVariant.debuggable

  override val javaCompilation: JavaCompilation
    get() = testSuite.testedVariant.javaCompilation

  override val lifecycleTasks: LifecycleTasksImpl
    get() = testSuite.testedVariant.lifecycleTasks

  override val namespace: Provider<String>
    get() = testSuite.testedVariant.namespace

  override val buildType: String?
    get() = testSuite.testedVariant.buildType

  override val flavorName: String?
    get() = testSuite.testedVariant.flavorName

  override val productFlavors: List<Pair<String, String>>
    get() = testSuite.testedVariant.productFlavors
}

class TestSuiteHostJarSources(val delegate: InternalSources, val sourceContainer: TestSuiteSourceContainer) : InternalSources by delegate {
  override val assets: LayeredSourceDirectoriesImpl?
    get() = null

  override val res: LayeredSourceDirectoriesImpl?
    get() = null

  override val jniLibs: LayeredSourceDirectoriesImpl?
    get() = null

  override val resources: FlatSourceDirectoriesImpl?
    get() = (sourceContainer.source as? com.android.build.gradle.internal.api.HostJarTestSuiteSourceSet)?.resources
}
