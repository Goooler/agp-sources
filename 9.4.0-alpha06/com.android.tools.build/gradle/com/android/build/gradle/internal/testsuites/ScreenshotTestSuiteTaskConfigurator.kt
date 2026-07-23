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
import com.android.build.gradle.internal.services.DslServices
import java.io.File
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
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
) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> {
    return listOf(
      "-DPreviewScreenshotTestEngineInput.referenceImageDir=${referenceImageDir.get()}",
      "-DPreviewScreenshotTestEngineInput.projectRoot=${projectRoot.get()}",
    )
  }
}

/**
 * Configurator for screenshot test tasks.
 *
 * This class contains the implementation details (system properties, inputs/outputs) for screenshot tests, keeping them out of the DSL
 * layer.
 */
internal class ScreenshotTestSuiteTaskConfigurator(private val suiteName: String) {

  fun configureTask(task: Test, context: TestTaskContext, dslServices: DslServices, providers: ProviderFactory) {
    val isRecordingMode = context.isUpdateTask
    task.systemProperty("PreviewScreenshotTestEngineInput.TestOption.recordingModeEnabled", isRecordingMode.toString())

    val targetName = context.targetName
    val capitalizedTargetName = targetName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString() }

    val variantName = context.targetedVariant
    val capitalizedVariantName = variantName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString() }

    val projectDirectory = dslServices.projectInfo.projectDirectory
    val srcDir = projectDirectory.asFile.resolve("src")

    // Resolve the reference directory: src/[suiteName][Target][Variant]/reference
    // (e.g., src/screenshotTestDefaultDebug/reference)
    val referenceImageDirProvider: Provider<File> =
      providers.provider { srcDir.resolve("${suiteName}${capitalizedTargetName}${capitalizedVariantName}").resolve("reference") }

    // Pass the relative path to the system property to preserve build cache relocatability.
    val relativeReferencePathProvider =
      referenceImageDirProvider.map { referenceDir -> projectDirectory.asFile.toPath().relativize(referenceDir.toPath()).toString() }

    val rootDirProvider = providers.provider { task.project.rootDir.absolutePath }

    task.jvmArgumentProviders.add(ScreenshotArgumentProvider(relativeReferencePathProvider, rootDirProvider))

    // Register the directory as input or output using the Provider API
    if (isRecordingMode) {
      task.outputs.dir(referenceImageDirProvider).withPropertyName("referenceImageDir")
    } else {
      task.inputs
        .files(referenceImageDirProvider)
        .withPropertyName("referenceImageDir")
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .optional()
    }
  }
}
