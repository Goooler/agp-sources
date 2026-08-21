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

package com.android.tools.coverage.reporter

/**
 * Resolves bytecode line numbers to original source lines using SMAP (JSR-45) data. This is essential for correctly attributing coverage in
 * Kotlin inline functions.
 */
class SmapResolver(smapString: String) {

  private val lineMappings = mutableListOf<LineMapping>()
  private val fileMappings = mutableMapOf<Int, String>()

  data class LineMapping(val inputStart: Int, val fileId: Int, val repeat: Int, val outputStart: Int, val outputEnd: Int)

  init {
    parse(smapString)
  }

  /**
   * Maps a bytecode line number to the original source line and file name.
   *
   * @param outputLine The line number in the bytecode (DEX).
   * @param defaultSourceFile The source file to return if no SMAP mapping is found.
   * @return A pair containing the (original line number, source file name).
   */
  fun resolve(outputLine: Int, defaultSourceFile: String): Pair<Int, String> {
    val mapping = lineMappings.find { outputLine >= it.outputStart && outputLine <= it.outputEnd }
    if (mapping == null) {
      return Pair(outputLine, defaultSourceFile)
    }

    val offset = outputLine - mapping.outputStart
    // If the input range is only 1 line but the output range is multiple (e.g. 11:101,2),
    // all output lines map to that same input line.
    val sourceLine = if (mapping.repeat == 1) mapping.inputStart else mapping.inputStart + offset
    val sourceFile = fileMappings[mapping.fileId] ?: defaultSourceFile
    return Pair(sourceLine, sourceFile)
  }

  private fun parse(smap: String) {
    if (smap.isBlank()) return

    val lines = smap.lines().map { it.trim() }
    var currentStratum = ""
    var currentSubSection = ""

    for (line in lines) {
      when {
        line.startsWith("*S") -> {
          currentStratum = line.substring(2).trim()
          currentSubSection = ""
        }
        line == "*F" -> currentSubSection = "*F"
        line == "*L" -> currentSubSection = "*L"
        line == "*E" -> {
          currentStratum = ""
          currentSubSection = ""
        }
        else -> {
          // Only parse the main Kotlin section, similar to the Python POC.
          // KotlinDebug sections map virtual lines back to call-sites, which
          // we usually don't want for basic line coverage.
          if (currentStratum != "Kotlin") {
            continue
          }

          when (currentSubSection) {
            "*F" -> parseFileMapping(line)
            "*L" -> parseLineMapping(line)
          }
        }
      }
    }
  }

  private fun parseFileMapping(line: String) {
    // Format: + <fileId> <fileName>
    //         <path>
    if (line.startsWith("+ ")) {
      val parts = line.substring(2).split(" ", limit = 2)
      if (parts.size == 2) {
        val fileId = parts[0].toIntOrNull()
        if (fileId != null) {
          fileMappings[fileId] = parts[1]
        }
      }
    }
  }

  private fun parseLineMapping(line: String) {
    // Format: InputStartLine[#FileID][,RepeatCount]:OutputStartLine[,OutputRepeatCount]
    // Note: For simplicity and following the POC, we assume OutputRepeatCount is the same as RepeatCount
    // if not explicitly provided, which is the standard Kotlin behavior.

    val regex = Regex("""^(\d+)(?:#(\d+))?(?:,(\d+))?:(\d+)(?:,(\d+))?$""")
    val match = regex.find(line) ?: return

    val inputStart = match.groupValues[1].toInt()
    val fileId = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 1
    val repeat = match.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 1
    val outputStart = match.groupValues[4].toInt()
    val outputRepeat = match.groupValues[5].takeIf { it.isNotEmpty() }?.toInt() ?: repeat

    lineMappings.add(
      LineMapping(
        inputStart = inputStart,
        fileId = fileId,
        repeat = repeat,
        outputStart = outputStart,
        outputEnd = outputStart + outputRepeat - 1,
      )
    )
  }
}
