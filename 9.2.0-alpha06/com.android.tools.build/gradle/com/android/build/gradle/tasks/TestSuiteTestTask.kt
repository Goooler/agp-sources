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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.Version
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.AgpTestSuiteInputParameters
import com.android.build.api.testsuites.TestEngineInputProperty
import com.android.build.api.testsuites.TestSuiteExecutionClient.Companion.DEFAULT_ENV_VARIABLE
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.TestSuiteSourceSet
import com.android.build.api.variant.impl.JUnitEngineSpecImplForVariant
import com.android.build.api.variant.impl.TestSuiteSourceContainer
import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.BuildToolsExecutableInput
import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.component.TestSuiteTargetCreationConfig
import com.android.build.gradle.internal.computeAbiFromArchitecture
import com.android.build.gradle.internal.computeAvdName
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask.DeviceProviderFactory
import com.android.build.gradle.internal.tasks.GlobalTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.getApkFiles
import com.android.build.gradle.internal.test.BundleTestDataImpl
import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.test.report.TestReport
import com.android.build.gradle.internal.testing.TestData
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.BuilderConstants
import com.android.builder.testing.api.DeviceConfigProvider
import com.android.builder.testing.api.DeviceConfigProviderImpl
import com.android.builder.testing.api.DeviceException
import java.io.File
import java.util.Locale
import java.util.Properties
import kotlin.collections.asIterable
import kotlin.collections.joinToString
import kotlin.collections.plus
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import shadow.bundletool.com.android.utils.PathUtils

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class TestSuiteTestTask : Test(), GlobalTask {

  @get:Nested abstract val engineInputParameters: ListProperty<AgpTestSuiteInputParameter>

  @get:Nested abstract val buildTools: BuildToolsExecutableInput

  @get:Input abstract val engineInputProperties: MapProperty<String, String>

  @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE) abstract val sourceFolders: ConfigurableFileCollection

  @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE) abstract val binaryFolders: ConfigurableFileCollection

  /**
   * The app bundle file used for dynamic feature testing.
   *
   * For dynamic features, we need to extract a set of APKs (base + feature + other splits) that are compatible with the target device. This
   * extraction happens at execution time because the set of APKs depends on the connected device's configuration.
   */
  @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE) abstract val apkBundle: ConfigurableFileCollection

  /** Comma-separated list of paths to utility APKs to be installed before testing. */
  @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE) abstract val testUtilApks: ConfigurableFileCollection

  /** The module name within the bundle that contains the tests. */
  @get:Input @get:Optional abstract val bundleModuleName: Property<String>

  /** The execution mode for the test suite. */
  @get:Input @get:Optional abstract val executionMode: Property<String>

  @get:OutputFile abstract val engineInputPropertiesFiles: RegularFileProperty

  @get:OutputFile abstract val julConfigurationFile: RegularFileProperty

  @get:OutputFile abstract val logFile: RegularFileProperty

  @get:OutputFile abstract val streamingOutputFile: RegularFileProperty

  @get:OutputDirectory abstract val resultsDir: DirectoryProperty

  @get:OutputDirectory abstract val xmlResultsDir: DirectoryProperty

  @get:OutputDirectory abstract val coverageDir: DirectoryProperty

  @get:Nested abstract val deviceProviderFactory: DeviceProviderFactory

  @get:Internal abstract val modulePath: Property<String>

  @get:Internal abstract val testedVariantName: Property<String>

  @get:Internal abstract val testSuiteName: Property<String>

  @get:Internal abstract val testSuiteTarget: Property<String>

  /**
   * Specifies the target devices for test execution using a comma-separated list of serial numbers.
   *
   * When this property is set, tests will run only on the devices corresponding to the given serials. If this property is not provided or
   * is null, tests will be executed on all currently connected and online devices.
   */
  @get:Input @get:Optional abstract val androidDeviceSerials: Property<String>

  @get:Nested abstract val managedDevices: ListProperty<ManagedVirtualDevice>

  @get:Internal abstract val avdService: Property<AvdComponentsBuildService>

  @TaskAction
  override fun executeTests() {

    // I suspect Gradle considers that it is the responsibility of the test engine to clean
    // up the output folders but the fact is that we never run in incremental mode so keeping
    // the previous runs output folders is of little to no interest, I will therefore clean
    // up those folders, although we should check with Gradle what is their official policy.
    // In fact, I am suspecting that since the test engine is running in a separate process,
    // Gradle has no way to monitor what is written to these directories so it leaves it
    // untouched at the next execution.
    PathUtils.deleteRecursivelyIfExists(resultsDir.get().asFile.toPath())
    PathUtils.deleteRecursivelyIfExists(coverageDir.get().asFile.toPath())
    logFile.get().asFile.delete()

    val streamingFile = streamingOutputFile.get().asFile
    streamingFile.parentFile.mkdirs()
    if (streamingFile.exists()) {
      streamingFile.delete()
    }
    streamingFile.createNewFile()

    val engineInputParameters: List<TestEngineInputProperty> =
      engineInputParameters.get().map { inputProperty ->
        TestEngineInputProperty(inputProperty.type.propertyName, inputProperty.value.get().asFile.absolutePath)
      }

    doExecuteTests(engineInputParameters)
  }

  protected open fun doExecuteTests(engineInputParameters: List<TestEngineInputProperty>) {
    // only get the connected devices if the test requested an APK.
    if (
      engineInputParameters.any { inputParameter ->
        inputParameter.name == AgpTestSuiteInputParameters.TESTED_APKS.propertyName ||
          inputParameter.name == AgpTestSuiteInputParameters.TESTING_APK.propertyName
      }
    ) {
      provisionDevicesAndExecute { onlineDevices -> executeTests(engineInputParameters, onlineDevices) }
    } else {
      executeTests(engineInputParameters)
    }
  }

  private fun provisionDevicesAndExecute(onDevicesReady: (onlineDevices: List<DeviceTestTarget>) -> Unit) {
    provisionConnectedDevicesAndExecute { connectedDevices ->
      provisionManagedDevicesAndExecute { managedDevices -> onDevicesReady(connectedDevices + managedDevices) }
    }
  }

  private fun provisionConnectedDevicesAndExecute(onDevicesReady: (onlineDevices: List<DeviceTestTarget>) -> Unit) {
    val deviceProvider = deviceProviderFactory.getDeviceProvider(buildTools.adbExecutable(), androidDeviceSerials.orNull)
    try {
      deviceProvider.use {
        val targets =
          deviceProvider.devices.map { connector -> DeviceTestTarget(connector.serialNumber, DeviceConfigProviderImpl(connector)) }
        onDevicesReady(targets)
      }
    } catch (_: DeviceException) {
      onDevicesReady(listOf())
    }
  }

  private fun provisionManagedDevicesAndExecute(onDevicesReady: (onlineDeviceSerials: List<DeviceTestTarget>) -> Unit) {
    provisionManagedDevicesAndExecute(managedDevices.get().asIterable().iterator(), mutableListOf(), onDevicesReady)
  }

  private fun provisionManagedDevicesAndExecute(
    iterator: Iterator<ManagedVirtualDevice>,
    onlineDevices: MutableList<DeviceTestTarget>,
    onDevicesReady: (onlineDeviceSerials: List<DeviceTestTarget>) -> Unit,
  ) {
    if (!iterator.hasNext()) {
      onDevicesReady(onlineDevices)
      return
    }
    val device = iterator.next()
    val avdName = computeAvdName(device)
    avdService.get().runWithAvd(avdName) { onlineDeviceSerial ->
      onlineDevices.add(DeviceTestTarget(onlineDeviceSerial, DslDeviceConfigProvider(device)))
      provisionManagedDevicesAndExecute(iterator, onlineDevices, onDevicesReady)
    }
  }

  private fun executeTests(engineInputParameters: List<TestEngineInputProperty>, onlineDevices: List<DeviceTestTarget> = listOf()) {
    val standardInputs =
      mutableListOf(
        TestEngineInputProperty(TestEngineInputProperty.LOGGING_FILE, providerToPath(logFile)),
        TestEngineInputProperty(TestEngineInputProperty.STREAMING_FILE, providerToPath(streamingOutputFile)),
        TestEngineInputProperty(TestEngineInputProperty.RESULTS_DIR, providerToPath(resultsDir)),
        TestEngineInputProperty(TestEngineInputProperty.COVERAGE_DIR, providerToPath(coverageDir)),
        TestEngineInputProperty(
          AgpTestSuiteInputParameters.ADB_EXECUTABLE.propertyName,
          buildTools.adbExecutable().get().asFile.absolutePath,
        ),
      )

    if (!testUtilApks.isEmpty) {
      standardInputs.add(
        TestEngineInputProperty(
          AgpTestSuiteInputParameters.TEST_UTIL_APKS.propertyName,
          testUtilApks.joinToString(separator = ",") { it.absolutePath },
        )
      )
    }

    if (executionMode.isPresent) {
      standardInputs.add(TestEngineInputProperty(AgpTestSuiteInputParameters.ANDROID_TEST_EXECUTION_MODE.propertyName, executionMode.get()))
    }

    if (!sourceFolders.isEmpty) {
      standardInputs.add(
        TestEngineInputProperty(
          TestEngineInputProperty.SOURCE_FOLDERS,
          sourceFolders.files.joinToString(separator = File.pathSeparator) { it.absolutePath },
        )
      )
    }

    if (!binaryFolders.isEmpty) {
      standardInputs.add(
        TestEngineInputProperty(
          TestEngineInputProperty.BINARY_FOLDERS,
          binaryFolders.files.joinToString(separator = File.pathSeparator) { it.absolutePath },
        )
      )
    }

    if (onlineDevices.isNotEmpty()) {
      val onlineDeviceSerials = onlineDevices.joinToString(",") { it.serialNumber }
      standardInputs.add(TestEngineInputProperty(TestEngineInputProperty.SERIAL_IDS, onlineDeviceSerials))

      if (!apkBundle.isEmpty) {
        onlineDevices.forEach { target ->
          val extractedApks = getApkFiles(apkBundle.singleFile.toPath(), target.deviceConfigProvider, bundleModuleName.orNull)
          val apksString = extractedApks.joinToString(",") { it.toAbsolutePath().toString() }
          standardInputs.add(
            TestEngineInputProperty("${AgpTestSuiteInputParameters.TESTED_APKS.propertyName}[${target.serialNumber}]", apksString)
          )
        }
      }
    }

    // Configures java.util.logging to redirect all output from the test execution process
    // to a separate log file. Note that Gradle redirects java.util.logging to stderr by
    // default, which is why we need this configuration file to prevent JUL logs from
    // cluttering the Gradle console.
    if (!this.systemProperties.containsKey("java.util.logging.config.file")) {
      val julConfigFile = julConfigurationFile.get().asFile
      julConfigFile.parentFile.mkdirs()
      julConfigFile.writeText(
        """
        handlers = java.util.logging.FileHandler, java.util.logging.ConsoleHandler
        .level = INFO
        java.util.logging.FileHandler.level = INFO
        java.util.logging.FileHandler.pattern = ${logFile.get().asFile.absolutePath.replace("\\", "/")}
        java.util.logging.FileHandler.formatter = java.util.logging.SimpleFormatter
        java.util.logging.FileHandler.append = true
        java.util.logging.ConsoleHandler.level = INFO
        java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter
        """
          .trimIndent()
      )
      this.systemProperty("java.util.logging.config.file", julConfigFile.absolutePath)
    }

    // write all the input properties for the junit engine. This mean the input properties
    // that were requested through the TestSuite DSL/Variant APIs but also the default ones
    // that are always provided.
    AgpTestSuiteInputsSerializer.serialize(
      engineInputProperties = engineInputProperties.get(),
      engineInputParameters = engineInputParameters.plus(standardInputs),
      into = engineInputPropertiesFiles.asFile.get(),
    )

    try {
      super.executeTests()
    } finally {
      val xmlResultsDirFile = this.xmlResultsDir.get().asFile
      val htmlOutputDirFile = this.reports.html.outputLocation.get().asFile

      // When android.experimental.androidTest.builtin_test_platform=true, XMLs might be in the root
      // results directory or in device-specific subdirectories (alongside other artifacts).
      // We need to aggregate all of them to get a complete HTML report.
      val resultDirs = mutableListOf(xmlResultsDirFile)
      xmlResultsDirFile.listFiles()?.filter { it.isDirectory }?.let { resultDirs.addAll(it) }

      val report = TestReport(ReportType.SINGLE_FLAVOR, resultDirs, htmlOutputDirFile)
      report.generateReport()

      val metadataDir = xmlResultsDirFile.also { it.mkdirs() }
      val metadataFile = File(metadataDir, TEST_SUITE_METADATA_FILE)

      val metadataContent =
        """
              $TEST_SUITE_METADATA_MODULE_KEY=${this.modulePath.get()}
              $TEST_SUITE_METADATA_VARIANT_KEY=${this.testedVariantName.get()}
              $TEST_SUITE_METADATA_SUITE_KEY=${this.testSuiteName.get()}
              $TEST_SUITE_METADATA_TARGET_KEY=${this.testSuiteTarget.get()}
          """
          .trimIndent()
      metadataFile.writeText(metadataContent)

      // Also write metadata to coverage directory so that the coverage collection task can identify the suite
      val coverageMetadataDir = this.coverageDir.get().asFile.also { it.mkdirs() }
      val coverageMetadataFile = File(coverageMetadataDir, TEST_SUITE_METADATA_FILE)
      coverageMetadataFile.writeText(metadataContent)
    }
  }

  private fun providerToPath(value: Provider<out FileSystemLocation>): String = value.get().asFile.absolutePath

  class CreationAction(val creationConfig: TestSuiteCreationConfig, val testSuiteTarget: TestSuiteTargetCreationConfig) :
    GlobalTaskCreationAction<LegacyReportingTestSuiteTestTask>() {

    override val name: String
      get() = testSuiteTarget.testTaskName

    override val type: Class<LegacyReportingTestSuiteTestTask> = LegacyReportingTestSuiteTestTask::class.java

    override fun configure(task: LegacyReportingTestSuiteTestTask) {
      super.configure(task)
      task.group = JavaBasePlugin.VERIFICATION_GROUP
      task.outputs.upToDateWhen { false }

      val classesDir = task.project.layout.buildDirectory.file(task.name)
      task.testClassesDirs =
        creationConfig.services.fileCollection().also { fileCollection ->
          fileCollection.from(classesDir)
          creationConfig.sourceContainers.forEach { sourceContainer ->
            fileCollection.from(
              sourceContainer.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).getFinalArtifacts(ScopedArtifact.POST_COMPILATION_CLASSES)
            )
          }
        }
      task.classpath =
        creationConfig.services.fileCollection().also { fileCollection ->
          fileCollection.from(classesDir)
          fileCollection.from(
            creationConfig.testedVariant.artifacts
              .forScope(ScopedArtifacts.Scope.PROJECT)
              .getFinalArtifacts(ScopedArtifact.POST_COMPILATION_CLASSES)
          )
          creationConfig.sourceContainers.forEach { sourceContainer ->
            fileCollection.from(sourceContainer.suiteSourceClasspath.runtimeClasspath)
            fileCollection.from(
              sourceContainer.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).getFinalArtifacts(ScopedArtifact.POST_COMPILATION_CLASSES)
            )
          }
        }

      // Get all project properties, and system properties (possibly overriding project
      // properties) and register them for the test engine.
      creationConfig.services.projectOptions.get(StringOption.TEST_SUITE_TEST_TASK_ADDITIONAL_INPUTS_FILE)?.let { additionalInputFilePath ->
        task.systemProperty(StringOption.TEST_SUITE_TEST_TASK_ADDITIONAL_INPUTS_FILE.propertyName, additionalInputFilePath)
        // add the file to the task's inputs.
        task.inputs.file(additionalInputFilePath)
      }

      task.androidDeviceSerials.setDisallowChanges(task.project.providers.environmentVariable("ANDROID_SERIAL"))

      task.executionMode.setDisallowChanges(creationConfig.global.androidTestOptions.execution)

      val androidTestUtil = task.project.configurations.findByName(SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION)
      if (androidTestUtil != null) {
        task.testUtilApks.from(androidTestUtil)
      }

      val localDevices = creationConfig.global.androidTestOptions.managedDevices.localDevices
      testSuiteTarget.targetDevices.forEach { task.managedDevices.add(localDevices.getByName(it) as ManagedVirtualDevice) }
      task.managedDevices.disallowChanges()

      val testedVariant = creationConfig.testedVariant
      val junitEngineSpec = (creationConfig.junitEngineSpec as JUnitEngineSpecImplForVariant)
      junitEngineSpec.inputs.forEach { inputParameter: AgpTestSuiteInputParameters ->
        when (inputParameter) {
          AgpTestSuiteInputParameters.MERGED_MANIFEST -> {
            task.engineInputParameters.add(
              AgpTestSuiteInputParameter(
                AgpTestSuiteInputParameters.MERGED_MANIFEST,
                testedVariant.artifacts.get(SingleArtifact.MERGED_MANIFEST),
              )
            )
          }

          AgpTestSuiteInputParameters.TESTED_APKS -> {
            task.engineInputParameters.add(
              AgpTestSuiteInputParameter(AgpTestSuiteInputParameters.TESTED_APKS, testedVariant.artifacts.get(SingleArtifact.APK))
            )
          }
          AgpTestSuiteInputParameters.ADB_EXECUTABLE -> {
            // do nothing so far, we always do it but it might change in the near future.
          }

          else -> {
            println("I don't know of this parameter $inputParameter")
          }
        }
      }

      // always wire adb inputs
      // TODO: Find ways to do this on demand.
      task.buildTools.initialize(
        task,
        creationConfig.services.buildServiceRegistry,
        creationConfig.global.compileSdkHashString,
        creationConfig.global.buildToolsRevision,
      )

      task.engineInputProperties.set(junitEngineSpec.inputProperties)
      // add default properties.
      task.engineInputProperties.put(TestEngineInputProperty.TESTED_APPLICATION_ID, testedVariant.applicationId)
      task.engineInputProperties.disallowChanges()

      task.useJUnitPlatform { testFramework: JUnitPlatformOptions ->
        testFramework.includeEngines(*creationConfig.junitEngineSpec.includeEngines.toTypedArray())
        testFramework.excludeEngines("junit-jupiter")
      }
      creationConfig.sourceContainers.forEach { sourceContainer: TestSuiteSourceContainer ->
        when (val sourceSet = sourceContainer.source) {
          is TestSuiteSourceSet.Assets -> {
            task.sourceFolders.from(sourceSet.get().all)
            task.testDefinitionDirs.from(sourceSet.get().all)
            task.failOnNoDiscoveredTests.setDisallowChanges(false)
          }
          is TestSuiteSourceSet.HostJar -> {
            task.binaryFolders.from(
              sourceContainer.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).getFinalArtifacts(ScopedArtifact.POST_COMPILATION_CLASSES)
            )
          }
          is TestSuiteSourceSet.TestApk -> throw RuntimeException("Not implemented")
        }
      }

      task.engineInputParameters.disallowChanges()
      // TODO : Improve file handling by using Artifacts APIs.
      task.engineInputPropertiesFiles.setDisallowChanges(
        task.project.layout.buildDirectory.file("intermediates/${testedVariant.name}/$name/junit_inputs.txt")
      )
      task.julConfigurationFile.setDisallowChanges(
        task.project.layout.buildDirectory.file("intermediates/${testedVariant.name}/$name/logging.properties")
      )
      task.logFile.setDisallowChanges(
        task.project.layout.buildDirectory.file("intermediates/${testedVariant.name}/$name/junit_engines_logging.txt")
      )
      task.streamingOutputFile.setDisallowChanges(
        task.project.layout.buildDirectory.file("intermediates/${testedVariant.name}/$name/streaming.txt")
      )
      task.resultsDir.setDisallowChanges(task.project.layout.buildDirectory.dir("intermediates/${testedVariant.name}/$name/results"))
      task.environment(DEFAULT_ENV_VARIABLE, task.engineInputPropertiesFiles.get().asFile.absolutePath)
      task.environment("junit.platform.commons.logging.level", "debug")
      task.deviceProviderFactory.timeOutInMs.setDisallowChanges(10000)

      task.avdService.setDisallowChanges(getBuildService(creationConfig.services.buildServiceRegistry))

      // TODO : Provide this as a DSL setting
      val debugJunitEngine = System.getenv("DEBUG_JUNIT_ENGINE")
      if (!debugJunitEngine.isNullOrEmpty()) {
        val serverArg: String = if (debugJunitEngine.equals("socket-listen", ignoreCase = true)) "n" else "y"
        task.jvmArgs("-agentlib:jdwp=transport=dt_socket,server=$serverArg,suspend=y,address=5006")
      }

      val testTaskReports = task.reports
      // Set html to true so that Gradle's error message contains clickable link to the html file.
      testTaskReports.html.required.setDisallowChanges(true)
      testTaskReports.junitXml.required.setDisallowChanges(true)
      val xmlReport = testTaskReports.junitXml
      xmlReport.outputLocation.setDisallowChanges(task.xmlResultsDir)

      val htmlReport = testTaskReports.html
      htmlReport.outputLocation.fileProvider(creationConfig.services.projectInfo.getTestReportFolder().map { it.dir(task.name).asFile })

      task.modulePath.setDisallowChanges(creationConfig.services.projectInfo.path)
      task.testedVariantName.setDisallowChanges(creationConfig.testedVariant.name)
      task.testSuiteName.setDisallowChanges(creationConfig.name)
      task.testSuiteTarget.setDisallowChanges(testSuiteTarget.name)

      // This Gradle property key is hard coded in Android Studio.
      // We will remove it once Android Studio can consume test report using
      // the tooling api.
      task.legacyTestReportingRedirectionEnabled.setDisallowChanges(
        task.project.providers.gradleProperty(LegacyReportingTestSuiteTestTask.ENABLE_UTP_REPORTING_PROPERTY).orNull?.toBoolean() ?: false
      )
    }

    override fun handleProvider(taskProvider: TaskProvider<LegacyReportingTestSuiteTestTask>) {
      super.handleProvider(taskProvider)

      creationConfig.testedVariant.artifacts
        .use(taskProvider)
        .wiredWith(TestSuiteTestTask::xmlResultsDir)
        .toAppendTo(InternalMultipleArtifactType.TEST_SUITE_RESULTS)

      creationConfig.testedVariant.artifacts
        .use(taskProvider)
        .wiredWith(TestSuiteTestTask::coverageDir)
        .toAppendTo(InternalMultipleArtifactType.TEST_SUITE_CODE_COVERAGE)
    }
  }

  /**
   * Specialized [GlobalTaskCreationAction] to register the legacy 'connectedAndroidTest' task using the newer [TestSuiteTestTask]
   * implementation.
   *
   * This allows the transition from the legacy [DeviceProviderInstrumentTestTask] to the JUnit Platform-based execution while maintaining
   * backward compatibility for the default 'connected' check path, without requiring users to explicitly configure the test suite DSL.
   */
  class ConnectedTestSuiteCreationAction(
    private val creationConfig: DeviceTestCreationConfig,
    private val testData: TestData,
    private val connectedCheckSerials: Provider<List<String>>,
  ) : GlobalTaskCreationAction<LegacyReportingTestSuiteTestTask>() {

    override val name: String
      get() = creationConfig.computeTaskNameInternal("connected")

    override val type: Class<LegacyReportingTestSuiteTestTask> = LegacyReportingTestSuiteTestTask::class.java

    override fun configure(task: LegacyReportingTestSuiteTestTask) {
      super.configure(task)

      val globalConfig = creationConfig.global

      task.group = JavaBasePlugin.VERIFICATION_GROUP
      task.description = "Installs and runs the tests for ${creationConfig.mainVariant.name} on connected devices."
      task.outputs.upToDateWhen { false }

      task.testSuiteName.setDisallowChanges("androidTest")
      task.testSuiteTarget.setDisallowChanges("connected")
      task.testedVariantName.setDisallowChanges(creationConfig.mainVariant.name)
      task.modulePath.setDisallowChanges(creationConfig.services.projectInfo.path)

      task.deviceProviderFactory.timeOutInMs.setDisallowChanges(globalConfig.installationOptions.timeOutInMs)

      task.buildTools.initialize(task, creationConfig)

      task.androidDeviceSerials.setDisallowChanges(connectedCheckSerials.map { it.joinToString(",") })

      task.executionMode.setDisallowChanges(globalConfig.androidTestOptions.execution)

      val androidTestUtil = task.project.configurations.findByName(SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION)
      if (androidTestUtil != null) {
        task.testUtilApks.from(androidTestUtil)
      }

      // Gradle's Test task requires a non-empty testDefinitionDirs to avoid being skipped
      // as NO-SOURCE. For Android instrumentation tests, discovery occurs dynamically
      // on the device via 'am instrument' at execution time, which is opaque to Gradle.
      //
      // We use the test APK directory as a stable, flavor-agnostic "trigger" to ensure
      // task execution while letting AndroidTestEngine handle the actual test orchestration.
      task.testDefinitionDirs.from(creationConfig.artifacts.get(SingleArtifact.APK))

      val androidTestEngineVersion = if (Version.IS_AGP_RELEASE_BRANCH) "0.1.0" else "0.1.0-dev"

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

      if (creationConfig.mainVariant.artifacts.get(SingleArtifact.APK).isPresent) {
        task.engineInputParameters.add(
          AgpTestSuiteInputParameter(AgpTestSuiteInputParameters.TESTED_APKS, creationConfig.mainVariant.artifacts.get(SingleArtifact.APK))
        )
      }
      task.engineInputParameters.add(
        AgpTestSuiteInputParameter(AgpTestSuiteInputParameters.TESTING_APK, creationConfig.artifacts.get(SingleArtifact.APK))
      )

      task.engineInputProperties.put(TestEngineInputProperty.TESTED_APPLICATION_ID, testData.applicationId)
      task.engineInputProperties.put(
        AgpTestSuiteInputParameters.AAPT2_EXECUTABLE.propertyName,
        task.buildTools.aapt2ExecutableProvider().map { it.asFile.absolutePath },
      )

      // These are the AndroidTestEngine specific parameters. Consider making it a part of official
      // AgpTestSuiteInputParameters if they are useful for other JUnit engines.
      task.engineInputProperties.put("android-test.instrumentation-runner-class", testData.instrumentationRunner)
      task.engineInputProperties.put(
        "android-test.instrumentation-args",
        testData.instrumentationRunnerArguments.map { it.entries.joinToString(",") { (k, v) -> "$k=$v" } },
      )
      task.engineInputProperties.put(
        "android-test.uninstall-after-tests",
        (!globalConfig.services.projectOptions.get(BooleanOption.ANDROID_TEST_LEAVE_APKS_INSTALLED_AFTER_RUN)).toString(),
      )

      if (testData is BundleTestDataImpl) {
        task.apkBundle.from(testData.apkBundle)
        task.bundleModuleName.setDisallowChanges(testData.moduleName)
      }

      // This Gradle property key is hard coded in Android Studio.
      // We will remove it once Android Studio can consume test report using
      // the tooling api.
      task.legacyTestReportingRedirectionEnabled.setDisallowChanges(
        task.project.providers.gradleProperty(LegacyReportingTestSuiteTestTask.ENABLE_UTP_REPORTING_PROPERTY).orNull?.toBoolean() ?: false
      )
      task.engineInputProperties.put(
        "android-test.listener.stream-base64-encoded-result",
        task.legacyTestReportingRedirectionEnabled.map { it.toString() },
      )

      task.engineInputProperties.disallowChanges()

      val variantName = creationConfig.mainVariant.name
      var buildTarget: String
      var flavorFolder = if (creationConfig.componentType.isAar) "" else creationConfig.flavorName ?: ""
      if (flavorFolder.isNotEmpty()) {
        buildTarget = variantName.substring(flavorFolder.length).lowercase(Locale.US)
        flavorFolder = "${BuilderConstants.FD_FLAVORS}/$flavorFolder"
      } else {
        buildTarget = variantName
      }
      val providerFolder = BuilderConstants.CONNECTED
      val subFolder = "$providerFolder/$buildTarget/$flavorFolder"

      val testOptions = globalConfig.androidTestOptions

      val resultsDir = testOptions.resultsDir
      if (resultsDir != null) {
        task.resultsDir.set(File(resultsDir, subFolder))
        task.resultsDir.disallowChanges()
      } else {
        task.resultsDir.setDisallowChanges(
          creationConfig.services.projectInfo.getOutputsDir().map { it.dir("${BuilderConstants.FD_ANDROID_RESULTS}/$subFolder") }
        )
      }

      val reportDir = testOptions.reportDir
      if (reportDir != null) {
        task.xmlResultsDir.set(File(reportDir, subFolder))
        task.xmlResultsDir.disallowChanges()
      } else {
        task.xmlResultsDir.setDisallowChanges(task.resultsDir)
      }

      val testTaskReports = task.reports
      // Set html to true so that Gradle's error message contains clickable link to the html file.
      testTaskReports.html.required.setDisallowChanges(true)
      // We use our own per-device XML reporter in the android-test-engine to match the default
      // test execution path behavior.
      testTaskReports.junitXml.required.setDisallowChanges(false)
      testTaskReports.junitXml.outputLocation.setDisallowChanges(task.xmlResultsDir)
      testTaskReports.html.outputLocation.setDisallowChanges(
        creationConfig.services.projectInfo.getReportsDir().map { it.dir("${BuilderConstants.FD_ANDROID_TESTS}/$subFolder") }
      )

      task.engineInputPropertiesFiles.setDisallowChanges(
        task.project.layout.buildDirectory.file("intermediates/androidTest/${creationConfig.name}/connected/junit_inputs.txt")
      )
      task.julConfigurationFile.setDisallowChanges(
        task.project.layout.buildDirectory.file("intermediates/androidTest/${creationConfig.name}/connected/logging.properties")
      )
      task.logFile.setDisallowChanges(
        task.project.layout.buildDirectory.file("intermediates/androidTest/${creationConfig.name}/connected/junit_engines_logging.txt")
      )
      task.streamingOutputFile.setDisallowChanges(
        task.project.layout.buildDirectory.file("intermediates/androidTest/${creationConfig.name}/connected/streaming.txt")
      )

      task.environment(DEFAULT_ENV_VARIABLE, task.engineInputPropertiesFiles.get().asFile.absolutePath)
    }

    override fun handleProvider(taskProvider: TaskProvider<LegacyReportingTestSuiteTestTask>) {
      super.handleProvider(taskProvider)

      creationConfig.taskContainer.connectedTestTask = taskProvider

      creationConfig.artifacts
        .setInitialProvider(taskProvider, TestSuiteTestTask::coverageDir)
        .withName("connected")
        .on(InternalArtifactType.CODE_COVERAGE)
    }
  }

  class AgpTestSuiteInputParameter(
    @get:Input val type: AgpTestSuiteInputParameters,
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) val value: Provider<out FileSystemLocation>,
  )

  /** Encapsulates a target device for test execution, pairing its serial number with its configuration characteristics. */
  class DeviceTestTarget(val serialNumber: String, val deviceConfigProvider: DeviceConfigProvider)

  /**
   * Implementation of [DeviceConfigProvider] that resolves device characteristics directly from the [ManagedVirtualDevice] DSL. This
   * ensures that for Gradle Managed Devices, we use the configured settings (e.g., API level, ABIs) as the source of truth rather than
   * querying the device at runtime.
   */
  class DslDeviceConfigProvider(val device: ManagedVirtualDevice) : DeviceConfigProvider {
    override fun getConfigFor(abi: String?): String = requireNotNull(abi)

    override fun getDensity(): Int = -1

    override fun getLanguage(): String? = null

    override fun getRegion(): String? = null

    override fun getAbis(): MutableList<String> = mutableListOf(device.testedAbi ?: computeAbiFromArchitecture(device))

    override fun getApiCodeName(): String? = device.sdkPreview

    override fun getApiLevel(): Int = device.sdkVersion
  }

  class AgpTestSuiteInputsSerializer {
    companion object {
      fun serialize(engineInputParameters: List<TestEngineInputProperty>, engineInputProperties: Map<String, String>, into: File) {
        val properties = Properties()
        engineInputParameters.plus(engineInputProperties.map { TestEngineInputProperty(it.key, it.value) }).forEach {
          testEngineInputProperty ->
          properties.put(testEngineInputProperty.name, testEngineInputProperty.value)
        }
        into.writer(Charsets.UTF_8).use { properties.store(it, "Input properties for test engine") }
      }
    }
  }

  companion object {
    const val TEST_SUITE_METADATA_FILE = "metadata.txt"
    const val TEST_SUITE_METADATA_MODULE_KEY = "modulePath"
    const val TEST_SUITE_METADATA_VARIANT_KEY = "testedVariantName"
    const val TEST_SUITE_METADATA_SUITE_KEY = "testSuiteName"
    const val TEST_SUITE_METADATA_TARGET_KEY = "testTarget"

    fun parseMetadata(metadataFile: File): Map<String, String> {
      val metadata =
        metadataFile
          .readLines()
          .mapNotNull { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
          }
          .toMap()

      return mapOf(
        TEST_SUITE_METADATA_MODULE_KEY to (metadata[TEST_SUITE_METADATA_MODULE_KEY] ?: "unknown_module"),
        TEST_SUITE_METADATA_VARIANT_KEY to (metadata[TEST_SUITE_METADATA_VARIANT_KEY] ?: "unknown_variant"),
        TEST_SUITE_METADATA_SUITE_KEY to (metadata[TEST_SUITE_METADATA_SUITE_KEY] ?: "unknown_suite"),
        TEST_SUITE_METADATA_TARGET_KEY to (metadata[TEST_SUITE_METADATA_TARGET_KEY] ?: "unknown_target"),
      )
    }
  }
}
