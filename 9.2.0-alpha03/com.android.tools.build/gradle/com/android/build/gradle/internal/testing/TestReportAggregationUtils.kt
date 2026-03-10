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

@file:JvmName("TestReportAggregationUtils")

package com.android.build.gradle.internal.testing

import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_SUITE_METADATA_MODULE_KEY
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_SUITE_METADATA_SUITE_KEY
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_SUITE_METADATA_TARGET_KEY
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_SUITE_METADATA_VARIANT_KEY
import com.android.utils.FileUtils
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.gradle.api.file.DirectoryProperty

const val TEST_RESULT_METADATA_FILE = "metadata.txt"

fun processTestReportAggregation(
  sourceDir: File,
  xmlResultsDirectory: DirectoryProperty,
  modulePath: String,
  variantName: String,
  suiteName: String,
  target: String,
) {
  var metadataDir = sourceDir

  if (xmlResultsDirectory.isPresent) {
    val destDir = xmlResultsDirectory.get().asFile
    metadataDir = destDir
    if (sourceDir.exists()) {
      try {
        Files.walk(sourceDir.toPath()).forEach { source ->
          try {
            val destination = destDir.toPath().resolve(sourceDir.toPath().relativize(source))
            if (Files.isDirectory(source)) {
              Files.createDirectories(destination)
            } else {
              FileUtils.copyFile(source.toFile(), destination.toFile())
            }
          } catch (e: IOException) {
            throw java.io.UncheckedIOException(e)
          }
        }
      } catch (e: IOException) {
        throw RuntimeException("Failed to copy JUnit XML results to xmlResultsDirectory", e)
      }
    }
  }

  // If test report aggregation is enabled, we need to create the metadata file.
  metadataDir.mkdirs()
  val metadataFile = File(metadataDir, TEST_RESULT_METADATA_FILE)

  val content =
    """
          $TEST_SUITE_METADATA_MODULE_KEY=$modulePath
          $TEST_SUITE_METADATA_VARIANT_KEY=$variantName
          $TEST_SUITE_METADATA_SUITE_KEY=$suiteName
          $TEST_SUITE_METADATA_TARGET_KEY=$target
      """
      .trimIndent()

  try {
    if (metadataFile.exists() && !metadataFile.canWrite()) {
      metadataFile.setWritable(true)
    }
    Files.write(metadataFile.toPath(), content.toByteArray(StandardCharsets.UTF_8))
  } catch (e: IOException) {
    throw RuntimeException("Failed to write metadata file", e)
  }
}
