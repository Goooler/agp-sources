/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.resources

/*
 * Pseudolocalization support for Android string and plural resources (`en-rXA` and `ar-rXB`).
 *
 * For an overview of Android pseudolocales and the localization bugs they expose, see:
 * https://developer.android.com/guide/topics/resources/pseudolocales
 *
 * This implementation is shared between:
 * - Build-time resource compilation (`com.android.aaptcompiler.PseudolocaleGenerator`), which bakes
 *   pseudolocalized strings into an APK's `resources.arsc` table when `isPseudoLocalesEnabled` is
 *   enabled in Gradle, and
 * - Dynamic resource configuration (`com.android.ide.common.resources.ResourceRepositoryUtil`),
 *   which synthesizes pseudolocalized strings on the fly for Android Studio design tools (Compose
 *   Preview, Layout Editor, etc.) where resources are resolved directly from raw XML source
 *   repositories rather than compiled APKs.
 *
 * Ported from AAPT2's `frameworks/base/tools/aapt2/compile/Pseudolocalizer.cpp`.
 */

// Guillemet markers (» and «) used in ACCENT (en-rXA) mode to wrap format specifiers (e.g. »%1$s«)
// and ICU placeholders (e.g. »{USER}«) so developers can visually verify that placeholders are
// preserved as atomic units.
private const val PLACEHOLDER_OPEN = "\u00bb"
private const val PLACEHOLDER_CLOSE = "\u00ab"

// Unicode bidirectional control characters used in BIDI (ar-rXB) mode:
// - RLM (Right-to-Left Mark, U+200F): invisible strong RTL character used to anchor word boundaries
//   so adjacent words and punctuation flow right-to-left across the sentence.
// - RLO (Right-to-Left Override, U+202E): forces enclosed LTR characters (e.g. Latin letters and
//   attached punctuation) to be laid out right-to-left by the Unicode BiDi layout engine without
//   reversing the String's logical character order in memory.
// - PDF (Pop Directional Formatting, U+202C): terminates the nearest RLO override scope.
private const val RLM = "\u200f"
private const val RLO = "\u202e"
private const val PDF = "\u202c"

// Delimiters for ICU MessageFormat arguments (e.g. "{USER}" or "{COUNT, plural, one {...}}").
private const val ARG_START = '{'
private const val ARG_END = '}'

/**
 * Maps an ASCII character to its accented/diacritic lookalike for `en-rXA` ([Pseudolocalizer.Method.ACCENT]).
 *
 * This keeps English text human-readable while testing:
 * - Font glyph coverage for extended Latin/Unicode characters,
 * - Vertical line-height and ascent/descent clipping caused by tall diacritics,
 * - Hardcoded (unlocalized) UI strings, which stand out because they remain unaccented.
 *
 * Returns `null` for characters that should remain unchanged (such as digits, whitespace, or `'F'`, which has no single-codepoint accented
 * uppercase equivalent in AAPT2's table).
 */
private fun Char.pseudoLocalize(): String? =
  when (this) {
    'a' -> "\u00e5"
    'b' -> "\u0253"
    'c' -> "\u00e7"
    'd' -> "\u00f0"
    'e' -> "\u00e9"
    'f' -> "\u0192"
    'g' -> "\u011d"
    'h' -> "\u0125"
    'i' -> "\u00ee"
    'j' -> "\u0135"
    'k' -> "\u0137"
    'l' -> "\u013c"
    'm' -> "\u1e3f"
    'n' -> "\u00f1"
    'o' -> "\u00f6"
    'p' -> "\u00fe"
    'q' -> "\u0051"
    'r' -> "\u0155"
    's' -> "\u0161"
    't' -> "\u0163"
    'u' -> "\u00fb"
    'v' -> "\u0056"
    'w' -> "\u0175"
    'x' -> "\u0445"
    'y' -> "\u00fd"
    'z' -> "\u017e"
    'A' -> "\u00c5"
    'B' -> "\u03b2"
    'C' -> "\u00c7"
    'D' -> "\u00d0"
    'E' -> "\u00c9"
    /* No 'F' */
    'G' -> "\u011c"
    'H' -> "\u0124"
    'I' -> "\u00ce"
    'J' -> "\u0134"
    'K' -> "\u0136"
    'L' -> "\u013b"
    'M' -> "\u1e3e"
    'N' -> "\u00d1"
    'O' -> "\u00d6"
    'P' -> "\u00de"
    'Q' -> "\u0071"
    'R' -> "\u0154"
    'S' -> "\u0160"
    'T' -> "\u0162"
    'U' -> "\u00db"
    'V' -> "\u03bd"
    'W' -> "\u0174"
    'X' -> "\u00d7"
    'Y' -> "\u00dd"
    'Z' -> "\u017d"
    '!' -> "\u00a1"
    '?' -> "\u00bf"
    '$' -> "\u20ac"
    else -> null
  }

