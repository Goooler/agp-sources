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
import com.android.build.api.attributes.AgpVersionAttr
import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.api.dsl.AgpTestSuiteDependencies
import com.android.build.api.dsl.AgpTestSuiteInputParameters
import com.android.build.api.variant.TestSuiteSourceType
import com.android.build.api.variant.impl.HasTestFixtures
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.dsl.MultiVariantComponentDslInfo
import com.android.build.gradle.internal.dependency.LayoutlibExtractor
import com.android.build.gradle.internal.dependency.TestSuiteSourceClasspath
import com.android.build.gradle.internal.dependency.checkConfigurationAlignments
import com.android.build.gradle.internal.dependency.requestAndroidJvmKotlinPlatform
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.IssueReporter
import com.google.common.collect.Maps
import java.util.concurrent.Callable
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily

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
  private val sourceType: TestSuiteSourceType,
  private val sourceSetName: String = testSuiteBuilder.name,
) {

  private val jvmEnvironment = project.objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.ANDROID)
  private val standardJvmEnvironment = project.objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM)
  private val agpVersion = project.objects.named(AgpVersionAttr::class.java, Version.ANDROID_GRADLE_PLUGIN_VERSION)
  private val testSuiteName = testSuiteBuilder.name
  private val enginesDependencies = testSuiteBuilder.junitEngineSpec.enginesDependencies

  /**
   * Gather all declared dependencies into a [Collection] of [DependencyCollector] by running a block on all defined
   * [AgpTestSuiteDependencies]. That's the [dslDeclaredDependencies] that are declared in the DSL and the [variantSpecificDependencies]
   * that are potentially added through the Variant API.
   */
  private fun gatherCollectors(action: (AgpTestSuiteDependencies) -> Collection<DependencyCollector>): Collection<DependencyCollector> {
    return listOfNotNull(dslDeclaredDependencies, variantSpecificDependencies).flatMap(action)
  }

  fun build(): TestSuiteSourceClasspath {
    val factory = project.objects
    val configurations = project.configurations
    val testedVariantName = testedVariant.name

    val identifier = "${testedVariantName}${sourceSetName.capitalized()}"

    // ----------- COMPILE CLASSPATH
    val compileClasspathName: String = identifier + "CompileClasspath"
    val compileClasspath: Configuration = configurations.maybeCreate(compileClasspathName)
    compileClasspath.description = "Resolved configuration for compilation for test suite: $testSuiteName in $testedVariantName"
    populateClasspath(compileClasspath, gatherCollectors { listOf(it.compileOnly, it.implementation) })
    addAttributes(compileClasspath, factory.named(Usage::class.java, Usage.JAVA_API))

    // -------------- RUNTIME CLASSPATH
    val runtimeClasspathName: String = identifier + "RuntimeClasspath"
    val runtimeClasspath = configurations.maybeCreate(runtimeClasspathName)
    runtimeClasspath.description = "Resolved configuration for runtime for test suite: $testSuiteName in $testedVariantName"
    populateClasspath(runtimeClasspath, gatherCollectors { listOf(it.implementation, it.runtimeOnly) })
    addAttributes(runtimeClasspath, factory.named(Usage::class.java, Usage.JAVA_RUNTIME))

    // -------------- HOST RUNTIME CLASSPATH
    val hostRuntimeClasspathName: String = identifier + "HostRuntimeClasspath"
    val hostRuntimeClasspath = configurations.maybeCreate(hostRuntimeClasspathName)
    hostRuntimeClasspath.description = "Resolved configuration for host runtime for test suite: $testSuiteName in $testedVariantName"
    // Only host-driven test suites (like HOST_JAR) need the test classes and their
    // dependencies on the host runtime classpath. For on-device tests (TEST_APK),
    // the code runs on-device, and the host classpath should only contain the
    // test engine dependencies (enginesDependencies) required for orchestration.
    if (sourceType == TestSuiteSourceType.HOST_JAR) {
      hostRuntimeClasspath.extendsFrom(runtimeClasspath)
    }
    populateClasspath(hostRuntimeClasspath, listOf(enginesDependencies))
    // Host-jar sources compile and run against the Android variants of their dependencies (see addAttributes); for other source types,
    // this classpath only resolves the plain JVM test engines.
    addAttributesForHost(
      hostRuntimeClasspath,
      factory.named(Usage::class.java, Usage.JAVA_RUNTIME),
      isAndroidCode = sourceType == TestSuiteSourceType.HOST_JAR,
    )

    // -------------- ENGINES CLASSPATH
    val enginesClasspathName: String = identifier + "EnginesClasspath"
    val enginesClasspath =
      configurations.maybeRegister(enginesClasspathName) {
        it.description = "Resolved configuration for test engines for test suite: $testSuiteName in $testedVariantName"
        populateClasspath(it, listOf(enginesDependencies))
        addAttributesForHost(it, factory.named(Usage::class.java, Usage.JAVA_RUNTIME), isAndroidCode = false)
      }

    if (
      sourceType == TestSuiteSourceType.HOST_JAR &&
        testSuiteBuilder.junitEngineSpec.inputs.contains(AgpTestSuiteInputParameters.LAYOUTLIB_DATA_DIR)
    ) {
      registerLayoutlibExtractor(identifier, enginesClasspath)
    }

    addTestedModuleDependencies(compileClasspath, runtimeClasspath)

    return TestSuiteSourceClasspath(
      compileClasspath = compileClasspath,
      runtimeClasspath = runtimeClasspath,
      hostRuntimeClasspath = hostRuntimeClasspath,
      enginesClasspath = enginesClasspath,
      layoutlibConsumerId = identifier,
      objectFactory = project.objects,
    )
  }

  /**
   * Adds the tested module, its test fixtures, and its dependencies to the suite's classpaths:
   * - [compileClasspath] extends the `implementation` configurations of the tested variant's source sets and depends on `project()`, which
   *   resolves the tested module's API elements.
   * - [runtimeClasspath] depends on `project()` when the suite packages or loads the tested module itself: host-jar and asset suites, and
   *   test APKs of a library.
   * - Both depend on `testFixtures(project())` when the tested variant has test fixtures.
   *
   * These links are live, so dependencies added to the tested variant after suite creation, such as `kotlin-stdlib`, reach the suite.
   */
  private fun addTestedModuleDependencies(compileClasspath: Configuration, runtimeClasspath: Configuration) {
    val dependencies = project.dependencies
    val testedVariantDependencies = testedVariant.variantDependencies

    testedVariantDependencies.sourceSetImplementationConfigurations.forEach { compileClasspath.extendsFrom(it) }
    compileClasspath.dependencies.add(dependencies.project())

    // A test APK of an application or dynamic feature runs in the tested APK's process, which provides the tested module at runtime.
    val runsInTestedApk = sourceType == TestSuiteSourceType.TEST_APK && testedVariant.componentType.isApk
    if (!runsInTestedApk) {
      runtimeClasspath.dependencies.add(dependencies.project())
    }

    if ((testedVariant as? HasTestFixtures)?.testFixtures != null) {
      compileClasspath.dependencies.add(dependencies.testFixtures(dependencies.project()))
      runtimeClasspath.dependencies.add(dependencies.testFixtures(dependencies.project()))
    }

    addDependencyConstraints(compileClasspath, runtimeClasspath, isTestApk = sourceType == TestSuiteSourceType.TEST_APK)
  }

  /**
   * Aligns dependency versions, unless [BooleanOption.DISABLE_ALL_CONSTRAINTS] is set:
   * - A test APK of an application or dynamic feature shares the tested APK's process, so [runtimeClasspath] resolves consistently with the
   *   tested variant's runtime classpath.
   * - With [BooleanOption.USE_DEPENDENCY_CONSTRAINTS], [compileClasspath] resolves consistently with [runtimeClasspath]. For host-jar and
   *   asset suites, this also requires [BooleanOption.EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS] to be off.
   */
  private fun addDependencyConstraints(compileClasspath: Configuration, runtimeClasspath: Configuration, isTestApk: Boolean) {
    if (projectOptions[BooleanOption.DISABLE_ALL_CONSTRAINTS]) {
      return
    }
    if (isTestApk && testedVariant.componentType.isApk) {
      val testedRuntimeClasspath = testedVariant.variantDependencies.runtimeClasspath
      runtimeClasspath.shouldResolveConsistentlyWith(testedRuntimeClasspath)
      checkConfigurationAlignments(runtimeClasspath, testedRuntimeClasspath, issueReporter, project.buildFile)
    }
    if (projectOptions[BooleanOption.USE_DEPENDENCY_CONSTRAINTS]) {
      val applyLibraryConstraints = !projectOptions[BooleanOption.EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS]
      if (applyLibraryConstraints || isTestApk) {
        compileClasspath.shouldResolveConsistentlyWith(runtimeClasspath)
      }
    }
  }

  /** Registers the [LayoutlibExtractor] that extracts the layoutlib runtime distribution for this test suite. */
  private fun registerLayoutlibExtractor(identifier: String, enginesClasspath: NamedDomainObjectProvider<Configuration>) {
    // Callable, so that the engines classpath is only resolved if the transform actually runs.
    val frameworkResources =
      project.files(
        Callable {
          enginesClasspath
            .get()
            .incoming
            .artifactView { config ->
              config.componentFilter { id ->
                id is ModuleComponentIdentifier &&
                  id.group == LayoutlibExtractor.LAYOUTLIB_GROUP &&
                  id.module == LayoutlibExtractor.LAYOUTLIB_RESOURCES_MODULE
              }
            }
            .files
        }
      )
    LayoutlibExtractor.registerTransform(project, identifier, frameworkResources)
  }

  /** Lazily registers the configuration named [name], or returns the existing one if it has already been registered. */
  private fun ConfigurationContainer.maybeRegister(
    name: String,
    action: (Configuration) -> Unit,
  ): NamedDomainObjectProvider<Configuration> = if (names.contains(name)) named(name) else register(name, action)

  private fun populateClasspath(classpath: Configuration, from: Collection<DependencyCollector>) {
    for (collector in from) {
      classpath.dependencies.addAllLater(collector.dependencies)
      classpath.dependencyConstraints.addAllLater(collector.dependencyConstraints)
    }
  }

  private fun addAttributes(configuration: Configuration, usage: Usage) {
    configuration.isCanBeConsumed = false
    configuration.resolutionStrategy.sortArtifacts(ResolutionStrategy.SortOrder.CONSUMER_FIRST)
    val attributes = configuration.attributes
    attributes.attribute(Usage.USAGE_ATTRIBUTE, usage)
    attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.LIBRARY))
    attributes.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, jvmEnvironment)
    attributes.attribute(AgpVersionAttr.ATTRIBUTE, agpVersion)
    requestAndroidJvmKotlinPlatform(configuration, project, projectOptions)
    val consumptionFlavorMap = getConsumptionFlavorAttributes(flavorSelection)
    applyVariantAttributes(attributes, testedVariant.buildType, consumptionFlavorMap)
  }

  /**
   * @param isAndroidCode whether [configuration] resolves Android code and its dependencies, requesting [jvmEnvironment] and the
   *   `androidJvm` Kotlin platform.
   */
  private fun addAttributesForHost(configuration: Configuration, usage: Usage, isAndroidCode: Boolean) {
    configuration.isCanBeConsumed = false
    configuration.resolutionStrategy.sortArtifacts(ResolutionStrategy.SortOrder.CONSUMER_FIRST)
    val attributes = configuration.attributes
    attributes.attribute(Usage.USAGE_ATTRIBUTE, usage)
    attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.LIBRARY))
    attributes.attribute(
      TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
      if (isAndroidCode) jvmEnvironment else standardJvmEnvironment,
    )
    attributes.attribute(AgpVersionAttr.ATTRIBUTE, agpVersion)
    if (isAndroidCode) {
      requestAndroidJvmKotlinPlatform(configuration, project, projectOptions)
    }

    // Resolve host OS family and architecture attributes dynamically and CC-safely
    val hostOs = project.providers.systemProperty("os.name").map { getOperatingSystemFamily(it) }
    val hostArch = project.providers.systemProperty("os.arch").map { getMachineArchitecture(it) }

    attributes.attributeProvider(
      OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE,
      hostOs.map { project.objects.named(OperatingSystemFamily::class.java, it) },
    )
    attributes.attributeProvider(
      MachineArchitecture.ARCHITECTURE_ATTRIBUTE,
      hostArch.map { project.objects.named(MachineArchitecture::class.java, it) },
    )

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

