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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.R8D8ThreadPoolBuildService
import com.android.build.gradle.internal.services.doClose
import com.android.build.gradle.options.SyncOptions
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.BuilderConstants.FD_R8_REPORTS
import com.android.builder.dexing.KeepRuleFile
import com.android.builder.dexing.MainDexListConfig
import com.android.builder.dexing.ProguardConfig
import com.android.builder.dexing.ProguardOutputReports
import com.android.builder.dexing.ToolConfig
import com.android.builder.dexing.runR8
import java.io.File
import java.nio.file.Path
import javax.inject.Inject
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

/** Task that runs R8 config analyzer to analyze proguard keep rules. Writes output to build/reports. */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.OPTIMIZATION)
abstract class R8AnalysisTask @Inject constructor(projectLayout: ProjectLayout) : BaseR8Task(projectLayout) {

  @get:OutputFile abstract val r8ConfigurationAnalyzerDataOutput: RegularFileProperty

  @get:OutputFile abstract val r8ConfigurationAnalyzerReportOutput: RegularFileProperty

  class CreationAction(creationConfig: ConsumableCreationConfig, private val buildTypeName: String) :
    BaseR8Task.CreationAction<R8AnalysisTask, ConsumableCreationConfig>(
      creationConfig,
      isTestApplication = false,
      addCompileRClass = false,
    ) {
    override val type = R8AnalysisTask::class.java
    override val name = computeTaskName("analyze", "R8Config")

    override fun handleProvider(taskProvider: TaskProvider<R8AnalysisTask>) {
      // we do not call super.handleProvider to not initialize additional outputs
      val reportsDir = creationConfig.services.projectInfo.getReportsDir()
      creationConfig.artifacts
        .setInitialProvider(taskProvider, R8AnalysisTask::r8ConfigurationAnalyzerDataOutput)
        .atLocation(reportsDir.map { it.dir(FD_R8_REPORTS) })
        .withName("r8-config-analyzer-$buildTypeName.pb")
        .on(InternalArtifactType.R8_REPORT_PB)
      creationConfig.artifacts
        .setInitialProvider(taskProvider, R8AnalysisTask::r8ConfigurationAnalyzerReportOutput)
        .atLocation(reportsDir.map { it.dir(FD_R8_REPORTS) })
        .withName("r8-config-analyzer-$buildTypeName.html")
        .on(InternalArtifactType.R8_REPORT_HTML)
    }

    override fun configure(task: R8AnalysisTask) {
      super.configure(task)
      creationConfig.artifacts.setTaskInputToFinalProduct(InternalArtifactType.ORIGINAL_MERGED_JAVA_RES, task.resourcesJar)
    }
  }

  override fun doTaskAction() {
    // verify r8 gradual settings
    verifyGradualShrinkingConfiguration()

    val reportFile = r8ConfigurationAnalyzerReportOutput.get().asFile
    val dataFile = r8ConfigurationAnalyzerDataOutput.get().asFile

    val workerAction = { it: R8RunnableAnalysis.Params ->
      it.bootClasspath.from(bootClasspath.toList())
      it.mainDexListFiles.from(getCombinedMainDexListFiles())
      it.mainDexRulesFiles.from(getCombinedMainDexRulesFiles())
      it.proguardConfigurationFiles.set(
        reconcileDefaultProguardFile(obtainKeepRules(), extractedDefaultProguardFile, failOnMissingProguardFiles.get())
      )
      it.inputProguardMapping.set(
        if (testedMappingFile.isEmpty) {
          null
        } else {
          testedMappingFile.singleFile
        }
      )
      it.proguardConfigurations.set(proguardConfigurations)
      it.legacyMultiDexEnabled.set(legacyMultiDexEnabled)
      it.referencedInputs.from((referencedClasses + referencedResources).toList())
      it.classes.from(classes.toList())
      it.resourcesJar.set(resourcesJar)
      it.r8ConfigurationAnalyzerDataOutput.set(dataFile)
      it.r8ConfigurationAnalyzerReportOutput.set(reportFile)
      it.featureClassJars.from(featureClassJars.toList())
      it.featureJavaResourceJars.from(featureJavaResourceJars.toList())
      it.libConfiguration.set(coreLibDesugarConfig.orNull)
      it.errorFormatMode.set(errorFormatMode.get())
      it.inputArtProfile.set(inputArtProfile)
      it.inputProfileForDexStartupOptimization.set(inputProfileForDexStartupOptimization)
      it.toolConfig.set(toolParameters.toToolConfig())
      it.resourceShrinkingConfig.set(resourceShrinkingParams.toConfig())
      it.partialShrinkingIncludes.set(aggregatePartialShrinkingConfig())
      if (executionOptions.get().runInSeparateProcess) {
        it.r8ThreadPoolSizeIfIsolationMode.set(r8ThreadPoolSize)
      } else {
        it.r8D8ThreadPoolBuildServiceIfNonIsolationMode.set(r8D8ThreadPoolBuildService)
      }
    }
    if (executionOptions.get().runInSeparateProcess) {
      workerExecutor
        .processIsolation { spec ->
          spec.forkOptions { forkOptions ->
            forkOptions.jvmArgs(executionOptions.get().jvmArgs)
            forkOptions.systemProperties(System.getProperties().mapKeys { it.key.toString() })
          }
        }
        .submit(R8RunnableAnalysis::class.java, workerAction)
    } else {
      workerExecutor.noIsolation().submit(R8RunnableAnalysis::class.java, workerAction)
    }

    // Print relative path to the report file
    val relativePath = projectLayout.projectDirectory.asFile.toURI().relativize(reportFile.toURI()).path
    logger.lifecycle("R8 Keep Rules analysis report generated: $relativePath")
  }

