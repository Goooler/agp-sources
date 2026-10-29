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

/** Factories for instances of [FileMergerOutput]. */
object FileMergerOutputs {

  @JvmStatic
  fun fromAlgorithmAndWriter(merger: InputStreamMerger, writer: MergeOutputWriter): FileMergerOutput {
    return object : FileMergerOutput {
      override fun open() {
        writer.open()
      }

      override fun close() {
        writer.close()
      }

      override fun create(path: String, inputs: List<FileMergerInput>, compress: Boolean) {
        merger.merge(
          path,
          compress,
          {
            inputs.map { input ->
              input.open()
              MergeInput(input.openPath(path), input.getName())
            }
          },
        ) {
          writer.create(path, it, compress)
        }
      }
    }
  }

  @JvmStatic
  fun fromAlgorithmAndWriter(merger: JavaResZipSourceMerger, writer: SourceMergeOutputWriter): FileMergerOutput {
    return object : FileMergerOutput {
      override fun open() {
        writer.open()
      }

      override fun close() {
        writer.close()
      }

      override fun create(path: String, inputs: List<FileMergerInput>, compress: Boolean) {
        merger.merge(path, compress, { inputs }) { result ->
          when (result) {
            is MergedSourceResult.ZipSource -> writer.create(path, result.source)
            is MergedSourceResult.InputStream -> writer.create(path, result.stream, compress)
          }
        }
      }
    }
  }
}
