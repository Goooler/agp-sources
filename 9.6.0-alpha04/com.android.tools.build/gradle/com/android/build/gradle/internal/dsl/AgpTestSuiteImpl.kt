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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.AgpTestSuite
import com.android.build.api.dsl.AgpTestSuiteTarget
import com.android.build.api.dsl.JUnitEngineSpec
import com.android.build.api.dsl.TestSuiteAssetsSpec
import com.android.build.api.dsl.TestSuiteHostJarSpec
import com.android.build.api.dsl.TestSuiteTestApkSpec
import com.android.build.api.dsl.TestTaskContext
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.testsuites.TestSuiteSourceCreationConfig
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test

/**
 * Implementation of the [AgpTestSuite] Dsl extension.
 *
 * @param name the Test Suite Name
 * @param dslServices internal services
 * @param androidResourcesIncluded whether Android resources are enabled by default for this suite type, used to seed
 *   [TestSuiteHostJarSpecImpl.enableAndroidResources] when a `hostJar {}` source container is first created and as the fallback when no
 *   `hostJar {}` container is configured. This is `false` for custom suites.
 */
abstract class AgpTestSuiteImpl
@Inject
constructor(private val name: String, val dslServices: DslServices, val androidResourcesIncluded: Boolean) : AgpTestSuite {

  private val jUnitEngineSpec = dslServices.newInstance(JUnitEngineSpecImpl::class.java)

  private val junitEngineUsed = AtomicBoolean(false)

  fun getJunitEngineIfUsed(): JUnitEngineSpec? = jUnitEngineSpec.takeIf { junitEngineUsed.get() }

  override fun useJunitEngine(action: JUnitEngineSpec.() -> Unit) {
    action.invoke(useJunitEngine)
  }

  fun useJunitEngine(action: Action<JUnitEngineSpec>) {
    action.execute(useJunitEngine)
  }

  override val useJunitEngine: JUnitEngineSpec
    get() {
      junitEngineUsed.set(true)
      return jUnitEngineSpec
    }

  override fun getName(): String = name

  override val targetVariants = mutableListOf<String>()

  /**
   * Adds one or more target variants to the [targetVariants] list.
   *
   * This method is provided for Groovy DSL support, allowing a more idiomatic syntax. For example:
   * ```groovy
   * targetVariants 'debug', 'release'
   * ```
   *
   * @param targetVariants The names of the variants to target.
   */
  fun targetVariants(vararg targetVariants: String) = this.targetVariants.addAll(targetVariants)

  /**
   * Returns whether this suite tests [variant].
   *
   * @param testBuildType the module's tested build type, or null if the module has none.
   */
  internal open fun targetsVariant(variant: ComponentIdentity, testBuildType: String?): Boolean = targetVariants.contains(variant.name)

  private val targets =
    dslServices.domainObjectContainer(AgpTestSuiteTarget::class.java) { name -> AgpTestSuiteTargetImpl(this@AgpTestSuiteImpl, name) }

  override fun getTargets(): NamedDomainObjectContainer<AgpTestSuiteTarget> = targets

  override var requiresUpdateTask = false

  override fun assets(action: TestSuiteAssetsSpec.() -> Unit) {
    addSource<TestSuiteAssetsSpecImpl>(action)
  }

  override fun hostJar(action: TestSuiteHostJarSpec.() -> Unit) {
    addSource<TestSuiteHostJarSpecImpl>(action, onCreate = { this.enableAndroidResources = androidResourcesIncluded })
  }

  fun hostJar(action: Action<TestSuiteHostJarSpec>) {
    hostJar { action.execute(this) }
  }

  override fun testApk(action: TestSuiteTestApkSpec.() -> Unit) {
    addSource<TestSuiteTestApkSpecImpl>(action)
  }

  fun testApk(action: Action<TestSuiteTestApkSpec>) {
    testApk { action.execute(this) }
  }

  private val sources = mutableListOf<TestSuiteSourceCreationConfig>()

  internal fun getSourceContainers(): Collection<TestSuiteSourceCreationConfig> = sources

  override fun configureTestTasks(action: Test.(TestTaskContext) -> Unit) {
    testTaskConfigActions.add(action)
  }

  override fun withTestTaskProviders(action: TaskProvider<Test>.(TestTaskContext) -> Unit) {
    testTaskProviderConfigActions.add { context -> @Suppress("UNCHECKED_CAST") (this as TaskProvider<Test>).action(context) }
  }

  override var codeCoverage = false

  /** Internal APIs */
  internal val testTaskConfigActions = mutableListOf<Test.(TestTaskContext) -> Unit>()
  internal val testTaskProviderConfigActions = mutableListOf<TaskProvider<out Test>.(TestTaskContext) -> Unit>()

  internal fun getTargetTestTaskConfigActions(): Map<String, List<Test.(TestTaskContext) -> Unit>> =
    targets
      .filterIsInstance<AgpTestSuiteTargetImpl>()
      .filter { it.testTaskConfigActions.isNotEmpty() }
      .associate { it.name to it.testTaskConfigActions.toList() }

  /** Private APIs */
  /**
   * Creates the source container of type [T] on first invocation or reuses the existing instance on subsequent DSL blocks.
   *
   * @param initializationBlock user configuration block applied on every invocation.
   * @param onCreate one-time initialization run only when the source container is first instantiated, before [initializationBlock], so that
   *   subsequent DSL blocks for the same source container do not overwrite values configured by earlier blocks.
   */
  private inline fun <reified T : TestSuiteSourceCreationConfig> addSource(
    initializationBlock: T.() -> Unit,
    onCreate: T.() -> Unit = {},
  ) {
    val existingSource = sources.filterIsInstance<T>().firstOrNull()
    if (existingSource != null) {
      initializationBlock.invoke(existingSource)
      return
    }
    dslServices.newInstance(T::class.java, name, dslServices.projectInfo.projectDirectory, dslServices.projectInfo.buildDirectory).also {
      newSources ->
      sources.add(newSources)
      onCreate.invoke(newSources)
      initializationBlock.invoke(newSources)
    }
  }
}
