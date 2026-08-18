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

open class DelegateFileMergerOutput(private val delegate: FileMergerOutput) : FileMergerOutput {

  var isOpen = false

  override fun open() {
    delegate.open()
    isOpen = true
  }

  override fun close() {
    delegate.close()
    isOpen = false
  }

  override fun create(path: String, inputs: List<FileMergerInput>, compress: Boolean) {
    if (!isOpen) error("File Merger is not open.")
    delegate.create(path, inputs, compress)
  }
}
