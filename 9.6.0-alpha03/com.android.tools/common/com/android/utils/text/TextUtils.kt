/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.utils.text

import kotlin.text.Typography

/**
 * Generates a comma-separated list consisting of passed collection of [items]. Function is parameterized by [lastSeparator] so enumerations
 * like "A, B or C" and "A, B and C" can be generated.
 *
 * Specify [oxfordComma] as `true` if you want a comma after the penultimate item.
 */
@JvmOverloads
fun Collection<Any>.toCommaSeparatedList(lastSeparator: String, oxfordComma: Boolean = false): String =
  when (size) {
    0 -> ""
    1 -> single().toString()
    else ->
      chunked(size - 1).let {
        val postfix = if (oxfordComma) "," else ""
        "${it[0].joinToString(", ")}$postfix $lastSeparator ${it[1].single()}"
      }
  }

/**
 * Returns a shortened version of this [String] by replacing characters in the middle with an ellipsis if it is longer than [maxSize].
 * Otherwise, returns `this`.
 */
fun String.ellipsize(maxSize: Int): String =
  when {
    maxSize < 1 -> ""
    length <= maxSize -> this
    else -> "${take(maxSize / 2)}${Typography.ellipsis}${takeLast((maxSize - 1) / 2)}"
  }

/**
 * Returns a shortened version of this [String] by replacing characters at the end with an ellipsis if it is longer than [maxSize].
 * Otherwise, returns `this`.
 *
 * To use a character other than ellipsis, provide a [lastChar] argument.
 */
fun String.truncate(maxSize: Int, lastChar: Char = Typography.ellipsis): String =
  when {
    maxSize < 1 -> ""
    length <= maxSize -> this
    else -> take(maxSize - 1) + lastChar
  }

fun String.dropPrefix(prefix: String) = if (startsWith(prefix)) drop(prefix.length) else this

/**
 * Computes the edit distance (number of insertions, deletions or substitutions to edit one string into the other) between two strings. In
 * particular, this will compute the Levenshtein distance.
 *
 * See http://en.wikipedia.org/wiki/Levenshtein_distance for details.
 *
 * @param s the first string to compare
 * @param t the second string to compare
 * @param max the maximum edit distance that we care about; if for example the string length delta is greater than this we don't bother
 *   computing the exact edit distance since the caller has indicated they're not interested in the result
 * @return the edit distance between the two strings, or some other value greater than that if the edit distance is at least as big as the
 *   `max` parameter
 */
fun editDistance(s: String, t: String, max: Int = Integer.MAX_VALUE): Int {
  if (s == t) {
    return 0
  }

  if (Math.abs(s.length - t.length) > max) {
    // The string lengths differ more than the allowed edit distance;
    // no point in even attempting to compute the edit distance (requires
    // O(n*m) storage and O(n*m) speed, where n and m are the string lengths)
    return Integer.MAX_VALUE
  }

  val m = s.length
  val n = t.length
  val d = Array(m + 1) { IntArray(n + 1) }
  for (i in 0..m) {
    d[i][0] = i
  }
  for (j in 0..n) {
    d[0][j] = j
  }
  for (j in 1..n) {
    for (i in 1..m) {
      if (s[i - 1] == t[j - 1]) {
        d[i][j] = d[i - 1][j - 1]
      } else {
        val deletion = d[i - 1][j] + 1
        val insertion = d[i][j - 1] + 1
        val substitution = d[i - 1][j - 1] + 1
        d[i][j] = Math.min(deletion, Math.min(insertion, substitution))
      }
    }
  }

  return d[m][n]
}
