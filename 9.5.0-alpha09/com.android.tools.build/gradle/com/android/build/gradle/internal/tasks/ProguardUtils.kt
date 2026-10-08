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

import java.io.File
import org.gradle.api.file.Directory
import org.gradle.api.provider.ListProperty

fun checkAarKeepRulesDirectories(directories: ListProperty<Directory>, projectDir: File) {
  checkKeepRulesDirectories(directories, projectDir, "aarKeepRules")
}

fun checkKeepRulesDirectories(directories: ListProperty<Directory>, projectDir: File) {
  checkKeepRulesDirectories(directories, projectDir, "keepRules")
}

private fun checkKeepRulesDirectories(directories: ListProperty<Directory>, projectDir: File, folder: String) {
  val banList = setOf("pro", "pgcfg")
  val proFiles = mutableMapOf<File, MutableList<File>>()
  directories.orNull?.forEach { directory ->
    directory
      .takeIf { it.asFile.exists() }
      ?.asFileTree
      ?.forEach { file ->
        if (file.isFile && file.extension in banList) {
          proFiles.getOrPut(directory.asFile) { mutableListOf() }.add(file)
        }
      }
  }

  if (proFiles.isEmpty()) return

  val message = StringBuffer("Use .keep extensions for $folder source folders. To fix, rename files from list to .keep:\n")
  proFiles.keys.sorted().forEach { directory ->
    val relativeDirectoryPath = directory.relativeTo(projectDir).path
    val files = proFiles[directory]!!.map { it.relativeTo(directory).path }.sorted().joinToString(", ")
    message.append("- $relativeDirectoryPath has $files\n")
  }

  throw RuntimeException(message.toString())
}
