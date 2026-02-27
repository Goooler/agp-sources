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

import com.android.Version
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.AgpTestSuiteInputParameters
import com.android.build.api.testsuites.TestEngineInputProperty
import com.android.build.api.testsuites.TestSuiteExecutionClient.Companion.DEFAULT_ENV_VARIABLE
import com.android.build.api.variant.TestSuiteSourceSet
import com.android.build.api.variant.impl.JUnitEngineSpecImplForVariant
import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.BuildToolsExecutableInput
import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.component.TestSuiteTargetCreationConfig
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
import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.test.report.TestReport
import com.android.build.gradle.internal.testing.TestData
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.StringOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.BuilderConstants
import com.android.builder.testing.api.DeviceException
import java.io.File
import java.io.FileWriter
import java.util.Locale
import java.util.Properties
import org.gradle.api.file.Directory
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

  @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE) abstract val sourceFolders: ListProperty<Directory>

  @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE) abstract val binaryFolders: ListProperty<Directory>

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

    val engineInputParameters: List<TestEngineInputProperty> =
      engineInputParameters.get().map { inputProperty ->
        TestEngineInputProperty(inputProperty.type.propertyName, inputProperty.value.get().asFile.absolutePath)
      }

    // only get the connected devices if the test requested an APK.
    if (engineInputParameters.any { inputParameter -> inputParameter.name == AgpTestSuiteInputParameters.TESTED_APKS.propertyName }) {
      provisionDevicesAndExecute { onlineDeviceSerials -> executeTests(engineInputParameters, onlineDeviceSerials) }
    } else {
      executeTests(engineInputParameters)
    }
  }

  private fun provisionDevicesAndExecute(executeTestFunc: (onlineDeviceSerials: String) -> Unit) {
    provisionConnectedDevicesAndExecute { connectedDeviceSerials ->
      provisionManagedDevicesAndExecute { managedDeviceSerials ->
        val onlineDeviceSerials = (connectedDeviceSerials + managedDeviceSerials).joinToString(",")
        executeTestFunc(onlineDeviceSerials)
      }
    }
  }

  private fun provisionConnectedDevicesAndExecute(onDevicesReady: (onlineDeviceSerials: List<String>) -> Unit) {
    val deviceProvider = deviceProviderFactory.getDeviceProvider(buildTools.adbExecutable(), androidDeviceSerials.orNull)
    try {
      deviceProvider.use {
        val onlineDeviceSerials = deviceProvider.devices.map { it.serialNumber }
        onDevicesReady(onlineDeviceSerials)
      }
    } catch (_: DeviceException) {
      onDevicesReady(listOf())
    }
  }

  private fun provisionManagedDevicesAndExecute(onDevicesReady: (onlineDeviceSerials: List<String>) -> Unit) {
    provisionManagedDevicesAndExecute(managedDevices.get().asIterable().iterator(), mutableListOf(), onDevicesReady)
  }

  private fun provisionManagedDevicesAndExecute(
    iterator: Iterator<ManagedVirtualDevice>,
    onlineDeviceSerials: MutableList<String>,
    onDevicesReady: (onlineDeviceSerials: List<String>) -> Unit,
  ) {
    if (!iterator.hasNext()) {
      onDevicesReady(onlineDeviceSerials)
      return
    }
    val avdName = computeAvdName(iterator.next())
    avdService.get().runWithAvd(avdName) { onlineDeviceSerial ->
      onlineDeviceSerials += onlineDeviceSerial
      provisionManagedDevicesAndExecute(iterator, onlineDeviceSerials, onDevicesReady)
    }
  }

  private fun executeTests(engineInputParameters: List<TestEngineInputProperty>, onlineDeviceSerials: String? = null) {
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

    if (sourceFolders.isPresent) {
      standardInputs.add(
        TestEngineInputProperty(
          TestEngineInputProperty.SOURCE_FOLDERS,
          sourceFolders.get().joinToString(separator = File.separator) { it.asFile.absolutePath },
        )
      )
    }

    if (binaryFolders.isPresent) {
      standardInputs.add(
        TestEngineInputProperty(
          TestEngineInputProperty.BINARY_FOLDERS,
          binaryFolders.get().joinToString(separator = File.separator) { it.asFile.absolutePath },
        )
      )
    }

    if (onlineDeviceSerials != null) {
      standardInputs.add(TestEngineInputProperty(TestEngineInputProperty.SERIAL_IDS, onlineDeviceSerials))
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
        handlers = java.util.logging.FileHandler
        .level = INFO
        java.util.logging.FileHandler.level = INFO
        java.util.logging.FileHandler.pattern = ${logFile.get().asFile.absolutePath.replace("\\", "/")}
        java.util.logging.FileHandler.formatter = java.util.logging.SimpleFormatter
        java.util.logging.FileHandler.append = true
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
      val report = TestReport(ReportType.SINGLE_FLAVOR, this.xmlResultsDir.get().asFile, this.reports.html.outputLocation.get().asFile)
      report.generateReport()

      val metadataDir = this.xmlResultsDir.get().asFile.also { it.mkdirs() }
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
    GlobalTaskCreationAction<TestSuiteTestTask>() {

    override val name: String
      get() = testSuiteTarget.testTaskName

    override val type: Class<TestSuiteTestTask> = TestSuiteTestTask::class.java

    override fun configure(task: TestSuiteTestTask) {
      super.configure(task)
      task.group = JavaBasePlugin.VERIFICATION_GROUP
      task.outputs.upToDateWhen { false }

      val classesDir = task.project.layout.buildDirectory.file(task.name)
      UniqueClassGenerator().generateSimpleClass(classesDir.get().asFile)
      task.testClassesDirs = creationConfig.services.fileCollection().also { it.from(classesDir) }
      task.classpath =
        creationConfig.services.fileCollection().also {
          it.from(classesDir)
          creationConfig.sourceContainers.forEach { sourceContainer -> it.from(sourceContainer.suiteSourceClasspath.runtimeClasspath) }
        }

      // Get all project properties, and system properties (possibly overriding project
      // properties) and register them for the test engine.
      creationConfig.services.projectOptions.get(StringOption.TEST_SUITE_TEST_TASK_ADDITIONAL_INPUTS_FILE)?.let { additionalInputFilePath ->
        task.systemProperty(StringOption.TEST_SUITE_TEST_TASK_ADDITIONAL_INPUTS_FILE.propertyName, additionalInputFilePath)
        // add the file to the task's inputs.
        task.inputs.file(additionalInputFilePath)
      }

      task.androidDeviceSerials.setDisallowChanges(task.project.providers.environmentVariable("ANDROID_SERIAL"))

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
      creationConfig.sources.forEach { sourceSet: TestSuiteSourceSet ->
        when (sourceSet) {
          is TestSuiteSourceSet.Assets -> {
            task.sourceFolders.addAll(sourceSet.get().all)
            task.failOnNoDiscoveredTests.setDisallowChanges(false)
          }
          is TestSuiteSourceSet.HostJar -> {
            task.binaryFolders.add(creationConfig.artifacts.get(InternalArtifactType.BUILT_IN_KOTLINC))
            task.binaryFolders.add(creationConfig.artifacts.get(InternalArtifactType.JAVAC))
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
    }

    override fun handleProvider(taskProvider: TaskProvider<TestSuiteTestTask>) {
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
  ) : GlobalTaskCreationAction<TestSuiteTestTask>() {

    override val name: String
      get() = creationConfig.computeTaskNameInternal("connected")

    override val type: Class<TestSuiteTestTask> = TestSuiteTestTask::class.java

    override fun configure(task: TestSuiteTestTask) {
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

      val classesDir = task.project.layout.buildDirectory.file(task.name)
      UniqueClassGenerator().generateSimpleClass(classesDir.get().asFile)
      task.testClassesDirs = creationConfig.services.fileCollection().also { it.from(classesDir) }

      // TODO(b/476442048): Remove -dev suffix for release builds once the android-test-engine BUILD file is updated.
      val androidTestEngineVersion = if (Version.IS_AGP_RELEASE_BRANCH) "0.1.0-dev" else "0.1.0-dev"

      task.classpath =
        creationConfig.services.fileCollection().also {
          it.from(task.testClassesDirs)
          it.from(creationConfig.variantDependencies.runtimeClasspath)
          it.from(
            creationConfig.services.configurations.detachedConfiguration(
              creationConfig.services.dependencies.create("com.android.tools.androidtest:android-test-engine:$androidTestEngineVersion"),
              creationConfig.services.dependencies.create("org.junit.platform:junit-platform-engine:1.12.0"),
              creationConfig.services.dependencies.create("org.junit.platform:junit-platform-launcher:1.12.0"),
            )
          )
        }

      // Disable Gradle Test task's no discovered tests check until Gradle supports resource based
      // testing. (Android Test's input to TestEngine is an APK, not a compiled classes).
      task.failOnNoDiscoveredTests.setDisallowChanges(false)

      task.useJUnitPlatform { testFramework: JUnitPlatformOptions -> testFramework.includeEngines("android-test-engine") }

      task.engineInputParameters.add(
        AgpTestSuiteInputParameter(AgpTestSuiteInputParameters.TESTED_APKS, creationConfig.mainVariant.artifacts.get(SingleArtifact.APK))
      )
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
      task.engineInputProperties.put("android-test.uninstall-after-tests", "true")
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
      testTaskReports.junitXml.required.setDisallowChanges(true)
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

    override fun handleProvider(taskProvider: TaskProvider<TestSuiteTestTask>) {
      super.handleProvider(taskProvider)

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

  class AgpTestSuiteInputsSerializer {
    companion object {
      fun serialize(engineInputParameters: List<TestEngineInputProperty>, engineInputProperties: Map<String, String>, into: File) {
        val properties = Properties()
        engineInputParameters.plus(engineInputProperties.map { TestEngineInputProperty(it.key, it.value) }).forEach {
          testEngineInputProperty ->
          properties.put(testEngineInputProperty.name, testEngineInputProperty.value)
        }
        FileWriter(into).use { properties.store(it, "Input properties for test engine") }
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
