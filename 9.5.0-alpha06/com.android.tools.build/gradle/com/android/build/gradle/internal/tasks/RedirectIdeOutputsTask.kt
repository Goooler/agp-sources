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

import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import com.android.utils.FileUtils
import java.io.File
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault

/**
 * Base task class for copying AGP variant outputs to custom overridden IDE directories. Implements common properties, liveness
 * configuration, smart copy functionality, and file filters.
 */
@DisableCachingByDefault(
  because = "Calculating cache hit/miss and fetching results is likely more expensive than simply executing the task"
)
abstract class BaseRedirectIdeOutputsTask : NonIncrementalTask() {

  init {
    outputs.upToDateWhen { false }
  }

  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) @get:Optional abstract val obfuscationMappingFile: ConfigurableFileCollection

  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) @get:Optional abstract val nativeDebugMetadata: ConfigurableFileCollection

  @get:Internal abstract val targetDir: DirectoryProperty

  @get:OutputFile abstract val markerFile: RegularFileProperty

  protected fun resolveObfuscationMappingFile(): File? {
    return obfuscationMappingFile.filter { it.isFile }.singleOrNull()
  }

  protected fun resolveNativeDebugMetadata(): File? {
    return nativeDebugMetadata.filter { it.isFile }.singleOrNull()
  }

  protected fun copyIfChanged(src: File, dest: File) {
    if (!dest.exists() || src.lastModified() > dest.lastModified() || src.length() != dest.length()) {
      FileUtils.copyFile(src, dest)
    }
  }

  companion object {
    const val OBFUSCATION_MAPPING_FILENAME = "mapping.txt"
    const val NATIVE_DEBUG_SYMBOLS_FILENAME = "native-debug-symbols.zip"
    const val OUTPUT_METADATA_FILENAME = "output-metadata.json"
  }
}

/**
 * Task responsible for copying APK outputs and associated minification mappings (R8 mapping.txt) or native debug symbols
 * (native-debug-symbols.zip) to a custom wizard-selected output location passed via the `android.injected.apk.location` property.
 *
 * This task is decoupled from the bundle redirection task to prevent task-graph coupling (e.g. so that building APKs does not trigger
 * bundle creation, and vice-versa).
 *
 * To avoid Gradle overlapping output directory violations, the target custom directory is annotated as `@Internal`, and a dummy marker file
 * is registered as `@OutputFile`. It sets `outputs.upToDateWhen { false }` to ensure any manual deletions in the target directory or path
 * overrides are always caught, but uses a smart copy method to preserve file timestamps for fast incremental deployment in Android Studio.
 */
@DisableCachingByDefault(
  because = "Calculating cache hit/miss and fetching results is likely more expensive than simply executing the task"
)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MISC)
abstract class RedirectIdeApkOutputsTask : BaseRedirectIdeOutputsTask() {

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val apkFiles: DirectoryProperty

  override fun doTaskAction() {
    val destDir = targetDir.get().asFile
    if (!destDir.exists()) {
      destDir.mkdirs()
    }

    val srcDir = apkFiles.get().asFile
    val srcFiles =
      if (srcDir.exists()) {
        srcDir.listFiles()?.filter { it.isFile } ?: emptyList()
      } else {
        emptyList()
      }

    val mappingFile = resolveObfuscationMappingFile()
    val nativeZip = resolveNativeDebugMetadata()

    val expectedTargetNames = mutableSetOf<String>()
    srcFiles.forEach { expectedTargetNames.add(it.name) }
    if (mappingFile != null && mappingFile.exists()) {
      expectedTargetNames.add(OBFUSCATION_MAPPING_FILENAME)
    }
    if (nativeZip != null && nativeZip.exists()) {
      expectedTargetNames.add(NATIVE_DEBUG_SYMBOLS_FILENAME)
    }

    // Delete only stale files
    destDir.listFiles()?.forEach { file ->
      val name = file.name
      if (
        (name.endsWith(".apk") ||
          name == OBFUSCATION_MAPPING_FILENAME ||
          name == NATIVE_DEBUG_SYMBOLS_FILENAME ||
          name == OUTPUT_METADATA_FILENAME) && !expectedTargetNames.contains(name)
      ) {
        file.delete()
      }
    }

    // Smart copy
    srcFiles.forEach { srcFile -> copyIfChanged(srcFile, File(destDir, srcFile.name)) }
    if (mappingFile != null && mappingFile.exists()) {
      copyIfChanged(mappingFile, File(destDir, OBFUSCATION_MAPPING_FILENAME))
    }
    if (nativeZip != null && nativeZip.exists()) {
      copyIfChanged(nativeZip, File(destDir, NATIVE_DEBUG_SYMBOLS_FILENAME))
    }

    markerFile.get().asFile.writeText("APK outputs redirected successfully")
  }