/**
 * The classes on the compile classpath of [suiteSourceClasspath], with the tested module last.
 *
 * The compile classes jar of an application or dynamic feature (`COMPILE_APP_CLASSES_JAR`) bundles the `R` classes of its dependencies at
 * the versions the tested module resolves. Ordering it last puts the `R` classes of the versions the suite resolves first, so the suite
 * compiles against the resources those versions declare. The tested module is the root component of the resolution result, because
 * `project()` resolves to the consuming project.
 */
internal fun getTestSuiteCompileClasses(suiteSourceClasspath: TestSuiteSourceClasspath, services: TaskCreationServices): FileCollection {
  val classes = suiteSourceClasspath.getArtifactCollection(ConsumedConfigType.COMPILE_CLASSPATH, AndroidArtifacts.ArtifactType.CLASSES)
  val testedModule = suiteSourceClasspath.getResolutionResult(ConsumedConfigType.COMPILE_CLASSPATH).rootComponent.map { it.id }
  fun classesOf(fromTestedModule: Boolean) =
    classes.resolvedArtifacts.zip(testedModule) { artifacts, testedModuleId ->
      artifacts.filter { (it.id.componentIdentifier == testedModuleId) == fromTestedModule }.map { it.file }
    }
  return services.fileCollection(classesOf(fromTestedModule = false), classesOf(fromTestedModule = true)).builtBy(classes.artifactFiles)
}

internal fun getOperatingSystemFamily(osName: String): String {
  return when {
    osName.contains("mac", ignoreCase = true) || osName.contains("darwin", ignoreCase = true) -> OperatingSystemFamily.MACOS
    osName.contains("linux", ignoreCase = true) -> OperatingSystemFamily.LINUX
    osName.contains("windows", ignoreCase = true) -> OperatingSystemFamily.WINDOWS
    else -> throw IllegalStateException("Unsupported operating system: $osName")
  }
}

internal fun getMachineArchitecture(osArch: String): String {
  return when {
    osArch.contains("aarch64", ignoreCase = true) || osArch.contains("arm64", ignoreCase = true) -> "aarch64"
    osArch.contains("x86_64", ignoreCase = true) ||
      osArch.contains("amd64", ignoreCase = true) ||
      osArch.contains("x86-64", ignoreCase = true) -> "x86-64"
    else -> throw IllegalStateException("Unsupported machine architecture: $osArch")
  }
}
