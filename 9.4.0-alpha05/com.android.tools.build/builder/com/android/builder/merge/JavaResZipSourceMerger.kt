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

package com.android.builder.merge

import com.android.builder.packaging.ParsedPackagingOptions
import java.io.InputStream

class JavaResZipSourceMerger(
  private val packagingOption: ParsedPackagingOptions,
  private val inputStreamMerger: InputMerger<MergeInput, InputStream> = InputStreamMerger(packagingOption),
) : InputMerger<FileMergerInputNonIncremental, MergedSourceResult> {

  override fun merge(
    path: String,
    compress: Boolean,
    from: () -> List<FileMergerInputNonIncremental>,
    action: (mergedSource: MergedSourceResult) -> Unit,
  ) {
    val inputs = from()
    when (val packagingAction = packagingOption.getAction(path)) {
      ParsedPackagingOptions.JavaResPackagingFileAction.NONE,
      ParsedPackagingOptions.JavaResPackagingFileAction.PICK_FIRST -> {
        val input =
          if (packagingAction == ParsedPackagingOptions.JavaResPackagingFileAction.NONE) {
            if (inputs.size > 1) {
              throw DuplicateRelativeFileException(path, inputs.map { it.getName() })
            }
            inputs.single()
          } else {
            inputs.first()
          }
        input.open()
        if (!compress) {
          action(MergedSourceResult.InputStream(input.openPath(path)))
        } else {
          action(MergedSourceResult.ZipSource(input.openAsZipSource(path)))
        }
      }
      ParsedPackagingOptions.JavaResPackagingFileAction.MERGE -> {
        // Fallback to InputStream merger for actual concatenation
        inputStreamMerger.merge(
          path,
          compress,
          {
            inputs.map { input ->
              input.open()
              MergeInput(input.openPath(path), input.getName())
            }
          },
        ) {
          action(MergedSourceResult.InputStream(it))
        }
      }
      ParsedPackagingOptions.JavaResPackagingFileAction.EXCLUDE -> throw AssertionError()
    }
  }
}

interface MergedSourceResult {
  class InputStream(val stream: java.io.InputStream) : MergedSourceResult

  class ZipSource(val source: com.android.zipflinger.ZipSource) : MergedSourceResult
}
