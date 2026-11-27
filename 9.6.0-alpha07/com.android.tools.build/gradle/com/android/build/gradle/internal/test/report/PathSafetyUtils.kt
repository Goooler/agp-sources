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

package com.android.build.gradle.internal.test.report

import com.android.utils.ILogger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.Locale

/** Filename sanitization and path-containment helpers for test report attachments and artifacts. */
internal object PathSafetyUtils {

  /** Sanitizes [rawName] to `[A-Za-z0-9._-]`, returning [fallback] when blank, `.`, or `..`. */
  fun sanitizeFileName(rawName: String?, fallback: String): String {
    if (rawName.isNullOrBlank()) return fallback
    val baseName = rawName.substringAfterLast('/').substringAfterLast('\\')
    val cleaned =
      buildString(baseName.length) {
        for (ch in baseName) {
          if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '.' || ch == '_' || ch == '-') {
            append(ch)
          } else {
            append('_')
          }
        }
      }
    return if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") fallback else cleaned
  }

  /**
   * Allocates a sanitized filename whose lowercase key is not yet in [usedNames], appending `_2`, `_3`, ... before the extension on
   * collision so generated artifacts remain distinct on case-insensitive filesystems.
   */
  fun allocateUniqueFileName(
    rawName: String,
    fallback: String,
    usedNames: MutableSet<String>,
  ): String {
    val sanitized = sanitizeFileName(rawName, fallback)
    if (usedNames.add(sanitized.lowercase(Locale.ROOT))) return sanitized

    val dotIndex = sanitized.lastIndexOf('.')
    val stem = if (dotIndex > 0) sanitized.substring(0, dotIndex) else sanitized
    val ext = if (dotIndex > 0) sanitized.substring(dotIndex) else ""
    var suffix = 2
    while (true) {
      val candidate = "${stem}_$suffix$ext"
      if (usedNames.add(candidate.lowercase(Locale.ROOT))) return candidate
      suffix++
    }
  }

  /**
   * Resolves [relativePath] strictly within `<baseDir>/attachments/`, rejecting absolute paths, directory escapes, symbolic links, and
   * missing or non-regular files.
   */
  fun resolveAttachmentFile(baseDir: File, relativePath: String, logger: ILogger): File? =
    resolveContainedRegularFile(
      rawPathString = relativePath,
      allowedRoot = File(baseDir, ATTACHMENTS_DIR_NAME),
      relativeBaseDir = baseDir,
      allowAbsolutePaths = false,
      warn = logger::warning,
    )

  /**
   * Resolves a JUnit `[[ATTACHMENT|...]]` marker path against [allowedAttachmentRoot] (using [xmlParentDir] for relative markers),
   * rejecting directory traversal, symbolic links, and missing or non-regular files.
   */
  fun resolveAllowedAttachmentFile(
    rawMarkerPath: String,
    xmlParentDir: File?,
    allowedAttachmentRoot: File?,
    warn: (String) -> Unit,
  ): File? =
    resolveContainedRegularFile(
      rawPathString = rawMarkerPath,
      allowedRoot = allowedAttachmentRoot,
      relativeBaseDir = xmlParentDir,
      allowAbsolutePaths = true,
      warn = warn,
    )

  private fun resolveContainedRegularFile(
    rawPathString: String,
    allowedRoot: File?,
    relativeBaseDir: File?,
    allowAbsolutePaths: Boolean,
    warn: (String) -> Unit,
  ): File? {
    if (rawPathString.isBlank() || rawPathString.indexOf('\u0000') >= 0) {
      warn("Rejected invalid empty or null-containing attachment path '$rawPathString'.")
      return null
    }
    val parsedPath =
      try {
        Path.of(rawPathString)
      } catch (_: InvalidPathException) {
        warn("Rejected malformed attachment path '$rawPathString'.")
        return null
      }
    if (!allowAbsolutePaths && parsedPath.isAbsolute) {
      warn("Rejected absolute attachment path '$rawPathString'; paths must be relative to the results directory.")
      return null
    }
    if (allowedRoot == null || !allowedRoot.isDirectory) {
      warn("Ignoring test attachment '$rawPathString' because no attachment directory is present.")
      return null
    }

    val absRoot = allowedRoot.toPath().toAbsolutePath().normalize()
    val realRoot =
      try {
        allowedRoot.toPath().toRealPath()
      } catch (_: IOException) {
        warn("Ignoring test attachment '$rawPathString': unable to resolve attachment root.")
        return null
      }

    val candidatePath =
      if (parsedPath.isAbsolute) {
        parsedPath.normalize()
      } else {
        val baseDir = relativeBaseDir ?: return null
        val canonicalOrAbsBase = runCatching { baseDir.toPath().toRealPath() }.getOrNull() ?: baseDir.toPath().toAbsolutePath().normalize()
        canonicalOrAbsBase.resolve(parsedPath).normalize()
      }

    val insideRealRoot = candidatePath.startsWith(realRoot) && candidatePath != realRoot
    val insideAbsRoot = candidatePath.startsWith(absRoot) && candidatePath != absRoot
    if (!insideRealRoot && !insideAbsRoot) {
      warn("Rejecting test attachment '$rawPathString' outside attachment directory.")
      return null
    }

    val stopRoot = if (insideRealRoot) realRoot else absRoot
    if (containsSymlinkSegment(candidatePath, stopRoot)) {
      warn("Rejecting symbolic link test attachment '$rawPathString'.")
      return null
    }
    if (!Files.exists(candidatePath)) {
      warn("Referenced test attachment '$rawPathString' does not exist.")
      return null
    }

    val realCandidate =
      try {
        candidatePath.toRealPath()
      } catch (e: IOException) {
        warn("Failed to resolve test attachment '$rawPathString': ${e.message}")
        return null
      }
    if (!realCandidate.startsWith(realRoot) || realCandidate == realRoot || !Files.isRegularFile(realCandidate)) {
      warn("Rejecting test attachment '$rawPathString' outside attachment directory.")
      return null
    }
    return realCandidate.toFile()
  }

  private fun containsSymlinkSegment(path: Path, stopAt: Path): Boolean {
    var cursor: Path? = path
    while (cursor != null && cursor != stopAt) {
      if (Files.isSymbolicLink(cursor)) {
        return true
      }
      cursor = cursor.parent
    }
    return false
  }
}