  class CreationAction(creationConfig: ApkCreationConfig, private val targetDir: File) :
    VariantTaskCreationAction<RedirectIdeApkOutputsTask, ApkCreationConfig>(creationConfig) {

    override val name: String
      get() = computeTaskName("redirectIdeApkOutputs")

    override val type: Class<RedirectIdeApkOutputsTask>
      get() = RedirectIdeApkOutputsTask::class.java

    override fun configure(task: RedirectIdeApkOutputsTask) {
      super.configure(task)
      task.targetDir.set(targetDir)

      val apkProvider = creationConfig.artifacts.get(SingleArtifact.APK)
      task.apkFiles.set(apkProvider)

      val mappingProvider = creationConfig.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
      task.obfuscationMappingFile.from(mappingProvider.map { listOf(it) }.orElse(emptyList()))

      val nativeProvider = creationConfig.artifacts.get(InternalArtifactType.MERGED_NATIVE_DEBUG_METADATA)
      task.nativeDebugMetadata.from(nativeProvider.map { listOf(it) }.orElse(emptyList()))

      task.markerFile.set(creationConfig.paths.intermediatesDir("ide_redirect_marker", name).map { it.file("apk_redirect_marker.txt") })
    }
  }
}

/**
 * Task responsible for copying Bundle (.aab) outputs and associated minification mappings (R8 mapping.txt) or native debug symbols
 * (native-debug-symbols.zip) to the custom wizard-selected output location passed via the `android.injected.apk.location` property.
 *
 * Runs only on bundle builds and coordinates with the APK redirection task to run sequentially (using mustRunAfter) to prevent concurrent
 * write collisions on shared outputs.
 */
@DisableCachingByDefault(
  because = "Calculating cache hit/miss and fetching results is likely more expensive than simply executing the task"
)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MISC)
abstract class RedirectIdeBundleOutputsTask : BaseRedirectIdeOutputsTask() {

  @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val bundleFile: RegularFileProperty

  override fun doTaskAction() {
    val destDir = targetDir.get().asFile
    if (!destDir.exists()) {
      destDir.mkdirs()
    }

    val bundle = bundleFile.get().asFile
    val mappingFile = resolveObfuscationMappingFile()
    val nativeZip = resolveNativeDebugMetadata()

    val expectedTargetNames = mutableSetOf<String>()
    if (bundle.exists()) {
      expectedTargetNames.add(bundle.name)
    }
    if (mappingFile != null && mappingFile.exists()) {
      expectedTargetNames.add(OBFUSCATION_MAPPING_FILENAME)
    }
    if (nativeZip != null && nativeZip.exists()) {
      expectedTargetNames.add(NATIVE_DEBUG_SYMBOLS_FILENAME)
    }

    // Delete only stale files
    destDir.listFiles()?.forEach { file ->
      val name = file.name
      if (
        (name.endsWith(".aab") || name == OBFUSCATION_MAPPING_FILENAME || name == NATIVE_DEBUG_SYMBOLS_FILENAME) &&
          !expectedTargetNames.contains(name)
      ) {
        file.delete()
      }
    }

    // Smart copy
    if (bundle.exists()) {
      copyIfChanged(bundle, File(destDir, bundle.name))
    }
    if (mappingFile != null && mappingFile.exists()) {
      copyIfChanged(mappingFile, File(destDir, OBFUSCATION_MAPPING_FILENAME))
    }
    if (nativeZip != null && nativeZip.exists()) {
      copyIfChanged(nativeZip, File(destDir, NATIVE_DEBUG_SYMBOLS_FILENAME))
    }

    markerFile.get().asFile.writeText("Bundle outputs redirected successfully")
  }

  class CreationAction(creationConfig: ApkCreationConfig, private val targetDir: File) :
    VariantTaskCreationAction<RedirectIdeBundleOutputsTask, ApkCreationConfig>(creationConfig) {

    override val name: String
      get() = computeTaskName("redirectIdeBundleOutputs")

    override val type: Class<RedirectIdeBundleOutputsTask>
      get() = RedirectIdeBundleOutputsTask::class.java

    override fun configure(task: RedirectIdeBundleOutputsTask) {
      super.configure(task)
      task.targetDir.set(targetDir)

      val bundleProvider = creationConfig.artifacts.get(SingleArtifact.BUNDLE)
      task.bundleFile.set(bundleProvider)

      val mappingProvider = creationConfig.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
      task.obfuscationMappingFile.from(mappingProvider.map { listOf(it) }.orElse(emptyList()))

      val nativeProvider = creationConfig.artifacts.get(InternalArtifactType.MERGED_NATIVE_DEBUG_METADATA)
      task.nativeDebugMetadata.from(nativeProvider.map { listOf(it) }.orElse(emptyList()))

      task.markerFile.set(creationConfig.paths.intermediatesDir("ide_redirect_marker", name).map { it.file("bundle_redirect_marker.txt") })
    }
  }
}
