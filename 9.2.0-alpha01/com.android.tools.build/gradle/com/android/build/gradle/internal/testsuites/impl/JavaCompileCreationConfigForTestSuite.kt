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
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.variant.AnnotationProcessor
import com.android.build.api.variant.ScopedArtifacts.Scope
import com.android.build.api.variant.impl.TestSuiteSourceContainer
import com.android.build.gradle.internal.api.HostJarTestSuiteSourceSet
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.creationconfig.JavaCompileCreationConfig
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.util.PatternSet

internal class JavaCompileCreationConfigForTestSuite(
  val sourceContainer: TestSuiteSourceContainer,
  val testSuiteSource: HostJarTestSuiteSourceSet,
  val testedVariant: VariantCreationConfig,
  override val services: TaskCreationServices,
  override val taskContainer: MutableTaskContainer,
) : JavaCompileCreationConfig {

  override val source: FileTree
    get() {
      // Include only java sources, otherwise we hit b/144249620.
      val javaSourcesFilter = PatternSet().include("**/*.java")
      return services
        .fileCollection()
        .also { fileCollection ->
          // do not resolve the provider before execution phase, b/117161463.
          testSuiteSource.java?.let { javaSources -> fileCollection.from(javaSources.getAsFileTrees()) }
        }
        .asFileTree
        .matching(javaSourcesFilter)
    }

  override val isExportDataBindingClassList: Boolean
    get() = false

  override val isIncremental: Boolean
    get() = true

  override val dataBinding: Boolean
    get() = false

  override val usingKapt: Boolean
    get() = false

  override val useBuiltInKaptSupport: Boolean
    get() = false

  override val sourceCompatibility: String?
    get() = compileOptions.sourceCompatibility.toString()

  override val targetCompatibility: String?
    get() = compileOptions.targetCompatibility.toString()

  override val compileSdkHashString: String
    get() = testedVariant.global.compileSdkHashString

  override val compileOptions: CompileOptions
    get() = testedVariant.global.compileOptions

  override val annotationProcessor: AnnotationProcessor
    get() = testedVariant.javaCompilation.annotationProcessor

  override val bootClasspath: Provider<List<RegularFile>>
    get() = testedVariant.global.bootClasspath

  override val compileClasspath: FileCollection
    get() =
      services
        .fileCollection()
        .from(
          sourceContainer.suiteSourceClasspath.compileClasspath.asFileTree,
          testedVariant.artifacts.forScope(Scope.PROJECT).getFinalArtifacts(ScopedArtifact.POST_COMPILATION_CLASSES),
        )

  override val builtInKotlincOutput: Provider<Directory>?
    get() = null

  override val builtInKaptArtifact: Provider<Directory>?
    get() = null

  override val annotationProcessorPath: FileCollection?
    get() = testedVariant.getAnnotationProcessorJars()

  override val name: String
    get() = sourceContainer.identifier

  override val artifacts: ArtifactsImpl
    get() = sourceContainer.artifacts

  private var javacTask: TaskProvider<out JavaCompile>? = null

  override fun setJavaCompileTask(task: TaskProvider<JavaCompile>) {
    // do nothing.
  }

  override val global: GlobalTaskCreationConfig
    get() = testedVariant.global

  override val lifecycleTasks: LifecycleTasksImpl
    get() = testedVariant.lifecycleTasks
}
