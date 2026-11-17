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

  /** Allocates a sanitized filename not yet present in [usedNames], appending `_2`, `_3`, ... before the extension on collision. */
  fun allocateUniqueFileName(
    rawName: String,
    fallback: String,
    usedNames: MutableSet<String>,
  ): String {
    val sanitized = sanitizeFileName(rawName, fallback)
    if (usedNames.add(sanitized)) return sanitized

    val dotIndex = sanitized.lastIndexOf('.')
    val stem = if (dotIndex > 0) sanitized.substring(0, dotIndex) else sanitized
    val ext = if (dotIndex > 0) sanitized.substring(dotIndex) else ""
    var suffix = 2
    while (true) {
      val candidate = "${stem}_$suffix$ext"
      if (usedNames.add(candidate)) return candidate
      suffix++
    }
  }

  /** Resolves [relativePath] strictly within [baseDir], rejecting absolute paths, directory escapes, and symbolic links. */
  fun resolveContainedFile(baseDir: File, relativePath: String, logger: ILogger): File? {
    if (relativePath.isBlank() || relativePath.indexOf('\u0000') >= 0) {
      logger.warning("Rejected invalid empty or null-containing report path.")
      return null
    }

    val relPath: Path =
      try {
        Path.of(relativePath)
      } catch (_: InvalidPathException) {
        logger.warning("Rejected malformed report path '$relativePath'.")
        return null
      }

    if (relPath.isAbsolute) {
      logger.warning("Rejected absolute report path '$relativePath'; paths must be relative to the results directory.")
      return null
    }

    val normalizedBase = baseDir.toPath().toAbsolutePath().normalize()
    val resolved = normalizedBase.resolve(relPath).normalize()
    if (!resolved.startsWith(normalizedBase) || resolved == normalizedBase) {
      logger.warning("Rejected report path '$relativePath' escaping results directory '${baseDir.path}'.")
      return null
    }

    if (containsSymlinkSegment(resolved, normalizedBase)) {
      logger.warning("Rejected symbolic link report path '$relativePath'.")
      return null
    }

    if (Files.exists(resolved)) {
      try {
        val realBase = baseDir.toPath().toRealPath()
        val realResolved = resolved.toRealPath()
        if (!realResolved.startsWith(realBase) || realResolved == realBase) {
          logger.warning("Rejected report path '$relativePath' resolving outside results directory '${baseDir.path}'.")
          return null
        }
      } catch (_: IOException) {
        logger.warning("Unable to verify canonical path for '$relativePath'.")
        return null
      }
    }

    return resolved.toFile()
  }

  /**
   * Resolves [relativePath] within `<baseDir>/attachments/`, rejecting paths outside [ATTACHMENTS_DIR_NAME] or involving symbolic links.
   */
  fun resolveAttachmentFile(baseDir: File, relativePath: String, logger: ILogger): File? {
    val safeFile = resolveContainedFile(baseDir, relativePath, logger) ?: return null
    val relNorm = Path.of(relativePath).normalize()
    if (relNorm.nameCount < 2 || relNorm.getName(0).toString() != ATTACHMENTS_DIR_NAME) {
      logger.warning("Rejected attachment path '$relativePath' outside '$ATTACHMENTS_DIR_NAME/'.")
      return null
    }
    return safeFile
  }

  /**
   * Resolves a JUnit `[[ATTACHMENT|...]]` marker path against [allowedAttachmentRoot] (using [xmlParentDir] for relative markers),
   * rejecting directory traversal, symbolic links, and non-regular files.
   */
  fun resolveAllowedAttachmentFile(
    rawMarkerPath: String,
    xmlParentDir: File?,
    allowedAttachmentRoot: File?,
    warn: (String) -> Unit,
  ): File? {
    if (rawMarkerPath.isEmpty() || rawMarkerPath.contains('\u0000')) {
      warn("Ignoring invalid test attachment marker '$rawMarkerPath'.")
      return null
    }
    if (allowedAttachmentRoot == null || !allowedAttachmentRoot.isDirectory) {
      warn("Ignoring test attachment '$rawMarkerPath' because no attachment directory is configured.")
      return null
    }
    val absRoot = allowedAttachmentRoot.toPath().toAbsolutePath().normalize()
    val realRoot = runCatching {
      allowedAttachmentRoot.toPath().toRealPath()
    }
      .getOrElse {
        warn("Ignoring test attachment '$rawMarkerPath': unable to resolve attachment root.")
        return null
      }
    val rawPath = runCatching {
      Path.of(rawMarkerPath)
    }
      .getOrElse {
        warn("Ignoring malformed test attachment path '$rawMarkerPath'.")
        return null
      }
    val candidatePath =
      if (rawPath.isAbsolute) {
        rawPath.normalize()
      } else {
        val baseDir = xmlParentDir ?: return null
        baseDir.toPath().toAbsolutePath().resolve(rawPath).normalize()
      }
    if (!candidatePath.startsWith(absRoot) && !candidatePath.startsWith(realRoot)) {
      warn("Rejecting test attachment '$rawMarkerPath' outside attachment directory.")
      return null
    }
    if (containsSymlinkSegment(candidatePath, absRoot, realRoot)) {
      warn("Rejecting symbolic link test attachment '$rawMarkerPath'.")
      return null
    }
    if (!Files.exists(candidatePath)) {
      warn("Referenced test attachment '$rawMarkerPath' does not exist.")
      return null
    }
    val realCandidate = runCatching {
      candidatePath.toRealPath()
    }
      .getOrElse {
        warn("Failed to resolve test attachment '$rawMarkerPath': ${it.message}")
        return null
      }
    if (!realCandidate.startsWith(realRoot) || realCandidate == realRoot || !Files.isRegularFile(realCandidate)) {
      warn("Rejecting test attachment '$rawMarkerPath' outside attachment directory.")
      return null
    }
    return realCandidate.toFile()
  }

  private fun containsSymlinkSegment(path: Path, stopAtA: Path, stopAtB: Path? = null): Boolean {
    var cursor: Path? = path
    while (cursor != null && cursor != stopAtA && cursor != stopAtB) {
      if (Files.isSymbolicLink(cursor)) {
        return true
      }
      cursor = cursor.parent
    }
    return false
  }
}
