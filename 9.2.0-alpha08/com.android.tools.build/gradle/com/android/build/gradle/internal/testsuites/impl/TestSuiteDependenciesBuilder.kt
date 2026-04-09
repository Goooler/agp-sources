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

package com.android.build.gradle.internal.testsuites.impl

import com.android.Version
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.attributes.AgpVersionAttr
import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.api.dsl.AgpTestSuiteDependencies
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.dsl.MultiVariantComponentDslInfo
import com.android.build.gradle.internal.dependency.TestSuiteSourceClasspath
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.IssueReporter
import com.google.common.collect.Maps
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.extensions.stdlib.capitalized

/** Object that builds the dependencies of a test suite. */
class TestSuiteDependenciesBuilder
internal constructor(
  private val project: Project,
  private val projectOptions: ProjectOptions,
  private val issueReporter: IssueReporter,
  private val testSuiteBuilder: TestSuiteBuilderImpl,
  private val dslDeclaredDependencies: AgpTestSuiteDependencies?,
  private val variantSpecificDependencies: AgpTestSuiteDependencies?,
  private val testedVariant: VariantCreationConfig,
  private val flavorSelection: Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr>,
  private val dslInfo: MultiVariantComponentDslInfo,
) {

  private val jvmEnvironment = project.objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.ANDROID)
  private val agpVersion = project.objects.named(AgpVersionAttr::class.java, Version.ANDROID_GRADLE_PLUGIN_VERSION)
  private val testSuiteName = testSuiteBuilder.name
  private val enginesDependencies = testSuiteBuilder.junitEngineSpec.enginesDependencies

  /**
   * Gather all declared dependencies into a [Collection] of [DependencyCollector] by running a block on all defined
   * [AgpTestSuiteDependencies]. That's the [dslDeclaredDependencies] that are declared in the DSL and the [variantSpecificDependencies]
   * that are potentially added through the Variant API.
   */
  private fun gatherCollectors(action: (AgpTestSuiteDependencies) -> Collection<DependencyCollector>) =
    dslDeclaredDependencies?.let { action(it) }
      ?: listOf<DependencyCollector>().plus(variantSpecificDependencies?.let { action(it) } ?: listOf())

  fun build(): TestSuiteSourceClasspath {
    val factory = project.objects
    val configurations = project.configurations
    val testedVariantName = testedVariant.name

    // ----------- COMPILE CLASSPATH
    val compileClasspathName: String = testSuiteName + testedVariantName.capitalized() + "CompileClasspath"
    val compileClasspath: Configuration = configurations.maybeCreate(compileClasspathName)
    compileClasspath.isVisible = false
    compileClasspath.description = "Resolved configuration for compilation for test suite: $testSuiteName in $testedVariantName"
    populateClasspath(compileClasspath, gatherCollectors { listOf(it.compileOnly, it.implementation) })
    addAttributes(compileClasspath, factory.named(Usage::class.java, Usage.JAVA_API))

    // -------------- RUNTIME CLASSPATH
    val runtimeClasspathName: String = testSuiteName + testedVariantName.capitalized() + "RuntimeClasspath"
    val runtimeClasspath = configurations.maybeCreate(runtimeClasspathName)
    runtimeClasspath.description = "Resolved configuration for runtime for test suite: $testSuiteName in $testedVariantName"
    populateClasspath(runtimeClasspath, gatherCollectors { listOf(it.implementation, it.runtimeOnly, enginesDependencies) })
    addAttributes(runtimeClasspath, factory.named(Usage::class.java, Usage.JAVA_RUNTIME))
    runtimeClasspath.attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, AndroidArtifacts.ArtifactType.CLASSES_JAR.type)

    if (testedVariant.componentType.isAar) {
      // If the tested variant is a library, we can use standard project dependencies.
      compileClasspath.extendsFrom(testedVariant.variantDependencies.compileClasspath)
      runtimeClasspath.extendsFrom(testedVariant.variantDependencies.runtimeClasspath)
    } else {
      // If the tested variant is an application, we cannot use 'extendsFrom' because that
      // would inherit the 'category=library' attribute and cause a resolution failure.
      // Instead, we manually carry over the dependencies and add the app's classes as a file dependency.
      compileClasspath.dependencies.addAll(testedVariant.variantDependencies.compileClasspath.allDependencies)
      runtimeClasspath.dependencies.addAll(testedVariant.variantDependencies.runtimeClasspath.allDependencies)

      val appClasses =
        testedVariant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).getFinalArtifacts(ScopedArtifact.POST_COMPILATION_CLASSES)
      project.dependencies.add(compileClasspath.name, appClasses)
      project.dependencies.add(runtimeClasspath.name, appClasses)
    }

    return TestSuiteSourceClasspath(
      compileClasspath = compileClasspath,
      runtimeClasspath = runtimeClasspath,
      objectFactory = project.objects,
    )
  }

  private fun populateClasspath(classpath: Configuration, from: Collection<DependencyCollector>) {
    for (collector in from) {
      classpath.dependencies.addAllLater(collector.dependencies)
      classpath.dependencyConstraints.addAllLater(collector.dependencyConstraints)
    }
  }

  private fun addAttributes(configuration: Configuration, usage: Usage) {
    configuration.isCanBeConsumed = false
    configuration.isVisible = false
    configuration.resolutionStrategy.sortArtifacts(ResolutionStrategy.SortOrder.CONSUMER_FIRST)
    val attributes = configuration.attributes
    attributes.attribute(Usage.USAGE_ATTRIBUTE, usage)
    attributes.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, jvmEnvironment)
    attributes.attribute(AgpVersionAttr.ATTRIBUTE, agpVersion)
    val consumptionFlavorMap = getConsumptionFlavorAttributes(flavorSelection)
    applyVariantAttributes(attributes, testedVariant.buildType, consumptionFlavorMap)
  }

  private fun getConsumptionFlavorAttributes(
    flavorSelection: Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr>?
  ): Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> {
    val productFlavors = dslInfo.productFlavorList
    val map = Maps.newHashMapWithExpectedSize<Attribute<ProductFlavorAttr>, ProductFlavorAttr>(productFlavors.size)

    if (issueReporter.hasIssue(IssueReporter.Type.UNNAMED_FLAVOR_DIMENSION)) {
      return map
    }

    val objectFactory: ObjectFactory = project.objects

    for (f in productFlavors) {
      f.dimension?.let { map[ProductFlavorAttr.of(it)] = objectFactory.named(ProductFlavorAttr::class.java, f.name) }
    }

    flavorSelection?.let { map.putAll(it) }

    return map
  }

  private fun applyVariantAttributes(
    attributeContainer: AttributeContainer,
    buildType: String?,
    flavorMap: Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr>,
  ) {
    if (buildType != null) {
      attributeContainer.attribute(BuildTypeAttr.ATTRIBUTE, project.objects.named(BuildTypeAttr::class.java, buildType))
    }
    for ((key, value) in flavorMap) {
      attributeContainer.attribute(key, value)
    }
  }
}