  abstract class R8RunnableAnalysis : WorkAction<R8RunnableAnalysis.Params> {

    abstract class Params : WorkParameters {
      abstract val bootClasspath: ConfigurableFileCollection
      abstract val mainDexListFiles: ConfigurableFileCollection
      abstract val mainDexRulesFiles: ConfigurableFileCollection
      abstract val proguardConfigurationFiles: ListProperty<KeepRuleFile>
      abstract val inputProguardMapping: RegularFileProperty
      abstract val proguardConfigurations: ListProperty<String>
      abstract val r8ConfigurationAnalyzerDataOutput: RegularFileProperty
      abstract val r8ConfigurationAnalyzerReportOutput: RegularFileProperty
      abstract val legacyMultiDexEnabled: Property<Boolean>
      abstract val referencedInputs: ConfigurableFileCollection
      abstract val classes: ConfigurableFileCollection
      abstract val resourcesJar: RegularFileProperty
      abstract val featureClassJars: ConfigurableFileCollection
      abstract val featureJavaResourceJars: ConfigurableFileCollection
      abstract val libConfiguration: Property<String>
      abstract val errorFormatMode: Property<SyncOptions.ErrorFormatMode>
      abstract val inputArtProfile: RegularFileProperty
      abstract val inputProfileForDexStartupOptimization: RegularFileProperty
      abstract val toolConfig: Property<ToolConfig>
      abstract val resourceShrinkingConfig: Property<com.android.builder.dexing.ResourceShrinkingConfig>
      abstract val partialShrinkingIncludes: Property<com.android.builder.dexing.PartialShrinking>
      abstract val r8ThreadPoolSizeIfIsolationMode: Property<Int>
      abstract val r8D8ThreadPoolBuildServiceIfNonIsolationMode: Property<R8D8ThreadPoolBuildService>
    }

    private fun filterMissingFiles(files: List<File>, logger: LoggerWrapper): List<Path> {
      return files.mapNotNull { file ->
        if (file.exists()) file.toPath()
        else {
          logger.verbose("$file is ignored as it does not exist.")
          null
        }
      }
    }

    override fun execute() {
      val isolationMode = parameters.r8ThreadPoolSizeIfIsolationMode.isPresent
      val r8ThreadPool =
        if (isolationMode) {
          R8D8ThreadPoolBuildService.newThreadPool(parameters.r8ThreadPoolSizeIfIsolationMode.get())
        } else {
          parameters.r8D8ThreadPoolBuildServiceIfNonIsolationMode.get().threadPool
        }
      try {
        val proguardOutputReports =
          ProguardOutputReports(
            parameters.r8ConfigurationAnalyzerDataOutput.get().asFile.toPath(),
            parameters.r8ConfigurationAnalyzerReportOutput.get().asFile.toPath(),
          )
        val proguardConfig =
          ProguardConfig(
            parameters.proguardConfigurationFiles.get(),
            parameters.inputProguardMapping.orNull?.asFile?.toPath(),
            parameters.proguardConfigurations.get(),
            null,
            proguardOutputReports,
          )
        val mainDexListConfig =
          if (parameters.legacyMultiDexEnabled.get()) {
            MainDexListConfig(
              parameters.mainDexRulesFiles.files.map { it.toPath() },
              parameters.mainDexListFiles.files.map { it.toPath() },
              getPlatformRules(),
              null,
            )
          } else {
            MainDexListConfig()
          }

        val logger = LoggerWrapper.getLogger(R8AnalysisTask::class.java)
        runR8(
          filterMissingFiles(parameters.classes.files.toList(), logger),
          null,
          parameters.resourcesJar.asFile.get().toPath(),
          null,
          parameters.bootClasspath.files.map { it.toPath() },
          filterMissingFiles(parameters.referencedInputs.files.toList(), logger),
          parameters.toolConfig.get(),
          proguardConfig,
          mainDexListConfig,
          parameters.resourceShrinkingConfig.orNull,
          MessageReceiverImpl(parameters.errorFormatMode.get(), Logging.getLogger(R8RunnableAnalysis::class.java)),
          parameters.featureClassJars.files.map { it.toPath() },
          parameters.featureJavaResourceJars.files.map { it.toPath() },
          null,
          null,
          parameters.libConfiguration.orNull,
          parameters.inputArtProfile.orNull?.asFile?.toPath(),
          null,
          parameters.inputProfileForDexStartupOptimization.orNull?.asFile?.toPath(),
          null,
          parameters.partialShrinkingIncludes.orNull,
          r8ThreadPool,
        )
      } finally {
        if (isolationMode) {
          r8ThreadPool.doClose()
        }
      }
    }
  }
}
