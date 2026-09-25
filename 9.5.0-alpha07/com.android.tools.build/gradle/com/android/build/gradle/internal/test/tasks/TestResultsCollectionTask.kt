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

package com.android.build.gradle.internal.test.tasks

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.impl.capitalizeFirstChar
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.coverage.JacocoConfigurations
import com.android.build.gradle.internal.coverage.generateReport
import com.android.build.gradle.internal.coverage.getUnitTestJacocoVersion
import com.android.build.gradle.internal.coverage.report.ReportType
import com.android.build.gradle.internal.coverage.tasks.TestReportCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.JacocoTask
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.TestSuiteTestTask
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.CONNECTED_TEST_TEST_SUITE_NAME
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_SUITE_METADATA_FILE
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_SUITE_METADATA_MODULE_KEY
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_SUITE_METADATA_SUITE_KEY
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_SUITE_METADATA_VARIANT_KEY
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.UNIT_TEST_TEST_SUITE_NAME
import com.android.buildanalyzer.common.TaskCategory
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.ClassLoaderWorkerSpec
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.w3c.dom.Node

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class TestResultsCollectionTask : NonIncrementalTask() {

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) @get:Optional abstract val testSuiteResults: ListProperty<Directory>

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) @get:Optional abstract val unitTestResults: DirectoryProperty

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) @get:Optional abstract val androidTestResults: DirectoryProperty

  @get:InputFiles
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val dependentModuleTestResults: ConfigurableFileCollection

  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  @get:OutputDirectory @get:Optional abstract val coverageOutputDir: DirectoryProperty

  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) @get:Optional abstract val unitTestCoverageFile: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  @get:Optional
  abstract val connectedTestCoverageDirectory: ConfigurableFileCollection

  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) @get:Optional abstract val testSuiteCoverageData: ConfigurableFileCollection

  @get:Classpath @get:Optional abstract val classFileCollection: ConfigurableFileCollection

  /**
   * The source directories to be used for matching with coverage files when generating reports.
   *
   * We use [ListProperty] of [Provider] of [List] of [ConfigurableFileTree] because:
   * 1. [ListProperty] allows lazy collection of multiple independent source sets (e.g., separate registrations for Java and Kotlin source
   *    directories) without evaluating them during configuration.
   * 2. [Provider] and [List] handle the lazy resolution of directories that might not exist or are not fully configured yet.
   * 3. [ConfigurableFileTree] preserves the root directory (via [ConfigurableFileTree.getDir]) of each source tree. This is critical for
   *    Jacoco report generation to correctly resolve package structures and locate source files, which would be lost if using a flat
   *    [FileCollection].
   */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:Optional
  abstract val sources: ListProperty<Provider<List<ConfigurableFileTree>>>

  @get:InputFiles
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val dependantModulesReports: ConfigurableFileCollection

  @get:Classpath @get:Optional abstract val jacocoClasspath: ConfigurableFileCollection

  @get:Internal abstract val projectRoot: DirectoryProperty

  override fun doTaskAction() {
    val outputDir = this.outputDir.get().asFile

    val copyXmls = { directory: File ->
      if (directory.exists()) {
        directory
          .listFiles { file -> file.extension == "xml" }
          ?.forEach { xmlFile ->
            val targetFile = outputDir.resolve(xmlFile.name)
            xmlFile.copyTo(targetFile, overwrite = true)
          }
      }
    }

    if (testSuiteResults.isPresent) {
      testSuiteResults.get().forEach { directory -> copyXmls(directory.asFile) }
    }

    if (unitTestResults.isPresent) {
      copyXmls(unitTestResults.get().asFile)
    }

    if (androidTestResults.isPresent) {
      copyXmls(androidTestResults.get().asFile)
    }

    dependentModuleTestResults.asFileTree.forEach { xmlFile ->
      val targetFile = outputDir.resolve(xmlFile.name)
      xmlFile.copyTo(targetFile, overwrite = true)
    }

    // --- CODE COVERAGE GENERATION ---
    if (coverageOutputDir.isPresent) {
      try {
        if (jacocoClasspath.isEmpty) {
          logger.warn("Cannot generate report. Please ensure a single Jacoco version is configured.")
        } else {
          val sourceFolders: List<File> =
            if (sources.isPresent) {
              sources.get().flatMap { it.get().map(ConfigurableFileTree::getDir) }.distinctBy { it.absolutePath }
            } else {
              emptyList()
            }

          workerExecutor
            .classLoaderIsolation { classpath: ClassLoaderWorkerSpec -> classpath.classpath.from(jacocoClasspath.files) }
            .submit(CodeCoverageCollectionWorkerAction::class.java) {
              it.reportOutputDir.set(coverageOutputDir)
              it.unitTestCoverageFile.setFrom(unitTestCoverageFile)
              it.connectedTestCoverageDirectory.setFrom(connectedTestCoverageDirectory)
              it.testSuiteCoverageData.setFrom(testSuiteCoverageData)
              it.classFolders.setFrom(classFileCollection)
              it.sourceFolders.setFrom(sourceFolders)
              it.dependantModulesReports.setFrom(dependantModulesReports)
              it.variantName.set(variantName)
              it.projectName.set(projectPath.get())
              it.projectRoot.set(projectRoot)
            }
        }
      } catch (e: Exception) {
        logger.warn("Unable to generate code coverage reports", e)
      }
    }
  }

  class AggregatedTestResultsCollectionCreationAction(creationConfig: TestReportCreationConfig) :
    BaseTestResultsCollectionCreationAction(creationConfig) {

    override val name = computeTaskName("aggregatedTestResultsCollection")

    override fun configure(task: TestResultsCollectionTask) {
      super.configure(task)

      task.dependentModuleTestResults.from(
        creationConfig.variantCreationConfig.variantDependencies.getArtifactFileCollection(
          AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
          AndroidArtifacts.ArtifactScope.PROJECT,
          AndroidArtifacts.ArtifactType.TEST_RESULTS,
        )
      )

      task.dependantModulesReports.from(creationConfig.dependantModulesReports)
    }

    override fun handleProvider(taskProvider: TaskProvider<TestResultsCollectionTask>) {
      super.handleProvider(taskProvider)
      creationConfig.global.globalArtifacts
        .use(taskProvider)
        .wiredWith(TestResultsCollectionTask::outputDir)
        .toAppendTo(InternalMultipleArtifactType.ALL_PROJECT_TEST_RESULTS)

      creationConfig.global.globalArtifacts
        .use(taskProvider)
        .wiredWith(TestResultsCollectionTask::coverageOutputDir)
        .toAppendTo(InternalMultipleArtifactType.AGGREGATED_CODE_COVERAGE_DATA)
    }
  }

  class TestResultsCollectionCreationAction(creationConfig: TestReportCreationConfig) :
    BaseTestResultsCollectionCreationAction(creationConfig) {

    override val name = computeTaskName("testResultsCollection")

    override fun handleProvider(taskProvider: TaskProvider<TestResultsCollectionTask>) {
      super.handleProvider(taskProvider)

      creationConfig.artifacts
        .setInitialProvider(taskProvider, TestResultsCollectionTask::outputDir)
        .on(InternalArtifactType.VARIANT_TEST_RESULTS)

      creationConfig.global.globalArtifacts
        .use(taskProvider)
        .wiredWith(TestResultsCollectionTask::outputDir)
        .toAppendTo(InternalMultipleArtifactType.PROJECT_LEVEL_TEST_RESULTS)

      creationConfig.artifacts
        .setInitialProvider(taskProvider, TestResultsCollectionTask::coverageOutputDir)
        .on(InternalArtifactType.VARIANT_CODE_COVERAGE_DATA)

      creationConfig.global.globalArtifacts
        .use(taskProvider)
        .wiredWith(TestResultsCollectionTask::coverageOutputDir)
        .toAppendTo(InternalMultipleArtifactType.CODE_COVERAGE_DATA)
    }
  }

  abstract class BaseTestResultsCollectionCreationAction(creationConfig: TestReportCreationConfig) :
    VariantTaskCreationAction<TestResultsCollectionTask, TestReportCreationConfig>(creationConfig) {
    override val type = TestResultsCollectionTask::class.java

    override fun configure(task: TestResultsCollectionTask) {
      super.configure(task)

      task.testSuiteResults.set(creationConfig.artifacts.getAll(InternalMultipleArtifactType.TEST_SUITE_RESULTS))

      task.unitTestResults.set(creationConfig.artifacts.get(InternalArtifactType.UNIT_TEST_RESULTS))

      if (!creationConfig.services.projectOptions[BooleanOption.ANDROID_BUILTIN_TEST_PLATFORM]) {
        task.androidTestResults.set(creationConfig.artifacts.get(InternalArtifactType.ANDROID_TEST_RESULTS))
      }

      task.projectRoot.set(task.project.rootDir)

      val jacocoAntConfiguration = getJacocoAntTaskConfiguration(task.project, creationConfig.variantCreationConfig)
      jacocoAntConfiguration?.let { task.jacocoClasspath.setFrom(it) }

      creationConfig.unitTestCoverageFile?.let { task.unitTestCoverageFile.fromDisallowChanges(it) }

      if (!creationConfig.services.projectOptions[BooleanOption.ANDROID_BUILTIN_TEST_PLATFORM]) {
        creationConfig.connectedTestCoverageDirectory?.let { task.connectedTestCoverageDirectory.fromDisallowChanges(it) }
      }

      task.testSuiteCoverageData.fromDisallowChanges(creationConfig.artifacts.getAll(InternalMultipleArtifactType.TEST_SUITE_CODE_COVERAGE))

      creationConfig.java { javaSources -> task.sources.addAll(javaSources.getAsFileTrees()) }
      creationConfig.kotlin { kotlinSources -> task.sources.addAll(kotlinSources.getAsFileTrees()) }
      task.sources.disallowChanges()
      task.classFileCollection.fromDisallowChanges(
        creationConfig.artifacts
          .forScope(ScopedArtifacts.Scope.PROJECT)
          .getScopedArtifactsContainer(ScopedArtifact.CLASSES)
          .finalScopedContent
      )
    }
  }

  interface CodeCoverageWorkParameters : WorkParameters {
    val reportOutputDir: DirectoryProperty
    val unitTestCoverageFile: ConfigurableFileCollection
    val connectedTestCoverageDirectory: ConfigurableFileCollection
    val testSuiteCoverageData: ConfigurableFileCollection
    val classFolders: ConfigurableFileCollection
    val sourceFolders: ConfigurableFileCollection
    val dependantModulesReports: ConfigurableFileCollection
    val variantName: Property<String>
    val projectName: Property<String>
    val projectRoot: DirectoryProperty
  }

  abstract class CodeCoverageCollectionWorkerAction : WorkAction<CodeCoverageWorkParameters> {

    override fun execute() {

      try {
        val usedXmlFileNames = mutableSetOf<String>()
        val formattedName = formatProjectName(parameters.projectName.get())

        val generateXmlReport = { coverageFiles: Collection<File>, testSuiteName: String ->
          if (coverageFiles.isNotEmpty()) {
            val baseReportName = "${parameters.variantName.get()}${formattedName}${testSuiteName.capitalizeFirstChar()}"
            var xmlReportFileName = "${baseReportName}XmlReport"

            var counter = 1
            val originalXmlReportFileName = xmlReportFileName
            while (usedXmlFileNames.contains(xmlReportFileName)) {
              xmlReportFileName = "${originalXmlReportFileName}_${counter++}"
            }
            usedXmlFileNames.add(xmlReportFileName)

            generateReport(
              coverageFiles = coverageFiles,
              reportDir = parameters.reportOutputDir.asFile.get(),
              classFolders = parameters.classFolders.files,
              sourceFolders = parameters.sourceFolders.files,
              tabWidth = 4,
              reportName = baseReportName,
              logger = logger,
              reportTypes = listOf(ReportType.XML),
              xmlReportName = xmlReportFileName,
            )

            val xmlFile = File(parameters.reportOutputDir.asFile.get().absolutePath, "${xmlReportFileName}.xml")
            val rootDir = parameters.projectRoot.get().asFile

            injectMetadataInXmlReport(
              xmlFile,
              mapOf(
                TEST_SUITE_METADATA_MODULE_KEY to parameters.projectName.get(),
                TEST_SUITE_METADATA_SUITE_KEY to testSuiteName,
                TEST_SUITE_METADATA_VARIANT_KEY to parameters.variantName.get(),
              ),
              sourceFolders = parameters.sourceFolders.files.map { it.relativeTo(rootDir).path },
            )
          }
        }

        val unitTestCoverageFile = parameters.unitTestCoverageFile.files.filter { it.exists() }
        generateXmlReport(unitTestCoverageFile, UNIT_TEST_TEST_SUITE_NAME)

        val connectedTestCoverageFile =
          parameters.connectedTestCoverageDirectory.asFileTree.files.filter { file ->
            file.isFile && (file.extension == "ec" || file.extension == "exec")
          }
        generateXmlReport(connectedTestCoverageFile, CONNECTED_TEST_TEST_SUITE_NAME)

        val testSuiteCoverageFiles = mutableListOf<File>()
        parameters.testSuiteCoverageData.files.forEach { directory ->
          getTestSuiteCoverageFiles(directory)?.let { (testSuiteName, coverageFiles) ->
            generateXmlReport(coverageFiles, testSuiteName)
            testSuiteCoverageFiles.addAll(coverageFiles)
          }
        }

        val mergedCoverageFiles = connectedTestCoverageFile + unitTestCoverageFile + testSuiteCoverageFiles
        generateXmlReport(mergedCoverageFiles, "Aggregated")

        parameters.dependantModulesReports.asFileTree.forEach { xmlFile ->
          if (xmlFile.isFile && xmlFile.extension == "xml") {
            val baseName = xmlFile.nameWithoutExtension
            var targetName = baseName
            var counter = 1
            while (usedXmlFileNames.contains(targetName)) {
              targetName = "${baseName}_${counter++}"
            }
            usedXmlFileNames.add(targetName)
            val targetFile = parameters.reportOutputDir.asFile.get().resolve("${targetName}.xml")
            xmlFile.copyTo(targetFile, overwrite = true)
          }
        }
      } catch (e: Exception) {
        logger.warn("Unable to generate Jacoco XML report", e)
      }
    }

    companion object {
      val logger = Logging.getLogger(CodeCoverageCollectionWorkerAction::class.java)

      /**
       * Formats a Gradle project name like ":app" or ":core:datastore" into a capitalized, CamelCase string like "App" or "CoreDatastore".
       *
       * @param projectName The Gradle project path.
       * @return The formatted name.
       */
      fun formatProjectName(projectName: String): String {
        return projectName.split(':').filter { it.isNotEmpty() }.joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
      }

      /**
       * Get the test suite name and the list of coverage files for the given directory.
       *
       * @param directory The directory to look for coverage files.
       * @return A pair of test suite name and a list of coverage files, or null if the directory does not exist or does not contain the
       *   metadata file.
       */
      fun getTestSuiteCoverageFiles(directory: File): Pair<String, List<File>>? {
        if (directory.exists()) {
          val metadataFile = File(directory, TEST_SUITE_METADATA_FILE)
          if (metadataFile.exists()) {
            val metadata = TestSuiteTestTask.parseMetadata(metadataFile)
            val testSuiteName = metadata[TEST_SUITE_METADATA_SUITE_KEY] ?: "unknown_suite"
            val coverageFiles = directory.walkTopDown().filter { file -> file.extension == "ec" || file.extension == "exec" }.toList()
            return Pair(testSuiteName, coverageFiles)
          }
        }
        return null
      }

      /**
       * Injects metadata into the generated Jacoco XML report.
       *
       * This function adds custom properties and source folder information to the XML report generated by Jacoco. The properties are added
       * under a `<properties>` tag, and the source folders are added under a `<sources>` tag.
       *
       * The following properties are added:
       * - "moduleName": The name of the Gradle module.
       * - "testSuiteName": The name of the test suite (e.g., "UnitTest", "AndroidTest", "Aggregated").
       * - "testedVariantName": The name of the Android variant being tested.
       *
       * @param xmlFile The Jacoco XML report file.
       * @param properties A map of key-value pairs to be added as properties.
       * @param sourceFolders A list of source folder paths to be added.
       */
      fun injectMetadataInXmlReport(xmlFile: File, properties: Map<String, String>, sourceFolders: List<String>) {
        try {
          val docFactory = DocumentBuilderFactory.newInstance()
          docFactory.isValidating = false
          docFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
          docFactory.isIgnoringElementContentWhitespace = true

          val docBuilder = docFactory.newDocumentBuilder()
          val document = docBuilder.parse(xmlFile)

          val propertiesList = document.getElementsByTagName("properties")

          val propertiesNode: Node =
            if (propertiesList.length == 0) {
              val node = document.createElement("properties")
              document.documentElement.appendChild(node)
              node
            } else {
              propertiesList.item(0)
            }
          properties.forEach { (key, value) ->
            val propertyElement = document.createElement("property")
            propertyElement.setAttribute("name", key)
            propertyElement.setAttribute("value", value)
            propertiesNode.appendChild(propertyElement)
          }

          val sourcesList = document.getElementsByTagName("sources")

          val sourcesNode: Node =
            if (sourcesList.length == 0) {
              val node = document.createElement("sources")
              document.documentElement.appendChild(node)
              node
            } else {
              sourcesList.item(0)
            }
          sourceFolders.forEach { folderPath ->
            val fileElement = document.createElement("file")
            fileElement.setAttribute("path", folderPath)
            sourcesNode.appendChild(fileElement)
          }

          val transformerFactory = TransformerFactory.newInstance()
          val transformer = transformerFactory.newTransformer()

          document.doctype?.let { doctype ->
            doctype.publicId?.let { transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, it) }
            doctype.systemId?.let { transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, it) }
          }

          val source = DOMSource(document)
          xmlFile.outputStream().buffered().use { outputStream ->
            val result = StreamResult(outputStream)
            transformer.transform(source, result)
          }
        } catch (e: Exception) {
          logger.warn("Failed to inject metadata into XML report: ${xmlFile.absolutePath}", e)
        }
      }
    }
  }

  companion object {
    /**
     * Returns the Jacoco Ant task configuration if the Jacoco version used for unit tests and Android tests is the same.
     *
     * This check is necessary because using different Jacoco versions for different test types can lead to inconsistencies or failures in
     * coverage report generation.
     *
     * @param project The Gradle project.
     * @param creationConfig The component creation configuration.
     * @return A [Configuration] for the Jacoco Ant task if the versions match, otherwise null.
     */
    fun getJacocoAntTaskConfiguration(project: Project, creationConfig: ComponentCreationConfig): Configuration? {
      return if (isJacocoVersionSame(project, creationConfig)) {
        JacocoConfigurations.getJacocoAntTaskConfiguration(project, JacocoTask.getAndroidTestJacocoVersion(creationConfig))
      } else {
        null
      }
    }

    private fun isJacocoVersionSame(project: Project, creationConfig: ComponentCreationConfig): Boolean {
      return getUnitTestJacocoVersion(project, creationConfig) == JacocoTask.getAndroidTestJacocoVersion(creationConfig)
    }
  }
}
