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
package com.android.repository.impl.manager

import com.android.SdkConstants
import com.android.annotations.concurrency.Slow
import com.android.repository.api.ProgressIndicator
import com.sun.nio.file.ExtendedWatchEventModifier
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService

/**
 * Monitors the local SDK repository directory tree on disk using a [WatchService].
 *
 * Tracks the SDK root directory and intermediate package container directories (up to [MAX_WATCH_DEPTH]), stopping recursion at package
 * boundaries (directories containing `package.xml`).
 */
class LocalRepoFileWatcher private constructor(private val root: Path, private val watchService: WatchService) : AutoCloseable {

  private var isFileTreeActive: Boolean = false

  /**
   * Drains queued watch events from the [WatchService].
   *
   * @param progress A [ProgressIndicator] for logging file watch events and errors.
   * @param blocking If `true`, blocks on the initial poll using [WatchService.take] until a watch event is available.
   * @return `true` if file system modifications were observed on disk, `false` otherwise.
   */
  @Slow
  fun consumeWatchEvents(progress: ProgressIndicator, blocking: Boolean = false): Boolean {
    var hasChanges = false
    var first = true

    while (true) {
      val key =
        when {
          blocking && first -> watchService.take()
          else -> watchService.poll() ?: break
        }
      first = false

      val events = key.pollEvents()
      try {
        for (event in events) {
          val kind = event.kind()
          if (kind == StandardWatchEventKinds.OVERFLOW) {
            progress.logInfo("WatchService overflow event in $root; re-registering tree.")
            hasChanges = true
            registerTree(root, progress = progress)
            continue
          }

          val watchable = key.watchable() as? Path ?: continue
          val contextPath = event.context() as? Path ?: continue

          val dir = root.resolve(watchable)
          val child = dir.resolve(contextPath)
          val relPath = if (child.startsWith(root)) root.relativize(child) else contextPath

          if (relPath.nameCount == 0 || relPath.nameCount > MAX_WATCH_DEPTH) {
            continue
          }

          // Ignore top-level excluded directories (e.g. fonts, skins, temp)
          val topDir = relPath.getName(0).toString()
          if (EXCLUDED_DIR_NAMES.contains(topDir)) {
            continue
          }

          // Ignore hidden files and cache artifacts (e.g. .knownPackages, .DS_Store, .git)
          if (relPath.any { it.toString().startsWith(".") }) {
            continue
          }

          val fileName = child.fileName?.toString() ?: ""
          val isPackageXml = fileName == LocalRepoLoaderImpl.PACKAGE_XML_FN

          // Ignore events inside existing package boundaries (ancestor directory containing package.xml)
          val checkStart = if (isPackageXml) child.parent?.parent else child.parent
          if (isInsidePackage(checkStart)) {
            continue
          }

          if (isPackageXml) {
            progress.logVerbose("Observed SDK repository package manifest change event ($kind) for $relPath in $dir")
            hasChanges = true
          } else if (Files.isDirectory(child)) {
            progress.logVerbose("Observed SDK repository directory change event ($kind) for $relPath in $dir")
            if (kind == StandardWatchEventKinds.ENTRY_CREATE && !isFileTreeActive) {
              if (registerTree(root, child, depth = relPath.nameCount, progress = progress)) {
                hasChanges = true
              }
            }
            if (Files.exists(child.resolve(LocalRepoLoaderImpl.PACKAGE_XML_FN))) {
              hasChanges = true
            }
          } else if (kind == StandardWatchEventKinds.ENTRY_DELETE && !fileName.contains(".")) {
            progress.logVerbose("Observed SDK repository directory deletion event ($kind) for $relPath in $dir")
            hasChanges = true
          }
        }
      } finally {
        key.reset()
      }
    }

    return hasChanges
  }

  private fun isInsidePackage(startDir: Path?): Boolean {
    var curr = startDir
    while (curr != null && curr.startsWith(root) && curr != root) {
      if (Files.exists(curr.resolve(LocalRepoLoaderImpl.PACKAGE_XML_FN))) {
        return true
      }
      curr = curr.parent
    }
    return false
  }

  private fun registerTree(root: Path, target: Path = root, depth: Int = 0, progress: ProgressIndicator): Boolean {
    if (!Files.isDirectory(target)) return false
    if (depth > MAX_WATCH_DEPTH) return false

    val name = target.fileName?.toString() ?: ""
    if (depth > 0 && name.startsWith(".")) return false
    if (depth == 1 && EXCLUDED_DIR_NAMES.contains(name)) return false

    val kinds = arrayOf(StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY)

    // Try using recursive file monitoring on Windows
    if (depth == 0 && IS_WINDOWS) {
      try {
        target.register(watchService, kinds, ExtendedWatchEventModifier.FILE_TREE)
        progress.logVerbose("Registered Windows native FILE_TREE watch on $target")
        isFileTreeActive = true
        return true
      } catch (t: Throwable) {
        progress.logVerbose("Native FILE_TREE watching failed for $target, falling back to manual recursion: ${t.message}")
      }
    }

    try {
      target.register(watchService, *kinds)
    } catch (e: IOException) {
      progress.logWarning("Failed to register WatchService for $target", e)
    }

    var isPackage = Files.exists(target.resolve(LocalRepoLoaderImpl.PACKAGE_XML_FN))
    if (isPackage) return true

    try {
      Files.newDirectoryStream(target).use { stream ->
        for (child in stream) {
          if (Files.isDirectory(child)) {
            if (registerTree(root, child, depth + 1, progress)) {
              isPackage = true
            }
          }
        }
      }
    } catch (e: IOException) {
      progress.logWarning("Failed to list directory stream for $target", e)
    }
    return isPackage
  }

  override fun close() {
    try {
      watchService.close()
    } catch (_: IOException) {
      // Ignore
    }
  }

  companion object {
    /** Creates a [LocalRepoFileWatcher] for the given [root] path, or returns `null` if the [WatchService] cannot be created. */
    @JvmStatic
    fun create(root: Path, progress: ProgressIndicator): LocalRepoFileWatcher {
      return LocalRepoFileWatcher(root, root.fileSystem.newWatchService()).also { it.registerTree(root, progress = progress) }
    }

    /**
     * Maximum depth to recurse into container directories when registering watch keys. Deepest standard SDK package is system-images (e.g.
     * system-images/android-34/google_apis/x86_64), which is at depth 4 from SDK root.
     */
    private const val MAX_WATCH_DEPTH = 5

    /** Top-level directories where resources or non-package caches are stored; these should not be watched. */
    private val EXCLUDED_DIR_NAMES = setOf("environments", "fonts", "icons", "skins", "temp")

    private val IS_WINDOWS: Boolean = SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS
  }
}
