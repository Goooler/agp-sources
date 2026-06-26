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
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.component.TestSuiteTargetCreationConfig
import com.android.build.gradle.internal.computeAbiFromArchitecture
import com.android.build.gradle.internal.computeAvdName
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask.DeviceProviderFactory
import com.android.build.gradle.internal.tasks.GlobalTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.getApkFiles
import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.test.report.TestReport
import com.android.build.gradle.internal.test.report.XMLReportAggregator
import com.android.build.gradle.internal.test.report.processTestReportAggregation
import com.android.build.gradle.internal.testing.TestData
import com.android.build.gradle.internal.testing.configureAndroidTestEngine
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.StringOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.BuilderConstants
import com.android.builder.core.ComponentType
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
import org.gradle.api.file.FileCollection
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

  @get:Input @get:Optional abstract val animationsDisabled: Property<Boolean>

  @get:OutputFile abstract val engineInputPropertiesFiles: RegularFileProperty

  @get:OutputFile abstract val julConfigurationFile: RegularFileProperty

  @get:OutputFile abstract val logFile: RegularFileProperty

  @get:OutputFile abstract val streamingOutputFile: RegularFileProperty

  @get:OutputDirectory abstract val resultsDir: DirectoryProperty

  @get:OutputDirectory @get:Optional abstract val xmlResultsDirectory: DirectoryProperty

  @get:OutputDirectory @get:Optional abstract val additionalTestOutputDir: DirectoryProperty

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

  /**
   * The number of shards to split the test execution into.
   *
   * This input is only relevant for Gradle Managed Devices (GMD) and is ignored for other test targets (e.g., connected devices).
   */
  @get:Input @get:Optional abstract val shardCount: Property<Int>

  @get:Internal abstract val avdService: Property<AvdComponentsBuildService>

  @get:Input @get:Optional abstract val reportAggregationSupport: Property<Boolean>

  @Input
  override fun getIgnoreFailures(): Boolean {
    return super.getIgnoreFailures()
  }

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
    if (additionalTestOutputDir.isPresent) {
      PathUtils.deleteRecursivelyIfExists(additionalTestOutputDir.get().asFile.toPath())
    }
    logFile.get().asFile.delete()

    val streamingFile = streamingOutputFile.get().asFile
    streamingFile.parentFile.mkdirs()
    if (streamingFile.exists()) {
      streamingFile.delete()
    }
    streamingFile.createNewFile()

    val engineInputParameters: List<TestEngineInputProperty> =
      engineInputParameters.get().map { inputProperty ->
        val resolvedPath = inputProperty.fileCollection.files.joinToString(java.io.File.pathSeparator) { it.absolutePath }
        TestEngineInputProperty(inputProperty.type.propertyName, resolvedPath)
      }

    doExecuteTests(engineInputParameters)
  }

  protected open fun doExecuteTests(engineInputParameters: List<TestEngineInputProperty>) {
    // only get the connected devices if the test requested an APK.
    if (
      engineInputParameters.any { inputParameter ->
        inputParameter.name == AgpTestSuiteInputParameters.TESTED_APKS.propertyName ||
          inputParameter.name == AgpTestSuiteInputParameters.TEST_APKS.propertyName
      }
    ) {
      provisionDevicesAndExecute { onlineDevices -> executeTests(engineInputParameters, onlineDevices) }
    } else {
      executeTests(engineInputParameters)
    }
  }

  private fun provisionDevicesAndExecute(onDevicesReady: (onlineDevices: List<DeviceTestTarget>) -> Unit) {
    provisionConnectedDevicesAndExecute { connectedDevices, deviceException ->
      provisionManagedDevicesAndExecute { managedDevices ->
        val onlineDevices = connectedDevices + managedDevices
        if (onlineDevices.isEmpty()) {
          throw deviceException ?: DeviceException("No connected devices!")
        }
        onDevicesReady(onlineDevices)
      }
    }
  }

  private fun provisionConnectedDevicesAndExecute(
    onDevicesReady: (onlineDevices: List<DeviceTestTarget>, exception: DeviceException?) -> Unit
  ) {
    val deviceProvider = deviceProviderFactory.getDeviceProvider(buildTools.adbExecutable(), androidDeviceSerials.orNull)
    try {
      deviceProvider.use {
        val targets =
          deviceProvider.devices.map { connector -> DeviceTestTarget(connector.serialNumber, DeviceConfigProviderImpl(connector)) }
        onDevicesReady(targets, null)
      }
    } catch (e: DeviceException) {
      onDevicesReady(listOf(), e)
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
    val desiredDeviceCount = if (shardCount.isPresent) shardCount.get() else 1
    avdService.get().runWithAvds(avdName, desiredDeviceCount) { onlineDeviceSerials ->
      if (desiredDeviceCount > 1) {
        onlineDeviceSerials.forEachIndexed { index, serial ->
          onlineDevices.add(DeviceTestTarget(serial, DslDeviceConfigProvider(device), "${device.name}_$index"))
        }
      } else {
        onlineDevices.add(DeviceTestTarget(onlineDeviceSerials.first(), DslDeviceConfigProvider(device), device.name))
      }
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
      )

    if (additionalTestOutputDir.isPresent) {
      standardInputs.add(
        TestEngineInputProperty("android-test.additional-test-output-dir-on-host", providerToPath(additionalTestOutputDir))
      )
    }

    standardInputs.add(
      TestEngineInputProperty(AgpTestSuiteInputParameters.ADB_EXECUTABLE.propertyName, buildTools.adbExecutable().get().asFile.absolutePath)
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

    if (animationsDisabled.isPresent) {
      // TODO(b/525084361): Replace hardcoded string with AgpTestSuiteInputParameters.ANIMATIONS_DISABLED.propertyName once exposed in DSL
      // API.
      standardInputs.add(TestEngineInputProperty("com.android.agp.test.ANIMATIONS_DISABLED", animationsDisabled.get().toString()))
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

      val useDeviceSpecificPaths = managedDevices.get().size == 1

      onlineDevices.forEach { target ->
        val serial = target.serialNumber
        if (useDeviceSpecificPaths) {
          standardInputs.add(TestEngineInputProperty("${TestEngineInputProperty.RESULTS_DIR}[$serial]", providerToPath(resultsDir)))
          standardInputs.add(TestEngineInputProperty("${TestEngineInputProperty.COVERAGE_DIR}[$serial]", providerToPath(coverageDir)))
          if (additionalTestOutputDir.isPresent) {
            standardInputs.add(
              TestEngineInputProperty("android-test.additional-test-output-dir-on-host[$serial]", providerToPath(additionalTestOutputDir))
            )
          }
          target.deviceName?.let { deviceName ->
            standardInputs.add(TestEngineInputProperty("android-test.device-id[$serial]", deviceName))
          }
        }

        if (!apkBundle.isEmpty) {
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
      val testResultsDir = resultsDir.get().asFile
      val htmlOutputDirFile = this.reports.html.outputLocation.get().asFile

      val metadataContent =
        """
              $TEST_SUITE_METADATA_MODULE_KEY=${this.modulePath.get()}
              $TEST_SUITE_METADATA_VARIANT_KEY=${this.testedVariantName.get()}
              $TEST_SUITE_METADATA_SUITE_KEY=${this.testSuiteName.get()}
              $TEST_SUITE_METADATA_TARGET_KEY=${this.testSuiteTarget.get()}
          """
          .trimIndent()

      processTestReportAggregation(
        testResultsDir,
        xmlResultsDirectory,
        this.modulePath.get(),
        this.testedVariantName.get(),
        this.testSuiteName.get(),
        this.testSuiteTarget.get(),
        logger,
      )

      if (reportAggregationSupport.isPresent && reportAggregationSupport.get()) {
        val aggregator = XMLReportAggregator(listOf(testResultsDir), this.modulePath.get())
        aggregator.writeReport(htmlOutputDirFile)
      } else {
        val report = TestReport(ReportType.SINGLE_FLAVOR, testResultsDir, htmlOutputDirFile)
        report.generateReport()
      }

      // Also write metadata to coverage directory so that the coverage collection task can identify the suite
      val coverageMetadataDir = this.coverageDir.get().asFile.also { it.mkdirs() }
      val coverageMetadataFile = File(coverageMetadataDir, TEST_SUITE_METADATA_FILE)
      coverageMetadataFile.writeText(metadataContent)
    }
  }

  private fun providerToPath(value: Provider<out FileSystemLocation>): String = value.get().asFile.absolutePath

  class CreationAction(
    val creationConfig: TestSuiteCreationConfig,
    val testSuiteTarget: TestSuiteTargetCreationConfig,
    private val deviceSerials: Provider<List<String>>? = null,
  ) : GlobalTaskCreationAction<LegacyReportingTestSuiteTestTask>() {

    override val name: String
      get() = testSuiteTarget.testTaskName

    override val type: Class<LegacyReportingTestSuiteTestTask> = LegacyReportingTestSuiteTestTask::class.java

    override fun configure(task: LegacyReportingTestSuiteTestTask) {
      super.configure(task)
      task.group = JavaBasePlugin.VERIFICATION_GROUP
      task.outputs.upToDateWhen { false }

      val classesDir = task.project.layout.buildDirectory.file(task.name)
      val hasHostJar = creationConfig.sourceContainers.any { it.source is TestSuiteSourceSet.HostJar }

      task.testClassesDirs =
        creationConfig.services.fileCollection().also { fileCollection ->
          fileCollection.from(classesDir)
          creationConfig.sourceContainers.forEach { sourceContainer ->
            if (sourceContainer.source is TestSuiteSourceSet.HostJar) {
              fileCollection.from(
                sourceContainer.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).getFinalArtifacts(ScopedArtifact.POST_COMPILATION_CLASSES)
              )
            }
          }
        }
      task.classpath =
        creationConfig.services.fileCollection().also { fileCollection ->
          if (hasHostJar) {
            fileCollection.from(classesDir)
            fileCollection.from(
              creationConfig.testedVariant.artifacts
                .forScope(ScopedArtifacts.Scope.PROJECT)
                .getFinalArtifacts(ScopedArtifact.POST_COMPILATION_CLASSES)
            )
            creationConfig.sourceContainers
              .filter { it.source is TestSuiteSourceSet.HostJar }
              .forEach { sourceContainer ->
                fileCollection.from(
                  sourceContainer.suiteSourceClasspath.getHostRuntimeClasspathArtifacts(AndroidArtifacts.ArtifactType.CLASSES_JAR)
                )
                fileCollection.from(
                  sourceContainer.artifacts
                    .forScope(ScopedArtifacts.Scope.PROJECT)
                    .getFinalArtifacts(ScopedArtifact.POST_COMPILATION_CLASSES)
                )
              }
          } else {
            creationConfig.sourceContainers.forEach { sourceContainer ->
              fileCollection.from(
                sourceContainer.suiteSourceClasspath.getHostRuntimeClasspathArtifacts(AndroidArtifacts.ArtifactType.CLASSES_JAR)
              )
            }
          }
        }

      // Get all project properties, and system properties (possibly overriding project
      // properties) and register them for the test engine.
      creationConfig.services.projectOptions.get(StringOption.TEST_SUITE_TEST_TASK_ADDITIONAL_INPUTS_FILE)?.let { additionalInputFilePath ->
        task.systemProperty(StringOption.TEST_SUITE_TEST_TASK_ADDITIONAL_INPUTS_FILE.propertyName, additionalInputFilePath)
        // add the file to the task's inputs.
        task.inputs.file(additionalInputFilePath)
      }

      // Configure the device serials for the test execution.
      // 1. If a list of serials provider is passed (e.g. from the connectedCheck task), we use it.
      //    However, if that list resolves to empty (meaning no task-specific serials were specified),
      //    we fall back to the ANDROID_SERIAL environment variable provider.
      // 2. If the serials provider itself is null, we directly use the ANDROID_SERIAL env var.
      if (deviceSerials != null) {
        val finalSerials =
          deviceSerials.flatMap { list ->
            if (list.isEmpty()) {
              task.project.providers.environmentVariable("ANDROID_SERIAL")
            } else {
              task.project.providers.provider { list.joinToString(",") }
            }
          }
        task.androidDeviceSerials.setDisallowChanges(finalSerials)
      } else {
        task.androidDeviceSerials.setDisallowChanges(task.project.providers.environmentVariable("ANDROID_SERIAL"))
      }

      if (creationConfig.junitEngineSpec.inputs.contains(AgpTestSuiteInputParameters.ANDROID_TEST_EXECUTION_MODE)) {
        task.executionMode.setDisallowChanges(creationConfig.global.androidTestOptions.execution)
      }

      // TODO(b/525084361): Wrap with creationConfig.junitEngineSpec.inputs.contains(AgpTestSuiteInputParameters.ANIMATIONS_DISABLED) once
      // exposed in DSL API.
      task.animationsDisabled.setDisallowChanges(
        creationConfig.services.provider { creationConfig.global.androidTestOptions.animationsDisabled }
      )

      if (creationConfig.junitEngineSpec.inputs.contains(AgpTestSuiteInputParameters.TEST_UTIL_APKS)) {
        val androidTestUtil = task.project.configurations.findByName(SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION)
        if (androidTestUtil != null) {
          task.testUtilApks.from(androidTestUtil)
        }
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
                task.project.files(testedVariant.artifacts.get(SingleArtifact.MERGED_MANIFEST)),
              )
            )
          }

          AgpTestSuiteInputParameters.TESTED_APKS -> {
            task.engineInputParameters.add(
              AgpTestSuiteInputParameter(
                AgpTestSuiteInputParameters.TESTED_APKS,
                task.project.files(testedVariant.artifacts.get(SingleArtifact.APK)),
              )
            )
          }

          AgpTestSuiteInputParameters.RESOURCES_AP_ARCHIVE -> {
            task.engineInputParameters.add(
              AgpTestSuiteInputParameter(
                AgpTestSuiteInputParameters.RESOURCES_AP_ARCHIVE,
                task.project.files(creationConfig.artifacts.get(InternalArtifactType.APK_FOR_LOCAL_TEST)),
              )
            )
          }

          AgpTestSuiteInputParameters.TEST_CLASSES -> {
            val testClasses =
              task.project.objects.fileCollection().also { fc ->
                creationConfig.sourceContainers.forEach { sc ->
                  fc.from(sc.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).getFinalArtifacts(ScopedArtifact.CLASSES))
                }
              }
            task.engineInputParameters.add(AgpTestSuiteInputParameter(AgpTestSuiteInputParameters.TEST_CLASSES, testClasses))
          }

          AgpTestSuiteInputParameters.TEST_CLASSPATH -> {
            val testClasspath =
              task.project.objects.fileCollection().also { fc ->
                creationConfig.sourceContainers.forEach { sc -> fc.from(sc.suiteSourceClasspath.runtimeClasspath) }
              }
            task.engineInputParameters.add(AgpTestSuiteInputParameter(AgpTestSuiteInputParameters.TEST_CLASSPATH, testClasspath))
          }

          AgpTestSuiteInputParameters.ANDROID_RES_DIRS -> {
            val testResourceDirs =
              task.project.objects.fileCollection().also { fc ->
                creationConfig.sourceContainers.forEach { sc ->
                  fc.from(
                    sc.suiteSourceClasspath.getRuntimeClasspathArtifacts(
                      com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ANDROID_RES
                    )
                  )
                  fc.from(
                    sc.suiteSourceClasspath.getCompileClasspathArtifacts(
                      com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ANDROID_RES
                    )
                  )
                }
              }
            task.engineInputParameters.add(AgpTestSuiteInputParameter(AgpTestSuiteInputParameters.ANDROID_RES_DIRS, testResourceDirs))
          }

          AgpTestSuiteInputParameters.R_CLASS_JARS -> {
            val testRClassJars =
              task.project.objects.fileCollection().also { fc ->
                creationConfig.sourceContainers.forEach { sc ->
                  fc.from(
                    sc.suiteSourceClasspath.getRuntimeClasspathArtifacts(
                      com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.R_CLASS_JAR
                    )
                  )
                  fc.from(
                    sc.suiteSourceClasspath.getCompileClasspathArtifacts(
                      com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.R_CLASS_JAR
                    )
                  )
                }
              }
            task.engineInputParameters.add(AgpTestSuiteInputParameter(AgpTestSuiteInputParameters.R_CLASS_JARS, testRClassJars))
          }

          AgpTestSuiteInputParameters.MAIN_CLASSES -> {
            val mainClasses =
              task.project.objects
                .fileCollection()
                .from(testedVariant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).getFinalArtifacts(ScopedArtifact.CLASSES))
            task.engineInputParameters.add(AgpTestSuiteInputParameter(AgpTestSuiteInputParameters.MAIN_CLASSES, mainClasses))
          }

          AgpTestSuiteInputParameters.MAIN_CLASSPATH -> {
            val mainClasspath =
              task.project.objects
                .fileCollection()
                .from(testedVariant.artifacts.forScope(ScopedArtifacts.Scope.ALL).getFinalArtifacts(ScopedArtifact.CLASSES))
            task.engineInputParameters.add(AgpTestSuiteInputParameter(AgpTestSuiteInputParameters.MAIN_CLASSPATH, mainClasspath))
          }

          AgpTestSuiteInputParameters.ADB_EXECUTABLE -> {
            // do nothing so far, we always do it but it might change in the near future.
          }

          AgpTestSuiteInputParameters.TEST_APKS -> {
            val testApkSourceContainer = creationConfig.sourceContainers.firstOrNull { it.source is TestSuiteSourceSet.TestApk }
            // Fall back to the main creationConfig artifacts if no explicit TestApk source container
            // is defined. This supports default androidTest paths and standalone test projects
            // where the test APK is produced directly by the variant.
            val apkProvider =
              if (testApkSourceContainer != null) {
                testApkSourceContainer.artifacts.get(SingleArtifact.APK)
              } else {
                creationConfig.artifacts.get(SingleArtifact.APK)
              }
            task.engineInputParameters.add(
              AgpTestSuiteInputParameter(AgpTestSuiteInputParameters.TEST_APKS, task.project.files(apkProvider))
            )
          }

          AgpTestSuiteInputParameters.AAPT2_EXECUTABLE -> {
            task.engineInputParameters.add(
              AgpTestSuiteInputParameter(
                AgpTestSuiteInputParameters.AAPT2_EXECUTABLE,
                task.project.files(task.buildTools.aapt2ExecutableProvider()),
              )
            )
          }

          AgpTestSuiteInputParameters.TEST_UTIL_APKS,
          AgpTestSuiteInputParameters.ANDROID_TEST_EXECUTION_MODE -> {
            // Handled via task properties and standardInputs in executeTests
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
      task.engineInputProperties.put(
        "com.android.agp.test.COVERAGE_TYPE",
        if (creationConfig.services.projectOptions.get(BooleanOption.ENABLE_ON_THE_FLY_CODE_COVERAGE)) "ON_THE_FLY" else "NONE",
      )
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
          }
          is TestSuiteSourceSet.HostJar -> {
            task.binaryFolders.from(
              sourceContainer.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).getFinalArtifacts(ScopedArtifact.POST_COMPILATION_CLASSES)
            )
          }
          is TestSuiteSourceSet.TestApk -> {
            task.testDefinitionDirs.from(sourceContainer.artifacts.get(InternalArtifactType.ANDROID_TEST_DISCOVERY_LIST))
          }
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
      task.resultsDir.set(task.project.layout.buildDirectory.dir("intermediates/${testedVariant.name}/$name/results"))
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
      xmlReport.outputLocation.setDisallowChanges(task.resultsDir)

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

      task.reportAggregationSupport.setDisallowChanges(creationConfig.services.projectOptions.get(BooleanOption.REPORT_AGGREGATION_SUPPORT))
    }

    override fun handleProvider(taskProvider: TaskProvider<LegacyReportingTestSuiteTestTask>) {
      super.handleProvider(taskProvider)

      creationConfig.testedVariant.artifacts
        .use(taskProvider)
        .wiredWith(TestSuiteTestTask::xmlResultsDirectory)
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
    private val creationConfig: InstrumentedTestCreationConfig,
    private val testData: TestData,
    private val connectedCheckSerials: Provider<List<String>>,
  ) : GlobalTaskCreationAction<LegacyReportingTestSuiteTestTask>() {

    override val name: String
      get() =
        if (creationConfig.componentType.isSeparateTestProject) {
          creationConfig.computeTaskNameInternal("connected", ComponentType.ANDROID_TEST_SUFFIX)
        } else {
          creationConfig.computeTaskNameInternal("connected")
        }

    override val type: Class<LegacyReportingTestSuiteTestTask> = LegacyReportingTestSuiteTestTask::class.java

    override fun configure(task: LegacyReportingTestSuiteTestTask) {
      super.configure(task)

      val globalConfig = creationConfig.global
      val testedConfig = (creationConfig as? DeviceTestCreationConfig)?.mainVariant
      val variantName = testedConfig?.name ?: creationConfig.name

      task.group = JavaBasePlugin.VERIFICATION_GROUP
      task.description = "Installs and runs the tests for $variantName on connected devices."
      task.outputs.upToDateWhen { false }

      task.testSuiteName.setDisallowChanges(CONNECTED_TEST_TEST_SUITE_NAME)
      task.testSuiteTarget.setDisallowChanges(CONNECTED_TEST_TEST_SUITE_TARGET_NAME)
      task.testedVariantName.setDisallowChanges(variantName)
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
      // We use the output of the AndroidTestDiscoveryTask as a trigger to ensure
      // task execution only when tests are discovered host-side, while letting
      // AndroidTestEngine handle the actual test orchestration on-device.
      task.testDefinitionDirs.from(
        creationConfig.artifacts.get(com.android.build.gradle.internal.scope.InternalArtifactType.ANDROID_TEST_DISCOVERY_LIST)
      )

      configureAndroidTestEngine(task, creationConfig, testData, "connected")

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

      val additionalTestOutputDir =
        creationConfig.services.projectInfo.getOutputsDir().map {
          it.dir("connected_android_test_additional_output/${creationConfig.name}/$providerFolder")
        }
      task.additionalTestOutputDir.set(additionalTestOutputDir)
      task.additionalTestOutputDir.disallowChanges()

      val testOptions = globalConfig.androidTestOptions
      val testTaskReports = task.reports
      val reportDir = testOptions.reportDir
      if (reportDir != null) {
        testTaskReports.html.outputLocation.set(File(reportDir, subFolder))
        testTaskReports.html.outputLocation.disallowChanges()
      } else {
        testTaskReports.html.outputLocation.setDisallowChanges(
          creationConfig.services.projectInfo.getReportsDir().map { it.dir("${BuilderConstants.FD_ANDROID_TESTS}/$subFolder") }
        )
      }

      task.reportAggregationSupport.setDisallowChanges(creationConfig.services.projectOptions.get(BooleanOption.REPORT_AGGREGATION_SUPPORT))
    }

    override fun handleProvider(taskProvider: TaskProvider<LegacyReportingTestSuiteTestTask>) {
      super.handleProvider(taskProvider)

      creationConfig.taskContainer.connectedTestTask = taskProvider

      creationConfig.artifacts
        .setInitialProvider(taskProvider, TestSuiteTestTask::coverageDir)
        .withName("connected")
        .on(InternalArtifactType.CODE_COVERAGE)

      if (creationConfig is DeviceTestCreationConfig) {
        creationConfig.mainVariant.artifacts
          .use(taskProvider)
          .wiredWith(TestSuiteTestTask::coverageDir)
          .toAppendTo(InternalMultipleArtifactType.TEST_SUITE_CODE_COVERAGE)

        creationConfig.mainVariant.artifacts
          .use(taskProvider)
          .wiredWith(TestSuiteTestTask::xmlResultsDirectory)
          .toAppendTo(InternalMultipleArtifactType.TEST_SUITE_RESULTS)
      }

      val artifacts =
        if (creationConfig is DeviceTestCreationConfig) {
          creationConfig.mainVariant.artifacts
        } else {
          creationConfig.artifacts
        }

      val globalConfig = creationConfig.global
      val testedConfig = (creationConfig as? DeviceTestCreationConfig)?.mainVariant
      val variantName = testedConfig?.name ?: creationConfig.name
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
      val request = artifacts.setInitialProvider(taskProvider, TestSuiteTestTask::resultsDir)

      if (resultsDir != null) {
        val f = File(resultsDir)
        val resolvedFile =
          if (f.isAbsolute) {
            File(f, subFolder)
          } else {
            File(creationConfig.services.projectInfo.projectDirectory.asFile, File(resultsDir, subFolder).path)
          }
        request.atLocation(resolvedFile)
      } else {
        val defaultLocation =
          creationConfig.services.projectInfo.getOutputsDir().map { it.dir("${BuilderConstants.FD_ANDROID_RESULTS}/$subFolder") }
        request.atLocation(defaultLocation)
      }
      request.on(InternalArtifactType.ANDROID_TEST_RESULTS)

      if (creationConfig is DeviceTestCreationConfig) {
        creationConfig.mainVariant.artifacts
          .use(taskProvider)
          .wiredWith(TestSuiteTestTask::xmlResultsDirectory)
          .toAppendTo(InternalMultipleArtifactType.TEST_SUITE_RESULTS)
      }
    }
  }

  /**
   * Specialized [GlobalTaskCreationAction] to register the managed device task using the newer [TestSuiteTestTask] implementation.
   *
   * This allows the transition from the legacy [ManagedDeviceInstrumentationTestTask] to the JUnit Platform-based execution.
   */
  class ManagedDeviceTestSuiteCreationAction(
    private val creationConfig: InstrumentedTestCreationConfig,
    private val device: ManagedVirtualDevice,
    private val testData: TestData,
    private val testResultOutputDir: File,
    private val testReportOutputDir: File,
    private val additionalTestOutputDir: File,
    private val coverageOutputDir: File,
    private val nameSuffix: String = "",
  ) : GlobalTaskCreationAction<LegacyReportingTestSuiteTestTask>() {

    override val name: String
      get() = creationConfig.computeTaskNameInternal(device.name, nameSuffix)

    override val type: Class<LegacyReportingTestSuiteTestTask> = LegacyReportingTestSuiteTestTask::class.java

    override fun handleProvider(taskProvider: TaskProvider<LegacyReportingTestSuiteTestTask>) {
      super.handleProvider(taskProvider)

      creationConfig.artifacts
        .setInitialProvider(taskProvider, LegacyReportingTestSuiteTestTask::coverageDir)
        .atLocation(coverageOutputDir.absolutePath)
        .on(InternalArtifactType.MANAGED_DEVICE_CODE_COVERAGE)

      val isAdditionalAndroidTestOutputEnabled = creationConfig.services.projectOptions[BooleanOption.ENABLE_ADDITIONAL_ANDROID_TEST_OUTPUT]
      if (isAdditionalAndroidTestOutputEnabled) {
        creationConfig.artifacts
          .setInitialProvider(taskProvider, LegacyReportingTestSuiteTestTask::additionalTestOutputDir)
          .atLocation(additionalTestOutputDir.absolutePath)
          .on(InternalArtifactType.MANAGED_DEVICE_ANDROID_TEST_ADDITIONAL_OUTPUT)
      }
    }

    override fun configure(task: LegacyReportingTestSuiteTestTask) {
      super.configure(task)

      val globalConfig = creationConfig.global
      val testedConfig = (creationConfig as? DeviceTestCreationConfig)?.mainVariant
      val variantName = testedConfig?.name ?: creationConfig.name

      task.group = JavaBasePlugin.VERIFICATION_GROUP
      task.description = "Installs and runs the tests for $variantName on managed device ${device.name}."
      task.outputs.upToDateWhen { false }

      task.testSuiteName.setDisallowChanges("androidTest")
      task.testSuiteTarget.setDisallowChanges(device.name)
      task.testedVariantName.setDisallowChanges(variantName)
      task.modulePath.setDisallowChanges(creationConfig.services.projectInfo.path)

      task.deviceProviderFactory.timeOutInMs.setDisallowChanges(globalConfig.installationOptions.timeOutInMs)

      task.buildTools.initialize(task, creationConfig)

      task.managedDevices.add(device)
      task.managedDevices.disallowChanges()

      val shardPoolSize = creationConfig.services.projectOptions.get(IntegerOption.MANAGED_DEVICE_SHARD_POOL_SIZE)
      task.shardCount.setDisallowChanges(shardPoolSize ?: 1)

      task.executionMode.setDisallowChanges(globalConfig.androidTestOptions.execution)

      val androidTestUtil = task.project.configurations.findByName(SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION)
      if (androidTestUtil != null) {
        task.testUtilApks.from(androidTestUtil)
      }

      task.testDefinitionDirs.from(
        creationConfig.artifacts.get(com.android.build.gradle.internal.scope.InternalArtifactType.ANDROID_TEST_DISCOVERY_LIST)
      )

      val subFolder = creationConfig.computeTaskNameInternal(device.name, nameSuffix)

      configureAndroidTestEngine(task, creationConfig, testData, subFolder)

      task.resultsDir.set(testResultOutputDir)

      val testTaskReports = task.reports
      testTaskReports.html.outputLocation.set(testReportOutputDir)
      testTaskReports.html.outputLocation.disallowChanges()

      task.avdService.setDisallowChanges(getBuildService(creationConfig.services.buildServiceRegistry))
    }
  }

  class AgpTestSuiteInputParameter(
    @get:Input val type: AgpTestSuiteInputParameters,
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) val fileCollection: FileCollection,
  )

  class DeviceTestTarget(val serialNumber: String, val deviceConfigProvider: DeviceConfigProvider, val deviceName: String? = null)

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

    const val CONNECTED_TEST_TEST_SUITE_NAME = "androidTest"

    const val UNIT_TEST_TEST_SUITE_NAME = "UnitTest"

    const val CONNECTED_TEST_TEST_SUITE_TARGET_NAME = "connected"

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