/**
 * Returns `true` if this character is a `printf` / [java.util.Formatter] conversion specifier that terminates a `%` format placeholder
 * (e.g. `%s`, `%1$d`, `%.2f`, `%%`, `%n`).
 *
 * Note: `'t'` (date/time prefix, as in `%tY`) is handled separately in [PseudoMethodAccent.processMaybePlaceholder] because it is followed
 * by a second suffix character.
 */
private fun Char.isPossiblePlaceholderEnd(): Boolean =
  when (this) {
    's',
    'S',
    'c',
    'C',
    'd',
    'o',
    'x',
    'X',
    'f',
    'e',
    'E',
    'g',
    'G',
    'a',
    'A',
    'b',
    'B',
    'h',
    'H',
    '%',
    'n' -> true
    else -> false
  }

/**
 * Strategy interface for a pseudolocalization method.
 *
 * A single string or ICU message may contain one top-level translatable segment or multiple nested translatable segments (e.g. inside
 * `{COUNT, plural, one {...} other {...}}`). For each translatable segment:
 * 1. [start] is called before the segment begins,
 * 2. [text] and [placeholder] are called for chunks within the segment, and
 * 3. [end] is called when the segment finishes.
 */
private abstract class PseudoMethodImpl {
  open fun start(): String = ""

  open fun end(): String = ""

  abstract fun text(originalText: String): String

  abstract fun placeholder(originalText: String): String
}

private object PseudoMethodNone : PseudoMethodImpl() {
  override fun text(originalText: String): String = originalText

  override fun placeholder(originalText: String): String = originalText
}

/**
 * Implements `ar-rXB` ([Pseudolocalizer.Method.BIDI]) pseudolocalization.
 *
 * Simulates Right-to-Left (RTL) text by wrapping each whitespace-delimited word and placeholder in `RLM + RLO + <word> + PDF + RLM`
 * (`\u200f\u202e...\u202c\u200f`). Note that the characters inside the [String] remain in logical order in memory; at render time, the
 * Unicode BiDi layout engine interprets `RLO`...`PDF` to lay out each word's characters (including attached punctuation, such as `"rule!"`
 * -> `"!elur"`) right-to-left while anchoring word boundaries as strong RTL tokens (`RLM`) so words flow right-to-left across the sentence
 * (e.g. `"pseudolocales rule!"` visually renders as `"!elur selacoloduesp"`).
 */
private object PseudoMethodBidi : PseudoMethodImpl() {
  private const val ESCAPE_CHAR = '\\'

  override fun text(originalText: String): String {
    val result = StringBuilder()
    // Tracks whether the previous character was whitespace (true initially so the first non-space
    // character triggers a word start).
    var lastSpace = true
    var escape = false

    for (currentChar in originalText) {
      // Defer emitting an unescaped backslash until we inspect the next character: if it is '\n'
      // or '\t', the two-character escape sequence acts as whitespace and must sit outside the
      // word's RLO...PDF block. If it is something else (like '\''), the backslash is part of the
      // word and must be emitted after the word-start RLM + RLO prefix.
      if (!escape && currentChar == ESCAPE_CHAR) {
        escape = true
        continue
      }

      val isSpace = (!escape && currentChar.isWhitespace()) || (escape && (currentChar == 'n' || currentChar == 't'))
      if (lastSpace && !isSpace) {
        // Entering a word: open the RTL directional override.
        result.append(RLM + RLO)
      } else if (!lastSpace && isSpace) {
        // Leaving a word: close the RTL directional override before the whitespace.
        result.append(PDF + RLM)
      }
      lastSpace = isSpace
      if (escape) {
        result.append(ESCAPE_CHAR)
        escape = false
      }
      result.append(currentChar)
    }
    if (!lastSpace) {
      // Close the RTL directional override for the final word at the end of the string.
      result.append(PDF + RLM)
    }
    return result.toString()
  }

  override fun placeholder(originalText: String): String =
    // Wrap placeholders in the same RTL directional override sequence as words.
    RLM + RLO + originalText + PDF + RLM
}

