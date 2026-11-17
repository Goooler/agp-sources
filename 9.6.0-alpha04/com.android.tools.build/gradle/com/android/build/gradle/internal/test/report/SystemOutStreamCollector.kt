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
internal fun appendCapped(buffer: StringBuilder, text: String, maxChars: Int): Boolean {
  val remaining = maxChars - buffer.length
  if (remaining <= 0) return text.isNotEmpty()
  if (text.isEmpty()) return false
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
  private var pendingHighSurrogate: Char? = null

  var truncated: Boolean = false
    private set

  val attachments = ArrayList<String>()

  fun append(chunk: String) {
    if (chunk.isEmpty()) return
    for (i in chunk.indices) {
      val ch = chunk[i]
      if (pendingCr) {
        pendingCr = false
        if (ch == '\n') {
          finishLine("\r\n")
          continue
        } else {
          finishLine("\r")
        }
      }
      when (ch) {
        '\r' -> pendingCr = true
        '\n' -> finishLine("\n")
        else -> appendLineChar(ch)
      }
    }
  }

  fun finish(): String? {
    if (pendingCr) {
      pendingCr = false
      finishLine("\r")
    } else if (lineOverflowed || lineBuffer.isNotEmpty() || pendingHighSurrogate != null) {
      finishLine("")
    }
    return if (maxChars > 0) finalizeStreamBuffer(output) else null
  }

  private fun appendLineChar(ch: Char) {
    if (!lineOverflowed) {
      lineBuffer.append(ch)
      if (lineBuffer.length >= MAX_MARKER_LINE_CHARS) {
        if (ch.isHighSurrogate()) {
          flushBufferToOutput(lineBuffer.substring(0, lineBuffer.length - 1))
          pendingHighSurrogate = ch
        } else {
          flushBufferToOutput(lineBuffer.toString())
        }
        lineBuffer.setLength(0)
        lineOverflowed = true
      }
      return
    }

    val prevHigh = pendingHighSurrogate
    if (prevHigh != null) {
      pendingHighSurrogate = null
      if (ch.isLowSurrogate()) {
        flushBufferToOutput(charArrayOf(prevHigh, ch).concatToString())
      } else {
        flushBufferToOutput(prevHigh.toString())
        if (ch.isHighSurrogate()) {
          pendingHighSurrogate = ch
        } else {
          flushBufferToOutput(ch.toString())
        }
      }
    } else if (ch.isHighSurrogate()) {
      pendingHighSurrogate = ch
    } else {
      flushBufferToOutput(ch.toString())
    }
  }

  private fun finishLine(lineEnding: String) {
    val trailingHigh = pendingHighSurrogate
    if (trailingHigh != null) {
      pendingHighSurrogate = null
      flushBufferToOutput(trailingHigh.toString())
    }
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
      flushBufferToOutput(line + lineEnding)
    }
  }

  private fun flushBufferToOutput(text: String) {
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
