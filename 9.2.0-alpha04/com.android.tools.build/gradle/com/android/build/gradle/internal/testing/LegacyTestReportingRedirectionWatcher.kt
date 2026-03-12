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

package com.android.build.gradle.internal.testing

import java.io.File
import java.io.PrintStream
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A background thread that watches a streaming results file and redirects matching events to stdout.
 *
 * This is a temporary legacy workaround for Android Studio to consume test results in real-time. In the future, Android Studio will be
 * updated to consume test results directly from the Gradle Tooling API, which will eliminate the need for this redirection.
 */
class LegacyTestReportingRedirectionWatcher(private val streamingFile: File, private val printStream: PrintStream = System.out) :
  AutoCloseable {

  private val shouldStopWatcher = AtomicBoolean(false)
  private var watcherThread: Thread? = null

  fun start() {
    watcherThread =
      Thread {
          // WatchService allows the OS to notify us when a file is modified, which is
          // more efficient than a manual sleep-based polling loop.
          val watchService = FileSystems.getDefault().newWatchService()
          val streamingPath = streamingFile.toPath()

          // WatchService monitors directories, so we register the parent directory
          // to listen for modifications to any file within it (including our streaming file).
          val parentDir = streamingPath.parent
          parentDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)

          try {
            streamingFile.bufferedReader().use { reader ->
              while (!shouldStopWatcher.get()) {
                val line = reader.readLine()
                if (line != null) {
                  // We only redirect lines that are explicitly tagged as UTP test result events.
                  if (
                    line.startsWith("<UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>") && line.endsWith("</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>")
                  ) {
                    printStream.println(line)
                  }
                } else {
                  // reader.readLine() returns null immediately when reaching the current end of a file.
                  // We use watchService.poll() to block this thread until the OS confirms the file
                  // was modified by the test engine process.
                  //
                  // We timeout every 100ms to check the 'shouldStopWatcher' flag. This prevents a
                  // worst-case scenario where the test engine finishes without a final write
                  // (which would leave this thread waiting indefinitely for an OS event). It also
                  // ensures the thread terminates with at most 100ms of latency after the test.
                  val key: WatchKey? = watchService.poll(100, TimeUnit.MILLISECONDS)

                  // After receiving an event, we must clear it and reset the key to continue
                  // receiving future notifications.
                  key?.pollEvents()
                  key?.reset()
                }
              }
              // Final drain to ensure all lines are processed after the test engine process finishes.
              reader.forEachLine { line ->
                if (line.startsWith("<UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>") && line.endsWith("</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>")) {
                  printStream.println(line)
                }
              }
            }
          } finally {
            // Always close the service to release OS resources.
            watchService.close()
          }
        }
        .apply { start() }
  }

  override fun close() {
    shouldStopWatcher.set(true)
    // Wait for the watcher thread to finish its final drain and terminate. We use a
    // 5-second timeout as a safety measure to prevent the Gradle task from hanging
    // indefinitely if the watcher thread encounters an unexpected issue.
    watcherThread?.join(5000)
  }
}
