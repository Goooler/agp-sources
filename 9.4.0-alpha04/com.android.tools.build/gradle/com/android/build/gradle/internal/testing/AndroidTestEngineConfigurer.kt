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

package com.android.build.gradle.internal.testing

import com.android.Version
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.AgpTestSuiteInputParameters
import com.android.build.api.testsuites.TestEngineInputProperty
import com.android.build.api.testsuites.TestSuiteExecutionClient.Companion.DEFAULT_ENV_VARIABLE
import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.component.TestVariantCreationConfig
import com.android.build.gradle.internal.test.BundleTestDataImpl
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.LegacyReportingTestSuiteTestTask
import com.android.build.gradle.tasks.TestSuiteTestTask
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions

/** Helper to configure [LegacyReportingTestSuiteTestTask] to run with Android Test Engine. */
fun configureAndroidTestEngine(
  task: LegacyReportingTestSuiteTestTask,
  creationConfig: InstrumentedTestCreationConfig,
  testData: TestData,
  subFolder: String,
) {
  val globalConfig = creationConfig.global
  val testedConfig = (creationConfig as? DeviceTestCreationConfig)?.mainVariant

  val androidTestEngineVersion = if (Version.IS_AGP_RELEASE_BRANCH) "0.1.0-alpha01" else "0.1.0-dev"

  task.classpath =
    creationConfig.services.fileCollection().also {
      it.from(
        creationConfig.services.configurations.detachedConfiguration(
          creationConfig.services.dependencies.create("com.android.tools.androidtest:android-test-engine:$androidTestEngineVersion"),
          creationConfig.services.dependencies.create(
            "com.android.tools.androidtest:android-test-engine-result-listener:$androidTestEngineVersion"
          ),
          creationConfig.services.dependencies.create("org.junit.platform:junit-platform-engine:1.12.0"),
          creationConfig.services.dependencies.create("org.junit.platform:junit-platform-launcher:1.12.0"),
        )
      )
    }

  task.useJUnitPlatform { testFramework: JUnitPlatformOptions -> testFramework.includeEngines("android-test-engine") }

  task.animationsDisabled.setDisallowChanges(creationConfig.services.provider { globalConfig.androidTestOptions.animationsDisabled })

  val isLibrary = testedConfig?.componentType?.isAar ?: false

  if (testedConfig != null && !isLibrary) {
    task.engineInputParameters.add(
      TestSuiteTestTask.AgpTestSuiteInputParameter(
        AgpTestSuiteInputParameters.TESTED_APKS,
        task.project.files(testedConfig.artifacts.get(SingleArtifact.APK)),
      )
    )
  } else if (creationConfig is TestVariantCreationConfig) {
    task.engineInputParameters.add(
      TestSuiteTestTask.AgpTestSuiteInputParameter(AgpTestSuiteInputParameters.TESTED_APKS, task.project.files(creationConfig.testedApks))
    )
  }
  task.engineInputParameters.add(
    TestSuiteTestTask.AgpTestSuiteInputParameter(
      AgpTestSuiteInputParameters.TEST_APKS,
      task.project.files(creationConfig.artifacts.get(SingleArtifact.APK)),
    )
  )

  task.engineInputProperties.put(TestEngineInputProperty.TESTED_APPLICATION_ID, testData.applicationId)
  task.engineInputProperties.put(
    AgpTestSuiteInputParameters.AAPT2_EXECUTABLE.propertyName,
    task.buildTools.aapt2ExecutableProvider().map { it.asFile.absolutePath },
  )

  task.engineInputProperties.put("android-test.instrumentation-runner-class", testData.instrumentationRunner)
  task.engineInputProperties.put("android-test.test-package-id", testData.applicationId)
  task.engineInputProperties.put("android-test.instrumentation-target-package-id", testData.instrumentationTargetPackageId)
  task.engineInputProperties.put(
    "android-test.instrumentation-args",
    testData.instrumentationRunnerArguments.map { it.entries.joinToString(",") { (k, v) -> "$k=$v" } },
  )
  task.engineInputProperties.put(
    "android-test.uninstall-after-tests",
    (!globalConfig.services.projectOptions.get(BooleanOption.ANDROID_TEST_LEAVE_APKS_INSTALLED_AFTER_RUN)).toString(),
  )
  task.engineInputProperties.put("android-test.force-aot-compilation", creationConfig.isForceAotCompilation.toString())

  task.engineInputProperties.put(
    "android-test.use-test-storage-service",
    testData.instrumentationRunnerArguments.map { it.getOrDefault("useTestStorageService", "false") },
  )
  task.engineInputProperties.put("android-test.is-test-coverage-enabled", testData.testCoverageEnabled.map { it.toString() })
  task.engineInputProperties.put(
    "android-test.coverage-file-on-device",
    testData.instrumentationRunnerArguments.map { it.getOrDefault("coverageFile", "") },
  )
  task.engineInputProperties.put(
    "android-test.coverage-dir-on-device",
    testData.instrumentationRunnerArguments.map { it.getOrDefault("coverageDir", "") },
  )
  task.engineInputProperties.put(
    "com.android.agp.test.COVERAGE_TYPE",
    if (creationConfig.services.projectOptions.get(BooleanOption.ENABLE_ON_THE_FLY_CODE_COVERAGE)) {
      "ON_THE_FLY"
    } else {
      "NONE"
    },
  )

  if (testData is BundleTestDataImpl) {
    task.apkBundle.from(testData.apkBundle)
    task.bundleModuleName.setDisallowChanges(testData.moduleName)
  }

  task.legacyTestReportingRedirectionEnabled.setDisallowChanges(
    task.project.providers.gradleProperty(LegacyReportingTestSuiteTestTask.ENABLE_UTP_REPORTING_PROPERTY).orNull?.toBoolean() ?: false
  )
  task.engineInputProperties.put(
    "android-test.listener.stream-base64-encoded-result",
    task.legacyTestReportingRedirectionEnabled.map { it.toString() },
  )

  task.engineInputProperties.put(
    "android-test.additional-test-output-dir-on-device",
    testData.instrumentationRunnerArguments.map { it.getOrDefault("additionalTestOutputDir", "") },
  )

  task.engineInputProperties.disallowChanges()

  task.engineInputPropertiesFiles.setDisallowChanges(
    task.project.layout.buildDirectory.file("intermediates/androidTest/${creationConfig.name}/$subFolder/junit_inputs.txt")
  )
  task.julConfigurationFile.setDisallowChanges(
    task.project.layout.buildDirectory.file("intermediates/androidTest/${creationConfig.name}/$subFolder/logging.properties")
  )
  task.logFile.setDisallowChanges(
    task.project.layout.buildDirectory.file("intermediates/androidTest/${creationConfig.name}/$subFolder/junit_engines_logging.txt")
  )
  task.streamingOutputFile.setDisallowChanges(
    task.project.layout.buildDirectory.file("intermediates/androidTest/${creationConfig.name}/$subFolder/streaming.txt")
  )

  task.environment(DEFAULT_ENV_VARIABLE, task.engineInputPropertiesFiles.get().asFile.absolutePath)
}
