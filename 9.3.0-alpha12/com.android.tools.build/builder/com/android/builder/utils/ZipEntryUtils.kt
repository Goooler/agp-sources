/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("ZipEntryUtils")

package com.android.builder.utils

import java.io.File
import java.util.zip.ZipEntry

/**
 * Validates the name of a zip entry to prevent directory traversal attacks (e.g., Zip-Slip).
 *
 * This function returns true if the entry is safe. It specifically rejects:
 * - Traversal sequences (`..`) and current directory (`.`) components.
 * - Absolute Windows paths with drives (`:`).
 * - Control characters (e.g., line feeds), which can be used for command injection.
 *
 * Note: This function does NOT reject absolute paths (e.g., leading `/`). This is intentional to support legitimate zip-to-zip copying
 * tasks in the build system (such as PackageAndroidArtifact) where leading slashes are present and harmless. Extracting callers that write
 * to the filesystem MUST additionally use [isValidZipEntryPath] or equivalent boundary checks to ensure the resolved path does not escape
 * the output directory.
 *
 * @param entry The zip entry to validate.
 * @return `true` if the entry name is considered safe, `false` otherwise.
 */
fun isValidZipEntryName(entry: ZipEntry): Boolean {
  val name = entry.name
  return !name.contains(":") && name.split('/', '\\').none { it == ".." || it == "." } && name.none { it < ' ' }
}

/** Helper function to validate the path inside a zipfile does not leave the output directory. */
fun isValidZipEntryPath(filePath: File, outputDir: File): Boolean {
  return filePath.canonicalPath.startsWith(outputDir.canonicalPath + File.separator)
}

/** Creates a new zip entry with time set to zero. */
fun zipEntry(name: String): ZipEntry = ZipEntry(name).apply { time = -1L }
