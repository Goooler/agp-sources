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

package com.android.build.gradle.internal.dependency

import com.android.SdkConstants
import com.android.build.gradle.internal.tasks.MergeJavaResourceTask
import com.android.builder.merge.FileMapInput
import com.android.builder.merge.FileMerger
import com.android.builder.merge.FileMergerInput
import com.android.builder.merge.FileMergerOutputs
import com.android.builder.merge.FilterFileMergerInput
import com.android.builder.merge.InputStreamMerger
import com.android.builder.merge.LazyFileMergerInput
import com.android.builder.merge.MergeOutputWriters
import com.android.builder.packaging.ParsedPackagingOptions
import java.io.File
import java.util.function.Predicate

/** Provides utilities for compressing types representing Java Resources into Jar files for merging. */
sealed class UncompressedJavaRes(protected val packagingOption: ParsedPackagingOptions) {

  protected abstract val fileInputs: List<FileMergerInput>

  fun compressToJar(outputFile: File): File = mergeAndCompress(fileInputs, outputFile)

  private val inputsFilter: Predicate<String> =
    MergeJavaResourceTask.predicate.and { path ->
      packagingOption.getAction(path) != ParsedPackagingOptions.JavaResPackagingFileAction.EXCLUDE
    }

  private fun merge(inputs: List<FileMergerInput>, compress: Boolean, outputFile: File): File {
    outputFile.delete()
    val merger = InputStreamMerger(packagingOption)
    val writer = MergeOutputWriters.toZipWithZipFlinger(outputFile)
    val output = FileMergerOutputs.fromAlgorithmAndWriter(merger, writer)
    val filteredInputs = inputs.map { FilterFileMergerInput(it, inputsFilter) }
    FileMerger.merge(filteredInputs, output, noCompressPredicate = { !compress })
    return outputFile
  }

  protected fun mergeAndCompress(inputs: List<FileMergerInput>, outputFile: File) = merge(inputs, true, outputFile)

  class FileTree(
    val fileTree: org.gradle.api.file.FileTree,
    packagingOptions: ParsedPackagingOptions = ParsedPackagingOptions(emptyList(), emptyList(), emptyList()),
  ) : UncompressedJavaRes(packagingOptions) {

    override val fileInputs: List<FileMergerInput>
      get() {
        val pathsToFile = mutableMapOf<String, File>()
        fileTree.visit { details ->
          if (!details.isDirectory && MergeJavaResourceTask.predicate.test(details.relativePath.pathString)) {
            pathsToFile[details.relativePath.pathString] = details.file
          }
        }
        return listOf(FileMapInput("fileTree", pathsToFile))
      }
  }

  class Jar(val jar: File, packagingOptions: ParsedPackagingOptions = ParsedPackagingOptions(emptyList(), emptyList(), emptyList())) :
    UncompressedJavaRes(packagingOptions) {

    init {
      if (!jar.isFile || jar.extension != SdkConstants.EXT_JAR) {
        error("$jar must be a jar containing java resources.")
      }
    }

    override val fileInputs = listOf(LazyFileMergerInput(jar.name, jar))
  }

  class MultipleJars(
    val jars: List<File>,
    packagingOptions: ParsedPackagingOptions = ParsedPackagingOptions(emptyList(), emptyList(), emptyList()),
  ) : UncompressedJavaRes(packagingOptions) {

    override val fileInputs = jars.map { LazyFileMergerInput(it.name, it) }
  }
}
