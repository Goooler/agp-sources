/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.utp

import com.android.Version.ANDROID_TOOLS_BASE_VERSION
import com.android.build.api.instrumentation.StaticTestData
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.testing.utp.worker.RunUtpWorkAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.sdklib.BuildToolInfo
import com.android.tools.utp.gradle.api.EmulatorControlConfig
import com.android.tools.utp.gradle.api.RunUtpWorkParameters
import com.android.tools.utp.gradle.api.ShardConfig
import com.android.tools.utp.gradle.api.TargetApkConfigBundle
import com.android.tools.utp.gradle.api.TestData
import com.android.tools.utp.gradle.api.UtpDependencies
import com.android.tools.utp.gradle.api.UtpDependency
import java.io.File
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.workers.WorkerExecutor

private const val TEST_RESULT_EXIT_CODE_FILE_NAME = "test-result-exit-code.txt"
private const val TEST_RESULT_PB_FILE_NAME = "test-result.pb"

/** Runs the given runner configs using Unified Test Platform. */
fun runUtpTestSuiteAndWait(
  runnerConfigs: List<RunUtpWorkParameters.UtpRunConfig>,
  workerExecutor: WorkerExecutor,
  projectPath: String,
  variantName: String,
  resultsDir: File,
  utpDependencies: UtpDependencies,
  versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader,
  provider: ProviderFactory,
): Boolean {
  val mergedUtpResultProtoOutputFile = File(resultsDir, TEST_RESULT_PB_FILE_NAME)
  val testResultExitCodeFile = File(resultsDir, TEST_RESULT_EXIT_CODE_FILE_NAME)

  // If there are no runner configurations, there are no tests to run.
  // We write "0" (success) to the exit code file and return early to avoid
  // spinning up an expensive isolated worker process for a no-op run.
  if (runnerConfigs.isEmpty()) {
    testResultExitCodeFile.parentFile?.mkdirs()
    testResultExitCodeFile.writeText("0")
    return true
  }

  val enableUtpTestReportingForAndroidStudio =
    provider.gradleProperty("com.android.tools.utp.GradleAndroidProjectResolverExtension.enable").orNull?.toBoolean() ?: false

  val isOnTheFlyCoverageEnabled =
    provider.gradleProperty(BooleanOption.ENABLE_ON_THE_FLY_CODE_COVERAGE.propertyName).orNull?.toBoolean() ?: false

  val adbPath = versionedSdkLoader.adbExecutableProvider.get().asFile.absolutePath
  val aapt2Path = versionedSdkLoader.buildToolInfoProvider.get().getPath(BuildToolInfo.PathId.AAPT)

  val serials = runnerConfigs.map { it.deviceSerialNumber.get() }

  val workQueue =
    workerExecutor.processIsolation { spec ->
      spec.classpath.fromDisallowChanges(utpDependencies.gradleWorkAction)
      spec.forkOptions { fork ->
        if (enableUtpTestReportingForAndroidStudio) {
          fork.systemProperty("android-test.listener.stream-base64-encoded-result", "true")
        }

        fork.systemProperty("android-test.adb-path", adbPath)
        fork.systemProperty("android-test.aapt2-path", aapt2Path)
        fork.systemProperty("android-test.device-serials", serials.joinToString(","))

        // Common configurations (using first config as representative, assuming they are mostly same)
        val firstConfig = runnerConfigs.first()
        fork.systemProperty("android-test.instrumentation-runner-class", firstConfig.testData.get().instrumentationRunner)
        fork.systemProperty("android-test.test-package-id", firstConfig.testData.get().applicationId)
        fork.systemProperty("android-test.instrumentation-target-package-id", firstConfig.testData.get().instrumentationTargetPackageId)
        firstConfig.testData.get().testedApplicationId?.let { fork.systemProperty("com.android.junit.engine.tested.application.id", it) }
        val instArgs = firstConfig.testData.get().instrumentationRunnerArguments
        if (instArgs.isNotEmpty()) {
          fork.systemProperty("android-test.instrumentation-args", instArgs.map { "${it.key}=${it.value}" }.joinToString(","))
        }
        val useTestStorageService = instArgs["useTestStorageService"]?.toBoolean() ?: false
        fork.systemProperty("android-test.use-test-storage-service", useTestStorageService.toString())
        fork.systemProperty("android-test.is-test-coverage-enabled", firstConfig.testData.get().isTestCoverageEnabled.toString())
        if (firstConfig.testData.get().isTestCoverageEnabled) {
          val coverageType = if (isOnTheFlyCoverageEnabled) "ON_THE_FLY" else "NONE"
          fork.systemProperty("android-test.coverage-type", coverageType)
        }
        fork.systemProperty("android-test.force-aot-compilation", firstConfig.forceCompilation.get().toString())
        fork.systemProperty("android-test.uninstall-after-tests", firstConfig.uninstallApksAfterTest.get().toString())
        if (firstConfig.testData.get().isTestCoverageEnabled) {
          val useOrchestrator = firstConfig.useOrchestrator.get()
          val customCoveragePath = instArgs["coverageFilePath"] ?: instArgs["coverageFile"]
          if (customCoveragePath != null) {
            if (useOrchestrator) {
              fork.systemProperty("android-test.coverage-dir-on-device", customCoveragePath)
            } else {
              fork.systemProperty("android-test.coverage-file-on-device", customCoveragePath)
            }
          }
        }
        fork.systemProperty("com.android.junit.engine.results.dir", resultsDir.absolutePath)
        if (firstConfig.useOrchestrator.get()) {
          fork.systemProperty("android-test.execution-mode", "ANDROIDX_TEST_ORCHESTRATOR")
        }
        fork.systemProperty("android-test.animations-disabled", firstConfig.testData.get().animationsDisabled.toString())
        fork.systemProperty("android-test.instrument-in-pcc", firstConfig.privateComputeCoreInstrumentationEnabled)
        if (firstConfig.additionalTestOutputOnDeviceDir.isPresent) {
          fork.systemProperty("android-test.additional-test-output-dir-on-device", firstConfig.additionalTestOutputOnDeviceDir.get())
        }

        // Device-specific configurations
        runnerConfigs.forEach { config ->
          val serial = config.deviceSerialNumber.get()
          fork.systemProperty("android-test.device-id[$serial]", config.deviceId.get())
          fork.systemProperty("android-test.results-dir[$serial]", config.outputDir.get().asFile.absolutePath)
          fork.systemProperty("android-test.coverage-dir-on-host[$serial]", config.coverageOutputDir.get().asFile.absolutePath)
          if (config.additionalTestOutputDir.isPresent) {
            fork.systemProperty(
              "android-test.additional-test-output-dir-on-host[$serial]",
              config.additionalTestOutputDir.get().asFile.absolutePath,
            )
          }

          val appApks = config.targetApkConfigBundle.get().appApks
          if (appApks.isNotEmpty()) {
            fork.systemProperty("android-test.tested-apks[$serial]", appApks.joinToString(",") { it.absolutePath })
          }
          fork.systemProperty("android-test.test-apks[$serial]", config.testData.get().testApk.absolutePath)

          val helperApks = config.helperApks.files
          if (helperApks.isNotEmpty()) {
            fork.systemProperty("android-test.test-util-apks[$serial]", helperApks.joinToString(",") { it.absolutePath })
          }

          val installOptions = config.additionalInstallOptions.get()
          if (installOptions.isNotEmpty()) {
            fork.systemProperty("android-test.apk-install-options[$serial]", installOptions.joinToString(","))
          }

          fork.systemProperty("android-test.install-timeout-ms[$serial]", ((config.installApkTimeout.orNull ?: 0) * 1000).toString())
        }

        // Propagate HOME environment variable to ensure:
        // 1. Bazel sandbox compatibility (reusing the writable HOME directory set by Bazel).
        // 2. ADB/Emulator configurations (e.g. adbkey authentication) can be resolved.
        System.getenv("HOME")?.let { fork.environment("HOME", it) }
      }
    }

  workQueue.submit(RunUtpWorkAction::class.java) { params ->
    params.utpRunConfigs.setDisallowChanges(runnerConfigs)
    params.utpDependencies.setDisallowChanges(utpDependencies)
    params.projectPath.setDisallowChanges(projectPath)
    params.variantName.setDisallowChanges(variantName)
    params.xmlTestReportOutputDirectory.fileValue(resultsDir).disallowChanges()
    params.mergedUtpResultProtoOutputFile.fileValue(mergedUtpResultProtoOutputFile).disallowChanges()
    params.testResultExitCodeFile.fileValue(testResultExitCodeFile).disallowChanges()
    params.androidSdkDirectory.setDisallowChanges(versionedSdkLoader.sdkDirectoryProvider)
    params.adbExecutable.setDisallowChanges(versionedSdkLoader.adbExecutableProvider)
    params.aaptExecutable
      .fileValue(File(versionedSdkLoader.buildToolInfoProvider.get().getPath(BuildToolInfo.PathId.AAPT)))
      .disallowChanges()
    params.dexdumpExecutable.fileValue(File(versionedSdkLoader.buildToolInfoProvider.get().getPath(BuildToolInfo.PathId.DEXDUMP)))
  }

  workQueue.await()

  return testResultExitCodeFile.exists() && testResultExitCodeFile.isFile && testResultExitCodeFile.readText().trim().toInt() == 0
}