/**
 * Implements `en-rXA` ([Pseudolocalizer.Method.ACCENT]) pseudolocalization.
 *
 * Transforms strings in three ways:
 * 1. Surrounds the outer string in `[` and `]` brackets so developers can easily tell if text is clipped or truncated in the UI (if the
 *    closing `]` is missing) or if two strings were inappropriately concatenated in code (`[first][second]`).
 * 2. Replaces ASCII characters with accented lookalikes via [Char.pseudoLocalize], while leaving `%` format specifiers (wrapped in `»...«`)
 *    and HTML tags/entities untouched.
 * 3. Appends English number words (`"one two three..."`) at the end of each translatable segment ([end]) to simulate text expansion in
 *    verbose translations:
 *     - Short phrases (`<= 3` words) are expanded by **50%** (`length / 2`).
 *     - Longer phrases (`> 3` words) are expanded by **100%** (`length`).
 */
private class PseudoMethodAccent : PseudoMethodImpl() {
  // Nesting depth of translatable segments (1 for top-level text, >1 for nested ICU plural/select
  // case bodies). Outer '[' and ']' brackets are only emitted at depth == 0.
  private var depth = 0
  // Number of whitespace-delimited words in the current translatable segment.
  private var wordCount = 0
  // Number of translatable characters (excluding HTML tags and '%' format specifiers) in the
  // current segment, used to calculate expansion length in end().
  private var length = 0

  override fun start(): String {
    val prefix = if (depth == 0) "[" else ""
    wordCount = 0
    length = 0
    ++depth
    return prefix
  }

  override fun end(): String {
    val result = StringBuilder()
    if (length != 0) {
      result.append(' ')
      val expansionTargetChars = if (wordCount > 3) length else length / 2
      result.append(pseudoGenerateExpansion(expansionTargetChars))
    }
    wordCount = 0
    length = 0
    --depth
    if (depth == 0) {
      result.append("]")
    }
    return result.toString()
  }

  override fun text(originalText: String): String {
    val result = StringBuilder()
    val size = originalText.length
    var lastSpace = true
    var currentIndex = 0
    while (currentIndex < size) {
      val currentChar = originalText[currentIndex]
      when (currentChar) {
        '%' -> {
          // Format specifier (e.g. "%1$s", "%d", "%%"): do not accent characters or count toward
          // expansion length.
          currentIndex = processMaybePlaceholder(result, originalText, currentIndex)
        }
        '<',
        '&' -> {
          // Potential HTML tag (e.g. "<i>") or entity (e.g. "&amp;", "&lt;"): preserve markup
          // without accenting tag/entity names.
          currentIndex = processHtml(result, originalText, currentIndex)
        }
        else -> {
          // Regular translatable text character.
          val localized = currentChar.pseudoLocalize()
          if (localized != null) {
            result.append(localized)
          } else {
            val space = currentChar.isWhitespace()
            if (lastSpace && !space) {
              ++wordCount
            }
            lastSpace = space
            result.append(currentChar)
          }
          // Count only translatable characters and delimiters toward expansion length.
          ++length
        }
      }
      ++currentIndex
    }
    return result.toString()
  }

  override fun placeholder(originalText: String): String =
    // Wrap placeholders in guillemets (»...«).
    PLACEHOLDER_OPEN + originalText + PLACEHOLDER_CLOSE

  /**
   * Generates a space-separated sequence of English number words (`"one two three..."`) containing at least [length] characters, rounded up
   * to the end of the word that crosses [length].
   */
  private fun pseudoGenerateExpansion(length: Int): String {
    val result = StringBuilder(EXPANSION_STRING)
    while (result.length < length) {
      result.append(" ")
      result.append(EXPANSION_STRING)
    }
    // Truncate at the first word boundary after `length` characters so we always emit whole words.
    val lastSpace = result.indexOf(' ', length + 1)
    return result.substring(0, if (lastSpace == -1) result.length else lastSpace)
  }

  /**
   * Scans a `%` format specifier starting at [startIndex] in [source], appends the formatted placeholder (or raw `%%` escape) to [partial],
   * and returns the index of the last character consumed by the specifier.
   */
  private fun processMaybePlaceholder(
    partial: StringBuilder,
    source: String,
    startIndex: Int,
  ): Int {
    val size = source.length
    var currentIndex = startIndex
    var currentChar = source[currentIndex]

    val chunk = StringBuilder().append(currentChar)
    while (currentIndex + 1 < size) {
      ++currentIndex
      currentChar = source[currentIndex]
      chunk.append(currentChar)

      if (currentChar.isPossiblePlaceholderEnd()) {
        break
      }

      // Date/time conversions use a two-character suffix starting with 't' (e.g. "%tY", "%1$tm").
      if (currentIndex + 1 < size && currentChar == 't') {
        ++currentIndex
        currentChar = source[currentIndex]
        chunk.append(currentChar)
        break
      }
    }

    // Wrap the format specifier in »...« unless it ended with '%' (e.g. escaped literal "%%" or a
    // trailing lone '%').
    partial.append(if (currentChar == '%') chunk else placeholder(chunk.toString()))
    return currentIndex
  }

