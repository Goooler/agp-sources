/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.fusedlibrary

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.dependency.FilterShrinkerRulesTransform
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.getAarOrJarTypeToConsume
import com.android.build.gradle.internal.r8.effectiveShrinkerVersion
import com.android.build.gradle.internal.scope.publishArtifactToConfiguration
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.DslServicesImpl
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.v2.ide.ProjectType
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.transform.TransformSpec
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

object FusedLibraryConstants {
  /**
   * Privacy sandbox doesn't have variants. However, some tasks e.g. lint may be required a variant name to be provided. In this case, we
   * have a default name to be used when this is required.
   */
  const val DEFAULT_VARIANT_NAME = "main"
  const val EXTENSION_NAME = "androidFusedLibrary"
  const val INCLUDE_CONFIGURATION_NAME = "include"
  const val FUSED_API_CONFIGURATION_NAME = "fusedApi"
  const val FUSED_RUNTIME_CONFIGURATION_NAME = "fusedRuntime"
  const val FUSED_SOURCES_CONFIGURATION_NAME = "fusedSources"
  const val VALIDATE_DEPENDENCIES_TASK_NAME = "validateDependencies"
  const val FUSED_LIBRARY_PUBLICATION_COMPONENT_NAME = "fusedLibraryComponent"
}

internal fun createTasks(
  project: Project,
  artifacts: ArtifactsImpl,
  artifactForPublication: Artifact.Single<RegularFile>,
  tasksCreationActions: List<TaskCreationAction<out DefaultTask>>,
) {
  val taskProviders =
    TaskFactoryImpl(project.tasks).let { taskFactory ->
      tasksCreationActions.map { creationAction -> taskFactory.register(creationAction) }
    }

  // create anchor tasks
  project.tasks.register("assemble") { assembleTask ->
    artifactForPublication?.let { artifactTypeForPublication -> assembleTask.dependsOn(artifacts.get(artifactTypeForPublication)) }
      ?: taskProviders.forEach { assembleTask.dependsOn(it) }
  }
}

internal fun configureTransformsForFusedLibrary(project: Project, projectServices: ProjectServices): DependencyConfigurator {
  if (projectServices.projectOptions[BooleanOption.ENABLE_PROGUARD_RULES_EXTRACTION]) {
    project.dependencies.registerTransform(FilterShrinkerRulesTransform::class.java) {
      reg: TransformSpec<FilterShrinkerRulesTransform.Parameters> ->
      reg.from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, AndroidArtifacts.ArtifactType.UNFILTERED_PROGUARD_RULES.type)
      reg.to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, AndroidArtifacts.ArtifactType.FILTERED_PROGUARD_RULES.type)
      reg.parameters { params: FilterShrinkerRulesTransform.Parameters ->
        params.shrinkerVersion.set(effectiveShrinkerVersion(projectServices.r8FromMaven))
        params.projectName.set(project.name)
      }
    }
  }
  return DependencyConfigurator(project, projectServices)
    .configureGeneralTransforms(getAarOrJarTypeToConsume(projectServices.projectOptions))
}

internal fun getDslServices(project: Project, projectServices: ProjectServices): DslServices {
  val sdkComponentsBuildService: Provider<SdkComponentsBuildService> =
    SdkComponentsBuildService.RegistrationAction(project, projectServices.projectOptions).execute()

  return DslServicesImpl(projectServices, sdkComponentsBuildService, ProjectType.FUSED_LIBRARY)
}

fun configureElements(
  project: Project,
  elements: Configuration,
  usage: String,
  artifacts: ArtifactsImpl,
  publications: Map<Artifact.Single<RegularFile>, AndroidArtifacts.ArtifactType>,
) {
  elements.attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, usage))
  elements.isCanBeResolved = false
  elements.isCanBeConsumed = true
  elements.isTransitive = true

  elements.outgoing.variants {
    for (publication in publications) {
      val artifactProvider = artifacts.get(publication.key)
      publishArtifactToConfiguration(elements, artifactProvider, publication.value)
    }
  }
}