/** Factory function to create and configure a [RunUtpWorkParameters.UtpRunConfig] instance. */
fun createUtpRunConfig(
  objectFactory: ObjectFactory,
  deviceId: String,
  deviceName: String,
  deviceSerialNumber: String,
  testData: StaticTestData,
  targetApkConfigBundle: TargetApkConfigBundle,
  additionalInstallOptions: Iterable<String>,
  helperApks: Iterable<File>,
  uninstallIncompatibleApks: Boolean,
  outputDir: File,
  emulatorControlConfig: EmulatorControlConfig,
  coverageOutputDir: File,
  useOrchestrator: Boolean,
  forceCompilation: Boolean,
  additionalTestOutputDir: File?,
  additionalTestOutputOnDeviceDir: String?,
  installApkTimeout: Int?,
  uninstallApksAfterTest: Boolean,
  reinstallIncompatibleApksBeforeTest: Boolean,
  shardConfig: ShardConfig?,
  privateComputeCoreInstrumentationEnabled: Boolean,
): RunUtpWorkParameters.UtpRunConfig {
  val utpRunConfig = objectFactory.newInstance(RunUtpWorkParameters.UtpRunConfig::class.java)

  utpRunConfig.deviceId.setDisallowChanges(deviceId)
  utpRunConfig.deviceName.setDisallowChanges(deviceName)
  utpRunConfig.deviceShardName.setDisallowChanges(
    if (shardConfig == null) {
      deviceName
    } else {
      "${deviceName}_${shardConfig.index}"
    }
  )
  utpRunConfig.utpResultProtoOutputFile.fileValue(File(outputDir, TEST_RESULT_PB_FILE_NAME)).disallowChanges()
  utpRunConfig.deviceSerialNumber.setDisallowChanges(deviceSerialNumber)
  utpRunConfig.testData.setDisallowChanges(testData.toWorkActionTestData())
  utpRunConfig.targetApkConfigBundle.setDisallowChanges(targetApkConfigBundle)
  utpRunConfig.additionalInstallOptions.setDisallowChanges(additionalInstallOptions)
  utpRunConfig.helperApks.fromDisallowChanges(helperApks)
  utpRunConfig.uninstallIncompatibleApks.setDisallowChanges(uninstallIncompatibleApks)
  utpRunConfig.outputDir.fileValue(outputDir).disallowChanges()
  utpRunConfig.emulatorControlConfig.setDisallowChanges(emulatorControlConfig)
  utpRunConfig.coverageOutputDir.fileValue(coverageOutputDir).disallowChanges()
  utpRunConfig.useOrchestrator.setDisallowChanges(useOrchestrator)
  utpRunConfig.forceCompilation.setDisallowChanges(forceCompilation)
  utpRunConfig.additionalTestOutputDir.fileValue(additionalTestOutputDir).disallowChanges()
  utpRunConfig.additionalTestOutputOnDeviceDir.setDisallowChanges(additionalTestOutputOnDeviceDir)
  utpRunConfig.installApkTimeout.setDisallowChanges(installApkTimeout)
  utpRunConfig.uninstallApksAfterTest.setDisallowChanges(uninstallApksAfterTest)
  utpRunConfig.reinstallIncompatibleApksBeforeTest.setDisallowChanges(reinstallIncompatibleApksBeforeTest)
  utpRunConfig.shardConfig.setDisallowChanges(shardConfig)
  utpRunConfig.privateComputeCoreInstrumentationEnabled.setDisallowChanges(privateComputeCoreInstrumentationEnabled)

  return utpRunConfig
}