  /**
   * Scans an HTML tag (`<...>`) or HTML character entity (`&...;`) starting at [startIndex] in [source], appends the untouched markup to
   * [sourcePartial], and returns the index of the last character consumed.
   *
   * If a `<` (or `&lt;`) does not have a matching closing `>` before the end of [source], it is not valid HTML markup; in that case, only
   * the initial character at [startIndex] is appended and [startIndex] is returned so subsequent characters are pseudolocalized normally.
   */
  private fun processHtml(sourcePartial: StringBuilder, source: String, startIndex: Int): Int {
    val size = source.length
    var currentIndex = startIndex
    var currentChar = source[currentIndex]
    val htmlPartial = StringBuilder()

    htmlTag@ while (currentIndex < size) {
      if (currentChar == '&') {
        // Scan an HTML entity reference such as "&amp;", "&#160;", or "&lt;".
        val escapeText = StringBuilder().append(currentChar)
        var htmlCodePosition = currentIndex

        htmlCode@ while (htmlCodePosition + 1 < size) {
          ++htmlCodePosition
          currentChar = source[htmlCodePosition]
          escapeText.append(currentChar)

          when (currentChar) {
            ';' -> {
              // Complete HTML entity reference.
              currentIndex = htmlCodePosition
              break@htmlCode
            }
            '#',
            in 'a'..'z',
            in 'A'..'Z',
            in '0'..'9' -> {
              // Valid entity body character; keep scanning.
            }
            else -> {
              // Malformed entity (e.g. standalone '&' followed by space); stop scanning entity.
              break@htmlCode
            }
          }
        }
        htmlPartial.append(escapeText)
        // If the entity is "&lt;", it may represent an escaped opening tag (e.g. "&lt;b>"), so
        // continue scanning the tag until '>'. Otherwise, the standalone entity is complete.
        if (escapeText.toString() != "&lt;") {
          break@htmlTag
        }
      } else if (currentChar == '>') {
        htmlPartial.append('>')
        break@htmlTag
      } else {
        htmlPartial.append(currentChar)
      }

      ++currentIndex
      if (currentIndex >= size) {
        // Reached the end of the string without finding a closing '>': not a valid HTML tag.
        // Emit only the starting character and rewind to startIndex.
        sourcePartial.append(source[startIndex])
        return startIndex
      }
      currentChar = source[currentIndex]
    }
    sourcePartial.append(htmlPartial.toString())
    return currentIndex
  }

  companion object {
    // Matches AAPT2's expansion word list verbatim (including the "fiveteen" spelling in AAPT2).
    private const val EXPANSION_STRING =
      "one two three four five six seven eight nine ten eleven " + "twelve thirteen fourteen fiveteen sixteen seventeen nineteen twenty"
  }
}

/**
 * Stateful pseudolocalizer for Android string and plural resources in `en-rXA` ([Method.ACCENT]) and `ar-rXB` ([Method.BIDI])
 * pseudolocales.
 *
 * In addition to plain text, [Pseudolocalizer] parses ICU MessageFormat syntax (such as `"{COUNT, plural, one {Delete a file} other {Delete
 * {COUNT} files}}"`), pseudolocalizing only the human-readable message fragments while preserving ICU keywords/selectors and wrapping
 * simple `{PLACEHOLDER}` arguments.
 *
 * See https://developer.android.com/guide/topics/resources/pseudolocales for details on how Android pseudolocales transform text.
 */
class Pseudolocalizer(method: Method) {
  private var implementation: PseudoMethodImpl = method.createImpl()
  // Tracks the curly-brace ('{' / '}') nesting depth at the start of the current chunk.
  var lastDepth = 0
    private set

  enum class Method {
    NONE,
    ACCENT,
    BIDI,
  }

  fun setMethod(method: Method) {
    implementation = method.createImpl()
  }

  fun start(): String = implementation.start()

  fun end(): String = implementation.end()

  /** Convenience helper that pseudolocalizes a complete single-fragment string. */
  fun localize(originalText: String): String = start() + text(originalText) + end()