/**
 * Intended to provide a list of Dependency from a set of ResolvedComponentResult.
 *
 * @param project The Gradle project used to create the dependencies.
 * @return A provider of a List<Dependency>.
 */
internal fun Provider<Set<ResolvedComponentResult>>.toDependenciesProvider(project: Project): Provider<List<Dependency>> {
  return map { components ->
    components.map { component ->
      val notation = component.moduleVersion.toString()
      when (component.getCategory()) {
        Category.REGULAR_PLATFORM -> project.dependencies.platform(notation)
        Category.ENFORCED_PLATFORM -> project.dependencies.enforcedPlatform(notation)
        else -> project.dependencies.create(notation)
      }
    }
  }
}

internal fun Provider<Set<ResolvedComponentResult>>.toDependencyModuleVersionIdentifiersProvider(): Provider<Set<ModuleVersionIdentifier>> {
  return map { components ->
    components.mapNotNull(ResolvedComponentResult::getModuleVersion).toSet()
  }
}

/**
 * For determining dependencies that are not included in the Fused Library AAR.
 *
 * As transitive dependencies are used for fused library publication and consumption, they must be added to these dependencies to each
 * directly to each configuration that requires them.
 *
 * @param sourceConfiguration configuration containing all dependencies (included in artifacts and dependencies). Direct/first level
 *   dependencies will be assumed to be packaged in the aar artifact.
 * @return A provider of a Set<ResolvedComponentResult>.
 */
internal fun getFusedLibraryDependencyComponents(sourceConfiguration: Configuration): Provider<Set<ResolvedComponentResult>> {
  return sourceConfiguration.incoming.resolutionResult.rootComponent.map { sourceRootComponent ->
    val dependenciesIncludedInFusedAar: Set<ModuleVersionIdentifier> =
      sourceRootComponent.dependencies
        // Unresolved dependencies cannot be published. The fused library validation tasks
        // will a human friendly error message.
        .filterIsInstance<ResolvedDependencyResult>()
        .map { it.selected.moduleVersion ?: error("${it.selected} cannot have a null moduleVersion") }
        .toSet()

    val excludedModuleVersions = dependenciesIncludedInFusedAar + listOfNotNull(sourceRootComponent.moduleVersion)

    sourceConfiguration.incoming.resolutionResult.allComponents
      // ResolvedComponentResult's subclasses don't define `equals()` methods so we need to
      // compare `ResolvedComponentResult`s through `ModuleVersionIdentifier`s.
      .asSequence()
      .filter { it.moduleVersion != null && it.moduleVersion !in excludedModuleVersions }
      .distinctBy(ResolvedComponentResult::getModuleVersion)
      .toSet()
  }
}

private fun ResolvedComponentResult.getCategory(): String? {
  return variants.firstNotNullOfOrNull { variant -> getCategoryAttribute(variant.attributes) }
}

private fun getCategoryAttribute(attributes: AttributeContainer): String? {
  if (Category.CATEGORY_ATTRIBUTE in attributes.keySet()) {
    return attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)?.name
  }
  val categoryAttr = attributes.keySet().firstOrNull { it.name == Category.CATEGORY_ATTRIBUTE.name } ?: return null
  return if (categoryAttr.type == String::class.java) {
    @Suppress("UNCHECKED_CAST") attributes.getAttribute(categoryAttr as Attribute<String>)
  } else {
    null
  }
}

internal class FusedLibraryTargetJvmEnvironmentCompatibilityRule : AttributeCompatibilityRule<TargetJvmEnvironment> {

  override fun execute(details: CompatibilityCheckDetails<TargetJvmEnvironment>) {
    with(details) {
      // 'standard-jvm' is acceptable when a dependency doesn't have the preferred
      // 'android' TargetJvmEnvironment attribute value.
      if (consumerValue?.name == TargetJvmEnvironment.ANDROID && producerValue?.name == TargetJvmEnvironment.STANDARD_JVM) {
        compatible()
      }
    }
  }
}
