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

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.LifecycleTasksImpl
import com.android.build.api.variant.AnnotationProcessor
import com.android.build.api.variant.ScopedArtifacts.Scope
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.api.variant.impl.TestSuiteSourceContainer
import com.android.build.gradle.internal.api.HostJarTestSuiteSourceSet
import com.android.build.gradle.internal.component.BuiltInKotlinCreationConfig
import com.android.build.gradle.internal.component.ComponentBasedBuiltInKotlinCreationConfig
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.BuiltInKaptSupportMode
import com.android.build.gradle.internal.services.BuiltInKotlinSupportMode
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import org.gradle.api.JavaVersion
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.plugin.sources.android.AndroidVariantType

internal class BuiltInKotlinCreationConfigImpl(
  val testSuite: TestSuiteCreationConfig,
  val sourceContainer: TestSuiteSourceContainer,
  val source: HostJarTestSuiteSourceSet,
  val testedVariant: VariantCreationConfig,
  override val services: TaskCreationServices,
  override val taskContainer: MutableTaskContainer,
  override val javacTask: TaskProvider<JavaCompile>,
) : BuiltInKotlinCreationConfig {

  override val kotlin: FlatSourceDirectoriesImpl?
    get() = source.kotlin

  override val java: FlatSourceDirectoriesImpl?
    get() = source.java

  override fun java(action: (FlatSourceDirectoriesImpl) -> Unit) {
    source.java?.let { action(it) }
  }

  override fun kotlin(action: (FlatSourceDirectoriesImpl) -> Unit) {
    source.kotlin?.let { action(it) }
  }

  override val bootClasspath: Provider<List<RegularFile>>
    get() = testSuite.global.bootClasspath

  override fun getAnnotationProcessorJars(): FileCollection = testSuite.testedVariant.getAnnotationProcessorJars()

  override fun getJavaClasspath(
    configType: AndroidArtifacts.ConsumedConfigType,
    classesType: AndroidArtifacts.ArtifactType,
    generatedBytecodeKey: Any?,
  ): FileCollection =
    services
      .fileCollection()
      .from(
        sourceContainer.suiteSourceClasspath.compileClasspath.asFileTree,
        testedVariant.artifacts.forScope(Scope.PROJECT).getFinalArtifacts(ScopedArtifact.POST_COMPILATION_CLASSES),
      )

  override val builtInKotlinSupportMode: BuiltInKotlinSupportMode
    get() = testSuite.testedVariant.builtInKotlinSupportMode

  override val builtInKaptSupportMode: BuiltInKaptSupportMode
    get() = BuiltInKaptSupportMode.NotSupported

  override fun getBuiltInKaptArtifact(internalArtifactType: InternalArtifactType<Directory>): Provider<Directory>? {
    return null
  }

  override val targetCompatibility: JavaVersion
    get() = testSuite.global.compileOptions.targetCompatibility

  override val kaptSourceOutputDir: Provider<Directory>
    get() = throw RuntimeException("KAPT for test suites is not yet implemented")

  override val kaptKotlinSourceOutputDir: Provider<Directory>
    get() = throw RuntimeException("KAPT for test suites is not yet implemented")

  override fun getExplicitApiMode(): ExplicitApiMode? {
    return ExplicitApiMode.Disabled
  }

  @OptIn(InternalKotlinGradlePluginApi::class)
  override fun toAndroidVariantType(): AndroidVariantType {
    return AndroidVariantType.UnitTest
  }

  override val sourceCompatibility: JavaVersion
    get() = testSuite.global.compileOptions.sourceCompatibility

  override val annotationProcessor: AnnotationProcessor
    get() = testSuite.testedVariant.javaCompilation.annotationProcessor

  override fun setupFriends(friendPaths: ConfigurableFileCollection) {
    ComponentBasedBuiltInKotlinCreationConfig.setupFriendsForNestedComponent(friendPaths, testSuite.testedVariant)
    ComponentBasedBuiltInKotlinCreationConfig.setupFriendsForTestComponent(friendPaths, testSuite.testedVariant)
  }

  override val name: String
    get() = sourceContainer.identifier

  override val artifacts: ArtifactsImpl
    get() = sourceContainer.artifacts

  override val global: GlobalTaskCreationConfig
    get() = testSuite.global

  override val lifecycleTasks: LifecycleTasksImpl
    get() = testSuite.testedVariant.lifecycleTasks
}
