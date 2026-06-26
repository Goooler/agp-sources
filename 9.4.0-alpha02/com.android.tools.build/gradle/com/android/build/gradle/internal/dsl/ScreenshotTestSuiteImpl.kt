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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.AgpTestSuiteDependencies
import com.android.build.api.dsl.AgpTestSuiteInputParameters
import com.android.build.api.dsl.ScreenshotTestSuite
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.ProviderFactory

/** Concrete implementation of [ScreenshotTestSuite] representing Compose Screenshot test suites. */
abstract class ScreenshotTestSuiteImpl @Inject constructor(val name: String, dslServices: DslServices) : ScreenshotTestSuite {

  abstract override var engineVersion: String?

  internal val dependenciesActions = mutableListOf<AgpTestSuiteDependencies.() -> Unit>()
  internal var dependenciesHandler: ((AgpTestSuiteDependencies.() -> Unit) -> Unit)? = null

  override fun dependencies(action: AgpTestSuiteDependencies.() -> Unit) {
    val handler = dependenciesHandler
    if (handler != null) {
      handler(action)
    } else {
      dependenciesActions.add(action)
    }
  }

  fun dependencies(action: Action<AgpTestSuiteDependencies>) {
    dependencies { action.execute(this) }
  }
}

open class ScreenshotAgpTestSuiteImpl
@Inject
constructor(
  private val screenshotSuite: ScreenshotTestSuiteImpl,
  dslServices: DslServices,
  private val dependencyHandler: DependencyHandler,
  private val providers: ProviderFactory,
) : AgpTestSuiteImpl(screenshotSuite.name, dslServices, true) {

  override val targetVariants: MutableList<String>
    get() = screenshotSuite.targetVariants

  init {
    // Automatically populate default engines and inputs
    useJunitEngine {
      includeEngines.add(SCREENSHOT_TEST_ENGINE_ID)
      inputs.addAll(
        listOf(
          AgpTestSuiteInputParameters.TEST_CLASSES,
          AgpTestSuiteInputParameters.TEST_CLASSPATH,
          AgpTestSuiteInputParameters.MAIN_CLASSES,
          AgpTestSuiteInputParameters.MAIN_CLASSPATH,
          AgpTestSuiteInputParameters.R_CLASS_JARS,
          AgpTestSuiteInputParameters.ANDROID_RES_DIRS,
          AgpTestSuiteInputParameters.RESOURCES_AP_ARCHIVE,
        )
      )

      val versionProvider =
        providers.provider {
          val version = screenshotSuite.engineVersion
          if (version.isNullOrBlank()) {
            dslServices.issueReporter.reportError(
              com.android.builder.errors.IssueReporter.Type.GENERIC,
              "Screenshot test engine version must be specified. e.g. engineVersion = \"0.0.1-alpha01\""
            )
            "unspecified"
          } else {
            version!!
          }
        }

      enginesDependencies.add(
        versionProvider.map { version ->
          dependencyHandler.create("com.android.tools.screenshot:screenshot-validation-junit-engine:$version")
        }
      )
      enginesDependencies.add(
        versionProvider.map { version -> dependencyHandler.create("com.android.tools.compose:compose-preview-renderer:$version") }
      )
      enginesDependencies.add(
        versionProvider.map { version -> dependencyHandler.create("com.android.tools.screenshot:screenshot-validation-api:$version") }
      )
    }

    hostJar {
      screenshotSuite.dependenciesHandler = { action -> this.dependencies(action) }
      screenshotSuite.dependenciesActions.forEach { this.dependencies(it) }
      screenshotSuite.dependenciesActions.clear()
    }
  }

  companion object {
    const val SCREENSHOT_TEST_ENGINE_ID = "preview-screenshot-test-engine"
  }
}
