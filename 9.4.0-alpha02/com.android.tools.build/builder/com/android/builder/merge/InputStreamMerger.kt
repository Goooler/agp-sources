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

class InputStreamMerger(val packagingOption: ParsedPackagingOptions) {

  private val merger = { path: String, inStreams: List<MergeInput> ->
    val packagingAction = packagingOption.getAction(path)
    when (packagingAction) {
      ParsedPackagingOptions.JavaResPackagingFileAction.NONE -> onlyOne(path, inStreams)
      ParsedPackagingOptions.JavaResPackagingFileAction.MERGE -> concat(inStreams)
      ParsedPackagingOptions.JavaResPackagingFileAction.PICK_FIRST -> pickFirst(inStreams)
      ParsedPackagingOptions.JavaResPackagingFileAction.EXCLUDE -> throw AssertionError()
    }
  }

  fun merge(path: String, from: () -> List<MergeInput>, action: (mergedInputStream: InputStream) -> Unit) {
    val inStreams = from()
    merger.invoke(path, inStreams).use { action(it) }
    inStreams.forEach { it.stream.close() }
  }

  private fun pickFirst(from: List<MergeInput>): InputStream = from.first().stream

  private fun concat(from: List<MergeInput>): InputStream = CombinedInputStream(from, true)

  private fun onlyOne(path: String, from: List<MergeInput>): InputStream {
    if (from.size > 1) {
      throw DuplicateRelativeFileException(path, from.map(MergeInput::name))
    }
    return from.single().stream
  }
}