private fun StaticTestData.toWorkActionTestData(): TestData {
  return TestData(
    instrumentationTargetPackageId = this.instrumentationTargetPackageId,
    testedApplicationId = this.testedApplicationId,
    applicationId = this.applicationId,
    instrumentationRunner = this.instrumentationRunner,
    testApk = this.testApk,
    instrumentationRunnerArguments = this.instrumentationRunnerArguments,
    isTestCoverageEnabled = this.isTestCoverageEnabled,
    animationsDisabled = this.animationsDisabled,
  )
}

/** Looks for UTP configurations in a project, creates and add it to the project if missing. */
fun maybeCreateUtpConfigurations(configurations: ConfigurationContainer, dependencies: DependencyHandler) {
  UtpDependency.entries.forEach { utpDependency ->
    if (!configurations.names.contains(utpDependency.configurationName)) {
      configurations.register(utpDependency.configurationName) {
        it.isVisible = false
        it.isTransitive = true
        it.isCanBeConsumed = false
        it.description = "A configuration to resolve the Unified Test Platform dependencies."
      }
      dependencies.add(utpDependency.configurationName, utpDependency.mavenCoordinate(ANDROID_TOOLS_BASE_VERSION))
    }
  }
}

/** Resolves the UTP dependencies and populates this [UtpDependencies] object from the given [ConfigurationContainer]. */
fun UtpDependencies.resolveDependencies(configurationsContainer: ConfigurationContainer) {
  UtpDependency.entries.forEach { utpDependency ->
    utpDependency.mapperFunc(this).from(configurationsContainer.getByName(utpDependency.configurationName))
  }
}
