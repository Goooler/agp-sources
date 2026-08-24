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

import com.android.zipflinger.ZipRepo
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException

class LazyFileMergerInput(private val name: String, private val jarFile: File) : FileMergerInput {

  private var zipRepo: ZipRepo? = null
  private val paths: Lazy<Set<String>> = lazy {
    if (!jarFile.exists()) {
      emptySet()
    } else {
      ZipRepo(jarFile.toPath()).use { repo -> repo.entries.values.filter { !it.isDirectory }.map { it.name }.toSet() }
    }
  }

  override fun getName(): String = name

  override fun getAllPaths(): Set<String> = paths.value

  override fun open() {
    try {
      zipRepo = ZipRepo(jarFile.toPath())
    } catch (e: IOException) {
      throw UncheckedIOException(e)
    }
  }

  override fun close() {
    try {
      zipRepo?.close()
    } catch (e: IOException) {
      throw UncheckedIOException(e)
    } finally {
      zipRepo = null
    }
  }

  override fun openPath(path: String): InputStream {
    try {
      return zipRepo!!.getInputStream(path)
    } catch (e: IOException) {
      throw UncheckedIOException(e)
    }
  }
}