  /**
   * Pseudolocalizes [originalText], handling both plain strings and nested ICU MessageFormat patterns.
   *
   * ### How ICU brace depth (`{` and `}`) works:
   * In ICU MessageFormat syntax, curly-brace nesting alternates between **translatable display text** at **even** depths (`depth % 2 == 0`)
   * and **non-translatable ICU syntax / placeholders** at **odd** depths (`depth % 2 == 1`):
   * - `depth == 0` (even): top-level display text outside any `{...}` block (e.g. `" is offline"` in `"{USER} is offline"`).
   * - `depth == 1` (odd): either a simple argument placeholder (e.g. `"{USER}"`) or an ICU plural/select header and case keywords (e.g.
   *   `"{COUNT, plural, one "` and `" other "` in `"{COUNT, plural, one {Delete a file} other {Delete {COUNT} files}}"`).
   * - `depth == 2` (even): display text inside an ICU plural/select case body (e.g. `"Delete a file"` inside `"one {Delete a file}"`). Each
   *   case body is treated as its own translatable segment with its own [PseudoMethodImpl.start] and [PseudoMethodImpl.end] calls so length
   *   expansion is appended inside each case body (`"one {Ðéļéţé å ƒîļé one two}"`).
   * - `depth == 3` (odd): a nested argument placeholder inside a case body (e.g. `"{COUNT}"` inside `"other {Delete {COUNT} files}"`).
   */
  fun text(originalText: String): String {
    val out = StringBuilder()
    var depth = lastDepth
    var lastPos = 0
    var pos = 0
    val size = originalText.length
    var escaped = false
    while (pos < size) {
      val currentChar = originalText[pos]
      // In ICU MessageFormat syntax, a single quote (') escapes the immediately following
      // character so literal '{' or '}' characters do not change brace depth.
      if (escaped) {
        escaped = false
        ++pos
        continue
      }
      if (currentChar == '\'') {
        escaped = true
        ++pos
        continue
      }

      if (currentChar == ARG_START) {
        ++depth
      } else if (currentChar == ARG_END && depth != 0) {
        --depth
      }

      // Flush the current chunk [lastPos, chunkEndExclusive) whenever brace depth changes or we
      // reach the end of the string.
      if (lastDepth != depth || pos == size - 1) {
        // Even depth means the chunk we are finishing is translatable text; odd depth means it is
        // an ICU argument or selector header.
        val isTranslatableChunk = (lastDepth % 2) == 0
        // Include `currentChar` at `pos` in the chunk when:
        // - We are finishing an odd-depth ICU block (so a simple placeholder like "{USER}"
        //   includes its closing '}' at `pos`), OR
        // - We reached the end of the string without a depth change (`depth == lastDepth`).
        // Conversely, when leaving an even-depth translatable text chunk because we hit '{' or
        // '}', `chunkEndExclusive` stays at `pos` so the brace itself is NOT included in the text
        // chunk.
        val chunkEndExclusive = if (!isTranslatableChunk || depth == lastDepth) pos + 1 else pos
        if (chunkEndExclusive > lastPos) {
          val rawChunk = originalText.substring(lastPos, chunkEndExclusive)
          val processedChunk =
            when {
              isTranslatableChunk -> implementation.text(rawChunk)
              originalText[lastPos] == ARG_START && originalText[chunkEndExclusive - 1] == ARG_END ->
                // An odd-depth chunk that both starts with '{' and ends with '}' is a standalone
                // placeholder (e.g. "{USER}" or "{COUNT}"), so wrap it with placeholder markers
                // (e.g. "»{USER}«"). Compound ICU headers like "{COUNT, plural, one {" end with '{'
                // instead of '}', so they are emitted verbatim without placeholder markers.
                implementation.placeholder(rawChunk)
              else -> rawChunk
            }
          out.append(processedChunk)
        }
        if (isTranslatableChunk && depth < lastDepth) {
          // Exiting a nested translatable case body (e.g. hitting '}' at the end of
          // "one {Delete a file}"): append segment expansion before the closing '}' is processed.
          out.append(implementation.end())
        } else if (!isTranslatableChunk && depth > lastDepth) {
          // Entering a nested translatable case body (e.g. hitting '{' at the start of
          // "one {Delete a file}"): initialize segment counters for the new case body.
          out.append(implementation.start())
        }
        lastPos = chunkEndExclusive
        lastDepth = depth
      }
      ++pos
    }
    return out.toString()
  }
}

private fun Pseudolocalizer.Method.createImpl(): PseudoMethodImpl =
  when (this) {
    Pseudolocalizer.Method.NONE -> PseudoMethodNone
    Pseudolocalizer.Method.ACCENT -> PseudoMethodAccent()
    Pseudolocalizer.Method.BIDI -> PseudoMethodBidi
  }
