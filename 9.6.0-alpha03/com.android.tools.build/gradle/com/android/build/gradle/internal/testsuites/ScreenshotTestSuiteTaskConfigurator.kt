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

package com.android.build.gradle.internal.testsuites

import com.android.build.api.dsl.TestTaskContext
import com.android.build.api.variant.impl.capitalizeFirstChar
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.options.BooleanOption
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.process.CommandLineArgumentProvider

/**
 * [CommandLineArgumentProvider] for screenshot test tasks.
 *
 * Supplies system properties (`referenceImageDir` and `projectRoot`) required by the screenshot validation JUnit engine
 * (`PreviewScreenshotTestEngineInput`). Using [CommandLineArgumentProvider] with `@get:Input` properties ensures Gradle properly tracks
 * these task inputs across builds and preserves build cache relocatability.
 */
internal class ScreenshotArgumentProvider(
  @get:Input val referenceImageDir: Provider<String>,
  @get:Input val projectRoot: Provider<String>,
  @get:Input @get:Optional val threshold: Float?,
) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> {
    return buildList {
      add("-DPreviewScreenshotTestEngineInput.referenceImageDir=${referenceImageDir.get()}")
      add("-DPreviewScreenshotTestEngineInput.projectRoot=${projectRoot.get()}")
      if (threshold != null) {
        add("-DPreviewScreenshotTestEngineInput.ImageDiffer.threshold=$threshold")
      }
    }
  }
}

/**
 * Configurator for screenshot test tasks.
 *
 * This class contains the implementation details (system properties, inputs/outputs) for screenshot tests, keeping them out of the DSL
 * layer.
 */
internal class ScreenshotTestSuiteTaskConfigurator(private val suiteName: String) {

  fun configureTask(task: Test, context: TestTaskContext, dslServices: DslServices, providers: ProviderFactory, threshold: Float?) {
    val isRecordingMode = context.isUpdateTask
    task.systemProperty("PreviewScreenshotTestEngineInput.TestOption.recordingModeEnabled", isRecordingMode.toString())
    task.testLogging {
      it.showStandardStreams = true
      if (!isRecordingMode) {
        // Gradle's default SHORT exception format prints only the exception class name,
        // which hides the image comparison details (paths and diff percentage). The
        // screenshot exceptions carry no stack frames, so FULL prints the message once and
        // adds no stack trace noise.
        it.exceptionFormat = TestExceptionFormat.FULL
      }
    }

    val capitalizedTargetName = context.targetName.capitalizeFirstChar()
    val capitalizedVariantName = context.targetedVariant.capitalizeFirstChar()
    val suiteTargetVariant = "${suiteName}${capitalizedTargetName}${capitalizedVariantName}"

    val projectDirectory = dslServices.projectInfo.projectDirectory
    val useSourceReferenceDirectories = dslServices.projectOptions[BooleanOption.SCREENSHOT_TEST_USE_SOURCE_REFERENCE_DIRECTORIES]

    val referenceImageDirProvider: Provider<File> =
      if (useSourceReferenceDirectories) {
        // Resolve the source reference directory: src/[suiteName][Target][Variant]/reference
        // (e.g., src/screenshotTestDefaultDebug/reference)
        val srcDir = projectDirectory.asFile.resolve("src")
        providers.provider { srcDir.resolve(suiteTargetVariant).resolve("reference") }
      } else {
        // Resolve the local build output reference directory:
        // build/outputs/screenshotTest-results/[suiteName][Target][Variant]/reference
        dslServices.projectInfo.getOutputsDir().map { outputsDir ->
          outputsDir.dir("screenshotTest-results/$suiteTargetVariant/reference").asFile
        }
      }

    // Pass the relative path to the system property to preserve build cache relocatability.
    val projectDirFile = projectDirectory.asFile
    val relativeReferencePathProvider = referenceImageDirProvider.map { referenceDir ->
      referenceDir.relativeToOrSelf(projectDirFile).path
    }

    val rootDirPath = dslServices.projectInfo.rootDir.absolutePath
    val rootDirProvider = providers.provider { rootDirPath }

    task.jvmArgumentProviders.add(ScreenshotArgumentProvider(relativeReferencePathProvider, rootDirProvider, threshold))

    // Register the directory as input or output using the Provider API
    if (isRecordingMode) {
      task.outputs.dir(referenceImageDirProvider).withPropertyName("referenceImageDir")
    } else {
      task.inputs
        .files(referenceImageDirProvider)
        .withPropertyName("referenceImageDir")
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .optional()

      if (!useSourceReferenceDirectories) {
        val targetedVariant = context.targetedVariant
        val propName = BooleanOption.SCREENSHOT_TEST_USE_SOURCE_REFERENCE_DIRECTORIES.propertyName
        task.doFirst {
          val refDir = referenceImageDirProvider.get()
          if (!refDir.exists() || refDir.walkTopDown().none { it.isFile }) {
            throw GradleException(
              "Local reference screenshot directory '${refDir.absolutePath}' does not exist or is empty. " +
                "When '$propName=false', run the screenshot update task for variant '$targetedVariant' first to generate local reference images, " +
                "or set '$propName=true' to validate against source reference images."
            )
          }
        }
      }
    }
  }
}
