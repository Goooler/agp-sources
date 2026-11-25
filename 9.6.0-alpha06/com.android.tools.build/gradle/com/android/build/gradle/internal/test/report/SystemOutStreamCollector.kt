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

/**
 * Appends [text] to [buffer] without exceeding [maxChars] UTF-16 code units or splitting a surrogate pair.
 *
 * Returns `true` when [text] exceeded [maxChars] and characters were dropped.
 */
internal fun appendCapped(buffer: StringBuilder, text: CharSequence, maxChars: Int): Boolean {
  if (text.isEmpty()) return false
  if (buffer.isNotEmpty() && buffer[buffer.length - 1].isHighSurrogate() && !text[0].isLowSurrogate()) {
    buffer.setLength(buffer.length - 1)
  }
  val remaining = maxChars - buffer.length
  if (remaining <= 0) return true
  if (text.length < remaining || (text.length == remaining && !text[remaining - 1].isHighSurrogate())) {
    buffer.append(text)
    return false
  }
  val safeEnd = if (text[remaining - 1].isHighSurrogate()) remaining - 1 else remaining
  if (safeEnd > 0) {
    buffer.append(text, 0, safeEnd)
  }
  return true
}

/** Finalizes [buffer] into a non-blank string, trimming any trailing unpaired high surrogate. */
internal fun finalizeStreamBuffer(buffer: StringBuilder?): String? {
  if (buffer == null || buffer.isEmpty()) return null
  if (buffer[buffer.length - 1].isHighSurrogate()) {
    buffer.setLength(buffer.length - 1)
  }
  return buffer.toString().takeIf { it.isNotBlank() }
}

/**
 * Collects `<system-out>` text up to [maxChars] UTF-16 code units while extracting and stripping `[[ATTACHMENT|...]]` marker lines
 * throughout the entire stream.
 */
internal class SystemOutStreamCollector(private val maxChars: Int = MAX_STREAM_CHARS) {
  private val output = StringBuilder()
  private val lineBuffer = StringBuilder()
  private var lineOverflowed = false
  private var pendingCr = false

  var truncated: Boolean = false
    private set

  val attachments = ArrayList<String>()

  fun append(chunk: String) {
    if (chunk.isEmpty()) return
    var index = 0
    if (pendingCr) {
      pendingCr = false
      if (chunk[0] == '\n') {
        finishLine("\r\n")
        index = 1
      } else {
        finishLine("\r")
      }
    }
    while (index < chunk.length) {
      var delimIndex = index
      while (delimIndex < chunk.length && chunk[delimIndex] != '\r' && chunk[delimIndex] != '\n') {
        delimIndex++
      }
      if (delimIndex > index) {
        appendLineSlice(chunk, index, delimIndex)
        index = delimIndex
      }
      if (index < chunk.length) {
        val ch = chunk[index++]
        if (ch == '\r') {
          if (index < chunk.length) {
            if (chunk[index] == '\n') {
              index++
              finishLine("\r\n")
            } else {
              finishLine("\r")
            }
          } else {
            pendingCr = true
          }
        } else {
          finishLine("\n")
        }
      }
    }
  }

  fun finish(): String? {
    if (pendingCr) {
      pendingCr = false
      finishLine("\r")
    } else if (lineOverflowed || lineBuffer.isNotEmpty()) {
      finishLine("")
    }
    return if (maxChars > 0) finalizeStreamBuffer(output) else null
  }

  private fun appendLineSlice(chunk: String, start: Int, end: Int) {
    val segLen = end - start
    if (lineOverflowed) {
      flushBufferToOutput(chunk.subSequence(start, end))
      return
    }
    if (lineBuffer.length + segLen < MAX_MARKER_LINE_CHARS) {
      lineBuffer.append(chunk, start, end)
      return
    }
    if (lineBuffer.isNotEmpty()) {
      flushBufferToOutput(lineBuffer)
      lineBuffer.setLength(0)
    }
    flushBufferToOutput(chunk.subSequence(start, end))
    lineOverflowed = true
  }

  private fun finishLine(lineEnding: String) {
    if (lineOverflowed) {
      if (lineEnding.isNotEmpty()) {
        flushBufferToOutput(lineEnding)
      }
      lineOverflowed = false
      lineBuffer.setLength(0)
      return
    }

    val line = lineBuffer.toString()
    lineBuffer.setLength(0)
    val trimmed = line.trim()
    if (trimmed.startsWith(ATTACHMENT_MARKER_PREFIX) && trimmed.endsWith(ATTACHMENT_MARKER_SUFFIX)) {
      val rawPath =
        trimmed
          .substring(
            ATTACHMENT_MARKER_PREFIX.length,
            trimmed.length - ATTACHMENT_MARKER_SUFFIX.length,
          )
          .trim()
      if (rawPath.isNotEmpty()) {
        attachments.add(rawPath)
      }
      return
    }

    if (line.isNotEmpty() || lineEnding.isNotEmpty()) {
      flushBufferToOutput(line)
      flushBufferToOutput(lineEnding)
    }
  }

  private fun flushBufferToOutput(text: CharSequence) {
    if (text.isEmpty()) return
    if (maxChars <= 0) {
      truncated = true
      return
    }
    if (truncated) return
    if (appendCapped(output, text, maxChars)) {
      truncated = true
    }
  }

  companion object {
    private const val MAX_MARKER_LINE_CHARS = 8192
  }
}
