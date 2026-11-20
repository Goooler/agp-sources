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
package com.android.ide.common.vectordrawable

import com.android.ide.common.vectordrawable.Svg2Vector.SVG_CLIP_PATH
import com.android.ide.common.vectordrawable.Svg2Vector.SVG_DEFS
import com.android.ide.common.vectordrawable.Svg2Vector.SVG_DISPLAY
import com.android.ide.common.vectordrawable.Svg2Vector.SVG_MASK
import com.android.ide.common.vectordrawable.Svg2Vector.SVG_OPACITY
import com.android.ide.common.vectordrawable.Svg2Vector.SVG_STYLE
import com.android.ide.common.vectordrawable.Svg2Vector.SVG_TSPAN
import java.awt.Font
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Converts an SVG `<text>` element to vector paths by replacing characters with outlines of the corresponding font glyphs.
 *
 * The conversion happens in two phases. During the first phase, triggered by [extract], the `<text>` element is split into runs of
 * characters sharing the same style and an [SvgLeafNode] is created for every run to preserve the drawing order. The second phase,
 * triggered by [convert], has to happen when all `<style>` elements of the SVG document have been processed since CSS rules may affect font
 * properties. During the second phase the text is laid out and the leaf nodes are populated with path data.
 *
 * Supported: `<tspan>` elements, the 'x', 'y', 'dx' and 'dy' attributes, and the 'font-family', 'font-size', 'font-weight', 'font-style',
 * 'font', 'letter-spacing', 'text-anchor', 'dominant-baseline' and 'xml:space' properties. Fonts are looked up among the fonts installed on
 * the computer running the conversion. Characters missing from a font are rendered using the next font in the 'font-family' list or a
 * fallback font.
 *
 * Not supported: `<textPath>`, `<tref>`, individual glyph positioning and rotation, 'textLength', 'text-decoration', 'word-spacing',
 * 'baseline-shift', 'font-variant', 'font-stretch', 'direction: rtl', and 'writing-mode' other than horizontal left-to-right. An error or a
 * warning is logged when any of these is encountered.
 */
internal class SvgTextConverter
private constructor(
  private val svgTree: SvgTree,
  private val textElement: Element,
  private val parentGroup: SvgGroupNode,
  private val group: SvgGroupNode,
) {
  /** A sequence of characters belonging to the same element. */
  private class Run(
    /** The innermost element containing the characters, either `<text>` or `<tspan>`. */
    val element: Element,
    val node: SvgLeafNode,
    var text: String,
  ) {
    // Layout results.
    /** True if the run starts a new text chunk, i.e. has an absolute position. */
    var startsChunk = false
    /** The absolute X coordinate of the start of the run, or NaN if the run continues from the current text position. */
    var absoluteX = Double.NaN
    /** The absolute Y coordinate of the start of the run, or NaN if the run continues from the current text position. */
    var absoluteY = Double.NaN
    var dx = 0.0
    var dy = 0.0
    /** The outline of the run's glyphs in layout units relative to the start of the run, or null if the run has not been laid out. */
    var outline: Shape? = null
    var scale = 0.0
    var advance = 0.0
    var baselineShift = 0.0
  }

  private val runs = mutableListOf<Run>()

  /** The `<text>` and `<tspan>` elements which position attributes, e.g. 'x' or 'dy', have already been applied to a run. */
  private val positionedElements: MutableSet<Element> = Collections.newSetFromMap(IdentityHashMap())

  /** Font families for which an unavailability warning has already been reported. */
  private val reportedFontFamilies = mutableSetOf<String>()

  /** Computed font sizes of elements. */
  private val fontSizes = IdentityHashMap<Element, Double>()

  /** Characters for which a missing font warning has already been reported. */
  private val reportedMissingCharacters = mutableSetOf<String>()

  private val fontCache = mutableMapOf<String, List<Font>>()

  /**
   * Recursively splits contents of the given element into runs.
   *
   * @param element the `<text>` or a `<tspan>` element
   */
  private fun extractRuns(element: Element) {
    val childNodes = element.childNodes
    for (i in 0 until childNodes.length) {
      val childNode = childNodes.item(i)
      when (childNode.nodeType) {
        Node.TEXT_NODE,
        Node.CDATA_SECTION_NODE -> {
          val text = childNode.nodeValue
          if (!text.isNullOrEmpty()) {
            addRun(element, text)
          }
        }
        Node.ELEMENT_NODE -> {
          val childElement = childNode as Element
          val tagName = childElement.tagName
          if (tagName == SVG_TSPAN) {
            extractRuns(childElement)
          } else if (tagName !in NON_RENDERED_ELEMENTS) {
            svgTree.logError("<$tagName> is not supported", childElement)
          }
        }
      }
    }
  }

  private fun addRun(element: Element, text: String) {
    val node = SvgLeafNode(svgTree, element, "text${runs.size}")
    // The transformation of the <text> element is handled by the group node.
    node.mLocalTransform = AffineTransform()
    group.addChild(node)
    runs.add(Run(element, node, text))
  }

  /**
   * Determines values of the presentation properties, e.g. 'fill' or 'stroke', for the given run and applies them to the run's leaf node. A
   * value defined for an element takes precedence over the values defined for its ancestors. The only exception is the 'opacity' property
   * since opacity of an element is combined with opacity of its ancestors.
   */
  private fun applyPresentationProperties(run: Run) {
    for (propertyName in Svg2Vector.presentationMap.keys) {
      var value: String? = if (propertyName == SVG_OPACITY) getCombinedOpacity(run.element) else null
      if (value != null) {
        run.node.fillPresentationAttributes(propertyName, value)
        continue
      }
      for (element in run.element.selfAndAncestors()) {
        if (element.tagName == SVG_DEFS) {
          // Don't inherit from ancestors of <defs> so that <use> attributes can apply to the referenced text.
          break
        }
        val declaredValue = getDeclaredProperty(element, propertyName)
        if (declaredValue != null && declaredValue != "inherit") {
          value = declaredValue
          break
        }
      }
      if (value != null) {
        run.node.fillPresentationAttributes(propertyName, value)
      }
    }
  }

  /**
   * Returns the product of the opacity values defined for the given element and its ancestors up to the `<text>` element, or null if
   * opacity is not defined for any of these elements. Multiplication of opacity values produces an accurate result only when glyphs don't
   * overlap, but is the best possible approximation since vector drawables don't support group opacity.
   */
  private fun getCombinedOpacity(element: Element): String? {
    var combinedOpacity = Double.NaN
    for (ancestor in element.selfAndAncestors()) {
      val opacity = getDeclaredProperty(ancestor, SVG_OPACITY)?.let { parseOpacity(it) } ?: Double.NaN
      if (!opacity.isNaN()) {
        combinedOpacity = if (combinedOpacity.isNaN()) opacity else combinedOpacity * opacity
      }
      if (ancestor === textElement) {
        break
      }
    }
    return if (combinedOpacity.isNaN()) null else combinedOpacity.toString()
  }

  /** Lays out the text and populates the leaf nodes with path data. */
  fun convert() {
    val clipPath = getDeclaredProperty(textElement, SVG_CLIP_PATH) ?: getDeclaredProperty(textElement, SVG_MASK)
    if (clipPath != null) {
      svgTree.addClipPathAffectedNode(group, parentGroup, clipPath)
    }

    normalizeWhitespace()
    for (run in runs) {
      applyPresentationProperties(run)
      layoutRun(run)
    }

    var x = 0.0
    var y = 0.0
    var anchorShift = 0.0
    for ((i, run) in runs.withIndex()) {
      if (i == 0 || run.startsChunk) {
        anchorShift = getAnchorShift(i)
      }
      if (!run.absoluteX.isNaN()) {
        x = run.absoluteX
      }
      if (!run.absoluteY.isNaN()) {
        y = run.absoluteY
      }
      x += run.dx
      y += run.dy
      val outline = run.outline
      if (outline != null && run.text.isNotBlank()) {
        val transform = AffineTransform(run.scale, 0.0, 0.0, run.scale, x + anchorShift, y + run.baselineShift)
        val path = transform.createTransformedShape(outline)
        val pathData = if (isRepresentable(path)) toPathData(path) else ""
        if (pathData.isEmpty()) {
          if (!path.getPathIterator(null).isDone) {
            svgTree.logError("Text position or size is out of range", run.element)
          }
        } else {
          run.node.setPathData(pathData)
          svgTree.hasLeafNode = true
        }
      }
      x += run.advance
    }
  }

  /** Returns the horizontal shift of the text chunk starting at the given run due to the 'text-anchor' property. */
  private fun getAnchorShift(firstRunOfChunk: Int): Double {
    val firstRun = runs[firstRunOfChunk]
    val anchor = getInheritedProperty(firstRun.element, "text-anchor")
    if (anchor != "middle" && anchor != "end") {
      return 0.0
    }
    var width = firstRun.advance
    for (i in firstRunOfChunk + 1 until runs.size) {
      val run = runs[i]
      if (run.startsChunk) {
        break
      }
      width += run.dx + run.advance
    }
    return if (anchor == "middle") -width / 2 else -width
  }

  /**
   * Collapses whitespace the same way browsers do. Unless `xml:space="preserve"` is in effect, line breaks and tabs are treated as spaces,
   * sequences of spaces are replaced by a single space, and spaces at the beginning and at the end of the text are removed. This matches
   * the behavior prescribed by https://www.w3.org/TR/SVG2/text.html#WhiteSpace and differs from
   * https://www.w3.org/TR/SVG11/text.html#WhiteSpace, which requires line breaks to be removed without leaving a space behind.
   */
  private fun normalizeWhitespace() {
    var previousIsSpace = true // To strip leading spaces.
    var lastRunEndingWithCollapsibleSpace: Run? = null
    for (run in runs) {
      if (!isDisplayed(run.element)) {
        run.text = ""
        continue
      }
      if (getInheritedProperty(run.element, "xml:space") == "preserve") {
        run.text = run.text.replace(LINE_BREAK_OR_TAB, " ")
        if (run.text.isNotEmpty()) {
          previousIsSpace = false
          lastRunEndingWithCollapsibleSpace = null
        }
        continue
      }

      val buf = StringBuilder(run.text.length)
      for (char in run.text) {
        val c = if (char == '\t' || char == '\n' || char == '\r') ' ' else char
        if (c == ' ') {
          if (previousIsSpace) {
            continue
          }
          previousIsSpace = true
        } else {
          previousIsSpace = false
        }
        buf.append(c)
      }
      run.text = buf.toString()
      if (run.text.isNotEmpty()) {
        lastRunEndingWithCollapsibleSpace = if (previousIsSpace) run else null
      }
    }

    // Strip the trailing space.
    lastRunEndingWithCollapsibleSpace?.let { it.text = it.text.dropLast(1) }
  }

  /** Computes position adjustments, the advance and the layout of the given run. */
  private fun layoutRun(run: Run) {
    if (run.text.isEmpty()) {
      // A run without characters, for example a hidden one, doesn't affect layout.
      return
    }

    // Position attributes of an element apply to its first rendered character. Apply the attributes of the elements enclosing the run
    // that haven't been applied to any preceding run, starting from the outermost one. Elements without rendered characters, for example
    // hidden or empty ones, don't enclose any non-empty runs and therefore have no effect on the text position.
    val viewportWidth = svgTree.viewportWidth.toDouble()
    val viewportHeight = svgTree.viewportHeight.toDouble()
    val enclosingElements = mutableListOf<Element>()
    for (element in run.element.selfAndAncestors()) {
      enclosingElements.add(element)
      if (element === textElement) {
        break
      }
    }
    for (element in enclosingElements.asReversed()) {
      if (!positionedElements.add(element)) {
        continue
      }
      val fontSize = getFontSize(element)
      val x = getCoordinate(element, "x", fontSize, viewportWidth)
      val y = getCoordinate(element, "y", fontSize, viewportHeight)
      val dx = getCoordinate(element, "dx", fontSize, viewportWidth)
      val dy = getCoordinate(element, "dy", fontSize, viewportHeight)
      // The four attributes are independent of each other. When an attribute is specified by several enclosing elements, the value
      // specified by the innermost one takes precedence.
      if (!x.isNaN()) {
        run.absoluteX = x
        run.startsChunk = true
      }
      if (!y.isNaN()) {
        run.absoluteY = y
        run.startsChunk = true
      }
      if (!dx.isNaN()) {
        run.dx = dx
      }
      if (!dy.isNaN()) {
        run.dy = dy
      }
      if (element.hasAttribute("rotate")) {
        svgTree.logWarning("Rotation of individual glyphs is not supported", element)
      }
      reportUnsupportedProperties(element)
    }

    val fontSize = getFontSize(run.element)
    if (fontSize <= 0) {
      return
    }
    val fonts = getFonts(run.element, fontSize)
    val outline = Path2D.Double()
    var advance = 0.0
    for ((text, font) in splitByFont(run.text, fonts, run.element)) {
      val layout = TextLayout(text, font, FONT_RENDER_CONTEXT)
      outline.append(layout.getOutline(AffineTransform.getTranslateInstance(advance, 0.0)), false)
      advance += layout.advance
    }
    run.scale = fontSize / LAYOUT_FONT_SIZE
    run.outline = outline
    run.advance = advance * run.scale
    run.baselineShift = getBaselineShift(run.element, fonts[0]) * run.scale
  }

  /**
   * Splits the given text into segments to be rendered using different fonts. Every character is rendered using the first of the given
   * fonts that is able to display it. Logs a warning if the text contains characters that cannot be displayed by any of the fonts. Such
   * characters are rendered using the first font.
   *
   * @param text the text to split
   * @param fonts the fonts in the order of decreasing priority
   * @param element the element containing the text
   * @return the segments of the text and the corresponding fonts
   */
  private fun splitByFont(text: String, fonts: List<Font>, element: Element): List<Pair<String, Font>> {
    val segments = mutableListOf<Pair<String, Font>>()
    val missingCharacters = mutableSetOf<Int>()
    var segmentFont: Font? = null
    var segmentStart = 0
    var offset = 0
    while (offset < text.length) {
      val codePoint = text.codePointAt(offset)
      val font =
        when {
          // Keep combining marks and format characters, e.g. zero width joiners, together with the preceding character.
          segmentFont != null && isClusterExtender(codePoint) -> segmentFont
          else ->
            fonts.firstOrNull { it.canDisplay(codePoint) }
              ?: fonts[0].also {
                if (!Character.isISOControl(codePoint)) {
                  missingCharacters.add(codePoint)
                }
              }
        }
      if (font !== segmentFont) {
        if (segmentFont != null) {
          segments.add(Pair(text.substring(segmentStart, offset), segmentFont))
        }
        segmentFont = font
        segmentStart = offset
      }
      offset += Character.charCount(codePoint)
    }
    if (segmentFont != null) {
      segments.add(Pair(text.substring(segmentStart), segmentFont))
    }

    if (missingCharacters.isNotEmpty()) {
      val characters = missingCharacters.joinToString { String.format("U+%04X", it) }
      if (reportedMissingCharacters.add(characters)) {
        svgTree.logWarning("No font is available for the following characters: $characters", element)
      }
    }
    return segments
  }

  /**
   * Logs warnings for the properties of the given `<text>` or `<tspan>` element that affect rendering of text but are not supported. For
   * the `<text>` element the properties inherited from its ancestors are also taken into account.
   */
  private fun reportUnsupportedProperties(element: Element) {
    fun declared(propertyName: String): String? = getDeclaredProperty(element, propertyName)?.takeUnless { it == "inherit" }
    fun inherited(propertyName: String): String? =
      if (element === textElement) getInheritedProperty(element, propertyName) else declared(propertyName)

    fun report(propertyName: String, value: String?, isSupported: (String) -> Boolean) {
      if (value != null && !isSupported(value)) {
        svgTree.logWarning("\"$propertyName: $value\" is not supported", element)
      }
    }

    report("text-decoration", inherited("text-decoration")) { it == "none" }
    report("word-spacing", inherited("word-spacing")) { it == "normal" || isZeroLength(it) }
    report("writing-mode", inherited("writing-mode")) { it in SUPPORTED_WRITING_MODES }
    report("direction", inherited("direction")) { it == "ltr" }
    report("font-variant", inherited("font-variant")) { it == "normal" }
    report("font-stretch", inherited("font-stretch")) { it == "normal" || it == "100%" }
    report("baseline-shift", declared("baseline-shift")) { it == "baseline" || isZeroLength(it) }
    if (element.hasAttribute("textLength")) {
      svgTree.logWarning("\"textLength\" attribute is not supported", element)
    }
  }

  /** Returns the first value of a coordinate list attribute, or NaN if the attribute is not present or is invalid. */
  private fun getCoordinate(element: Element, attributeName: String, fontSize: Double, percentageBase: Double): Double {
    val value = element.getAttribute(attributeName).trim()
    val values = value.split(LIST_SEPARATOR).filter { it.isNotEmpty() }
    if (values.isEmpty()) {
      return Double.NaN
    }
    if (values.size > 1) {
      svgTree.logWarning(
        "Positioning of individual glyphs is not supported, only the first value of the \"$attributeName\" attribute is used",
        element,
      )
    }
    return try {
      parseLength(values[0], fontSize, percentageBase)
    } catch (_: NumberFormatException) {
      svgTree.logError("Invalid attribute value: $attributeName=\"$value\"", element)
      Double.NaN
    }
  }

  /**
   * Returns the vertical shift, in layout units, of the alphabetic baseline due to the 'dominant-baseline' or the 'alignment-baseline'
   * property.
   */
  private fun getBaselineShift(element: Element, font: Font): Double {
    val baseline =
      getInheritedProperty(element, "dominant-baseline")?.takeUnless { it == "auto" }
        ?: getDeclaredProperty(element, "alignment-baseline")
        ?: return 0.0
    val metrics = font.getLineMetrics("x", FONT_RENDER_CONTEXT)
    return when (baseline) {
      "middle" -> font.createGlyphVector(FONT_RENDER_CONTEXT, "x").visualBounds.height / 2
      "central" -> (metrics.ascent - metrics.descent) / 2.0
      "hanging",
      "text-before-edge",
      "text-top" -> metrics.ascent.toDouble()
      "text-after-edge",
      "text-bottom",
      "ideographic" -> -metrics.descent.toDouble()
      else -> 0.0
    }
  }

  /**
   * Returns the fonts for rendering text of the given element in the order of decreasing priority. A font is used only for the characters
   * that cannot be displayed by the fonts preceding it.
   */
  private fun getFonts(element: Element, fontSize: Double): List<Font> {
    val families = getInheritedProperty(element, "font-family")
    val fontWeight = parseFontWeight(getInheritedProperty(element, "font-weight"))
    val fontStyle = getInheritedProperty(element, "font-style")
    val italic = fontStyle == "italic" || fontStyle == "oblique"
    val tracking = getLetterSpacing(element, fontSize) / fontSize

    return fontCache.getOrPut("$families|$fontWeight|$italic|$tracking") {
      val attributes = mutableMapOf<TextAttribute, Any>()
      attributes[TextAttribute.SIZE] = LAYOUT_FONT_SIZE
      attributes[TextAttribute.KERNING] = TextAttribute.KERNING_ON
      attributes[TextAttribute.LIGATURES] = TextAttribute.LIGATURES_ON
      if (tracking != 0.0) {
        attributes[TextAttribute.TRACKING] = tracking.toFloat()
      }
      resolveFonts(families, fontWeight, italic, element).map { it.deriveFont(attributes) }
    }
  }

  private fun getLetterSpacing(element: Element, fontSize: Double): Double {
    val value = getInheritedProperty(element, "letter-spacing")
    if (value == null || value == "normal") {
      return 0.0
    }
    return try {
      parseLength(value, fontSize, fontSize)
    } catch (_: NumberFormatException) {
      0.0
    }
  }

  /**
   * Returns fonts of size 1 for the available fonts from the given comma-separated list of font families followed by a fallback font
   * capable of displaying characters of most scripts. Logs a warning if none of the fonts in the list are available.
   */
  private fun resolveFonts(families: String?, fontWeight: Int, italic: Boolean, element: Element): List<Font> {
    val italicStyle = if (italic) Font.ITALIC else 0
    val style = italicStyle or (if (fontWeight >= SEMIBOLD_FONT_WEIGHT) Font.BOLD else 0)
    val fonts = mutableListOf<Font>()
    if (families.isNullOrBlank()) {
      fonts.add(Font(DEFAULT_FONT_FAMILY, style, 1))
    } else {
      for (quotedFamily in families.split(',')) {
        val family = unquote(quotedFamily.trim())
        val genericFamily = GENERIC_FONT_FAMILIES[family.lowercase(Locale.US)]
        val font = if (genericFamily == null) findFont(family, fontWeight, italicStyle, style) else Font(genericFamily, style, 1)
        if (font != null) {
          fonts.add(font)
        }
      }
      if (fonts.isEmpty() && reportedFontFamilies.add(families)) {
        svgTree.logWarning("Font \"${families.replace('"', '\'')}\" is not available, a sans-serif font is used instead", element)
      }
    }
    // Unlike physical fonts, the Java logical fonts support characters of all scripts for which there are fonts installed.
    fonts.add(Font(FALLBACK_FONT_FAMILY, style, 1))
    return fonts.distinct()
  }

  /** Returns the computed value of the 'font-size' property for the given element. */
  private fun getFontSize(element: Element): Double = fontSizes.getOrPut(element) { computeFontSize(element) }

  private fun computeFontSize(element: Element): Double {
    val parent = element.parentNode as? Element
    val value = getDeclaredProperty(element, "font-size")
    if (value == null || value == "inherit") {
      return parent?.let { getFontSize(it) } ?: DEFAULT_FONT_SIZE
    }
    FONT_SIZE_KEYWORDS[value]?.let {
      return it
    }
    val parentSize = parent?.let { getFontSize(it) } ?: DEFAULT_FONT_SIZE
    return when (value) {
      "smaller" -> parentSize / 1.2
      "larger" -> parentSize * 1.2
      else ->
        try {
          parseLength(value, parentSize, parentSize)
        } catch (_: NumberFormatException) {
          svgTree.logError("Invalid font size \"$value\"", element)
          parentSize
        }
    }
  }

  /** Checks if neither the given element, nor any of its ancestors have "display:none". */
  private fun isDisplayed(element: Element): Boolean = element.selfAndAncestors().none { getDeclaredProperty(it, SVG_DISPLAY) == "none" }

  /**
   * Returns the value of an inherited property for the given element, or null if the property is not defined for the element or any of its
   * ancestors.
   */
  private fun getInheritedProperty(element: Element, propertyName: String): String? =
    element.selfAndAncestors().firstNotNullOfOrNull { ancestor ->
      getDeclaredProperty(ancestor, propertyName)?.takeUnless { it == "inherit" }
    }

  /**
   * Returns the value of a property defined for the given element by, in the order of decreasing priority, the 'style' attribute, a CSS
   * rule, or a presentation attribute. Returns null if the property is not defined. Values of the 'font-style', 'font-weight', 'font-size'
   * and 'font-family' properties may also be defined by the 'font' shorthand property.
   */
  private fun getDeclaredProperty(element: Element, propertyName: String): String? {
    getStyleDeclaration(element.getAttribute(SVG_STYLE), propertyName, lastWins = true)?.let {
      return it
    }

    // CSS rules are checked in the order of decreasing specificity of their selectors: "tag.class", ".class", "tag".
    // Contents of the CSS rules with the same selector are concatenated by Svg2Vector in the reverse order. This means that
    // the declaration that comes first has the highest priority.
    val tagName = element.tagName
    val classNames = element.getAttribute("class").split(WHITESPACE).filter { it.isNotEmpty() }
    val selectors = classNames.map { "$tagName.$it" } + classNames.map { ".$it" } + tagName
    for (selector in selectors) {
      getStyleDeclaration(svgTree.getStyleClassAttr(selector), propertyName, lastWins = false)?.let {
        return it
      }
    }

    element
      .getAttribute(propertyName)
      .trim()
      .ifEmpty { null }
      ?.let {
        return it
      }
    if (propertyName in FONT_SHORTHAND_PROPERTIES) {
      return parseFontShorthand(element.getAttribute("font"))?.get(propertyName)
    }
    return null
  }

  companion object {
    /** The text is laid out using a large font size and then scaled down to avoid distortion of glyph outlines and advances by hinting. */
    private const val LAYOUT_FONT_SIZE = 1000f

    /** The 'medium' font size that is the initial value of the 'font-size' property. */
    private const val DEFAULT_FONT_SIZE = 16.0

    private const val DEFAULT_FONT_FAMILY = Font.SERIF
    private const val FALLBACK_FONT_FAMILY = Font.SANS_SERIF

    private const val NORMAL_FONT_WEIGHT = 400
    private const val SEMIBOLD_FONT_WEIGHT = 600
    private const val BOLD_FONT_WEIGHT = 700

    /** Suffixes of names of fonts of the same family that have different weights. */
    private val FONT_WEIGHT_NAMES: Map<Int, List<String>> =
      mapOf(
        100 to listOf("Thin", "Hairline"),
        200 to listOf("ExtraLight", "Extra Light", "UltraLight", "Ultra Light"),
        300 to listOf("Light"),
        500 to listOf("Medium"),
        600 to listOf("SemiBold", "Semi Bold", "DemiBold", "Demi Bold"),
        700 to listOf("Bold"),
        800 to listOf("ExtraBold", "Extra Bold", "UltraBold", "Ultra Bold"),
        900 to listOf("Black", "Heavy"),
      )

    private val FONT_WEIGHT_SUFFIXES: List<String> = FONT_WEIGHT_NAMES.values.flatten().map { " ${it.lowercase(Locale.US)}" }

    private val CAMEL_CASE_BOUNDARY = Regex("(?<=[a-z])(?=[A-Z])")
    private val WHITESPACE = Regex("\\s+")
    private val LIST_SEPARATOR = Regex("[\\s,]+")
    private val LINE_BREAK_OR_TAB = Regex("[\\t\\r\\n]")
    private val FONT_WEIGHT_NUMBER = Regex("[1-9]00")
    private val NUMBER = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")

    private val FONT_RENDER_CONTEXT = FontRenderContext(null, true, true)

    private val GENERIC_FONT_FAMILIES: Map<String, String> =
      mapOf(
        "serif" to Font.SERIF,
        "ui-serif" to Font.SERIF,
        "sans-serif" to Font.SANS_SERIF,
        "ui-sans-serif" to Font.SANS_SERIF,
        "system-ui" to Font.SANS_SERIF,
        "cursive" to Font.SANS_SERIF,
        "fantasy" to Font.SANS_SERIF,
        "monospace" to Font.MONOSPACED,
        "ui-monospace" to Font.MONOSPACED,
      )

    private val FONT_SIZE_KEYWORDS: Map<String, Double> =
      mapOf(
        "xx-small" to 9.0,
        "x-small" to 10.0,
        "small" to 13.0,
        "medium" to DEFAULT_FONT_SIZE,
        "large" to 18.0,
        "x-large" to 24.0,
        "xx-large" to 32.0,
      )

    /** The properties that are set by the 'font' shorthand property. */
    private val FONT_SHORTHAND_PROPERTIES: Set<String> =
      setOf("font-style", "font-variant", "font-weight", "font-stretch", "font-size", "font-family")

    /** Values of the 'writing-mode' property corresponding to the horizontal left-to-right text. */
    private val SUPPORTED_WRITING_MODES: Set<String> = setOf("horizontal-tb", "lr-tb", "lr")

    /** Values of the 'font-stretch' property that may be present in the 'font' shorthand property. */
    private val FONT_STRETCH_KEYWORDS: Set<String> =
      setOf(
        "ultra-condensed",
        "extra-condensed",
        "condensed",
        "semi-condensed",
        "semi-expanded",
        "expanded",
        "extra-expanded",
        "ultra-expanded",
      )

    /** Elements that may be present inside `<text>` but don't contribute to rendering. */
    private val NON_RENDERED_ELEMENTS: Set<String> = setOf("title", "desc", "metadata")

    /**
     * Creates SVG tree nodes for the given `<text>` element and registers a converter with the SVG tree that will populate the nodes with
     * path data when [convert] is called.
     *
     * @param svgTree the SVG tree
     * @param textElement the `<text>` element
     * @param parentGroup the group to add the created nodes to
     * @param name the name for the created group node
     * @return the group node representing the text
     */
    @JvmStatic
    fun extract(svgTree: SvgTree, textElement: Element, parentGroup: SvgGroupNode, name: String): SvgGroupNode {
      // The group node takes care of the transformation and of the presentation attributes of the <text> element.
      val group = SvgGroupNode(svgTree, textElement, name)
      parentGroup.addChild(group)
      val converter = SvgTextConverter(svgTree, textElement, parentGroup, group)
      converter.extractRuns(textElement)
      svgTree.addPendingText(converter)
      return group
    }

    /** Returns the sequence consisting of this element followed by its ancestor elements starting from the closest one. */
    private fun Element.selfAndAncestors(): Sequence<Element> = generateSequence(this) { it.parentNode as? Element }

    /**
     * Returns a font of size 1 for the given font family, or null if the font is not available.
     *
     * @param family the name of the font family
     * @param fontWeight the font weight between 100 and 900
     * @param italicStyle either [Font.ITALIC] or [Font.PLAIN]
     * @param style the Java font style corresponding to [fontWeight] and [italicStyle]
     */
    private fun findFont(family: String, fontWeight: Int, italicStyle: Int, style: Int): Font? {
      for (name in getFontNameVariants(family)) {
        if (!isFontAvailable(name)) {
          continue
        }
        if (hasWeightSuffix(name)) {
          // The font name, e.g. "Roboto Medium", already determines the weight.
          return Font(name, italicStyle, 1)
        }
        // Many font families have separate fonts for different weights, e.g. "Roboto Light" or "Roboto Medium".
        for (weightName in FONT_WEIGHT_NAMES[fontWeight].orEmpty()) {
          val nameWithWeight = "$name $weightName"
          if (isFontAvailable(nameWithWeight)) {
            return Font(nameWithWeight, italicStyle, 1)
          }
        }
        return Font(name, style, 1)
      }
      return null
    }

    /** Checks if the given character is a combining mark or a format character that has to be rendered together with the preceding one. */
    private fun isClusterExtender(codePoint: Int): Boolean =
      when (Character.getType(codePoint).toByte()) {
        Character.NON_SPACING_MARK,
        Character.ENCLOSING_MARK,
        Character.COMBINING_SPACING_MARK,
        Character.FORMAT -> true
        else -> false
      }

    /** Converts a value of the 'font-weight' property to a number between 100 and 900. */
    private fun parseFontWeight(fontWeight: String?): Int =
      when (fontWeight) {
        null -> NORMAL_FONT_WEIGHT
        "bold",
        "bolder" -> BOLD_FONT_WEIGHT
        "lighter" -> 300
        else ->
          try {
            (parseNumber(fontWeight).coerceIn(100.0, 900.0) / 100).roundToInt() * 100
          } catch (_: NumberFormatException) {
            NORMAL_FONT_WEIGHT
          }
      }

    /**
     * Returns possible Java names for the given font name. SVG files produced by some graphic editors refer to fonts by their PostScript
     * names, e.g. "Roboto-Medium" or "RobotoCondensed-Bold", whereas Java expects "Roboto Medium" or "Roboto Condensed Bold".
     */
    private fun getFontNameVariants(name: String): List<String> {
      if (name.indexOf('-') <= 0) {
        return listOf(name)
      }
      val spaced = name.replace('-', ' ')
      return listOf(name, spaced, spaced.replace(CAMEL_CASE_BOUNDARY, " ")).distinct()
    }

    private fun hasWeightSuffix(fontName: String): Boolean {
      val lowerCaseName = fontName.lowercase(Locale.US)
      return FONT_WEIGHT_SUFFIXES.any { lowerCaseName.endsWith(it) }
    }

    private fun isFontAvailable(name: String): Boolean {
      if (name.isEmpty()) {
        return false
      }
      // When a font is not available, Java substitutes the "Dialog" logical font.
      val font = Font(name, Font.PLAIN, 1)
      return !Font.DIALOG.equals(font.getFamily(Locale.US), ignoreCase = true) || Font.DIALOG.equals(name, ignoreCase = true)
    }

    private fun unquote(value: String): String {
      if (value.length >= 2) {
        val first = value[0]
        if ((first == '"' || first == '\'') && value.last() == first) {
          return value.substring(1, value.length - 1).trim()
        }
      }
      return value
    }

    /**
     * Parses an SVG length.
     *
     * @param length the value to parse
     * @param fontSize the font size for conversion of the "em" and "ex" units
     * @param percentageBase the value corresponding to 100%
     * @return the length in user units
     * @throws NumberFormatException if the value is not a valid length
     */
    private fun parseLength(length: String, fontSize: Double, percentageBase: Double): Double {
      val value = length.trim()
      val result =
        if (value.endsWith("%")) {
          parseNumber(value.dropLast(1)) / 100 * percentageBase
        } else {
          val factor =
            when (value.takeLast(2).lowercase(Locale.US)) {
              "px" -> 1.0
              "pt" -> 96.0 / 72
              "pc" -> 16.0
              "in" -> 96.0
              "cm" -> 96 / 2.54
              "mm" -> 96 / 25.4
              "em" -> fontSize
              "ex" -> fontSize / 2
              else -> Double.NaN // No units.
            }
          if (factor.isNaN()) parseNumber(value) else parseNumber(value.dropLast(2)) * factor
        }
      if (!result.isFinite()) {
        throw NumberFormatException("Value out of range: \"$length\"")
      }
      return result
    }

    /** Parses a value of the 'opacity' property and returns a number between 0 and 1, or NaN if the value is not valid. */
    private fun parseOpacity(value: String): Double =
      try {
        val opacity = if (value.endsWith("%")) parseNumber(value.dropLast(1)) / 100 else parseNumber(value)
        opacity.coerceIn(0.0, 1.0)
      } catch (_: NumberFormatException) {
        Double.NaN
      }

    /** Checks if the given value is a valid length that is equal to zero. */
    private fun isZeroLength(value: String): Boolean =
      try {
        parseLength(value, 1.0, 1.0) == 0.0
      } catch (_: NumberFormatException) {
        false
      }

    /**
     * Parses a number in the SVG format. Unlike [String.toDouble], doesn't accept "NaN", "Infinity", hexadecimal numbers, type suffixes, or
     * numbers that are too large to be represented by a finite [Double] value.
     *
     * @throws NumberFormatException if the value is not a valid finite number
     */
    private fun parseNumber(value: String): Double {
      if (!value.matches(NUMBER)) {
        throw NumberFormatException("Invalid number: \"$value\"")
      }
      val number = value.toDouble()
      if (!number.isFinite()) {
        throw NumberFormatException("Number out of range: \"$value\"")
      }
      return number
    }

    /**
     * Returns the value of the given property in a semicolon-separated list of CSS declarations, or null if not found. Takes into account
     * the 'font' shorthand property.
     *
     * @param style the list of CSS declarations
     * @param propertyName the name of the property to return the value of
     * @param lastWins determines whether the last or the first of the matching declarations is used
     */
    private fun getStyleDeclaration(style: String?, propertyName: String, lastWins: Boolean): String? {
      if (style.isNullOrEmpty()) {
        return null
      }
      var result: String? = null
      for (declaration in style.split(';')) {
        val colon = declaration.indexOf(':')
        if (colon <= 0) {
          continue
        }
        val name = declaration.substring(0, colon).trim()
        val declaredValue = declaration.substring(colon + 1).trim()
        val value =
          when {
            declaredValue.isEmpty() -> null
            name == propertyName -> declaredValue
            name == "font" && propertyName in FONT_SHORTHAND_PROPERTIES -> parseFontShorthand(declaredValue)?.get(propertyName)
            else -> null
          } ?: continue
        if (!lastWins) {
          return value
        }
        result = value
      }
      return result
    }

    /**
     * Parses the value of the 'font' shorthand property, e.g. "italic bold 12px/30px Arial, serif".
     *
     * @return the map containing values of the properties listed in [FONT_SHORTHAND_PROPERTIES] keyed by property names, or null if the
     *   given value is not a valid value of the 'font' property
     */
    private fun parseFontShorthand(font: String): Map<String, String>? {
      // All properties not mentioned in the shorthand are reset to their initial values.
      val result = mutableMapOf("font-style" to "normal", "font-variant" to "normal", "font-weight" to "normal", "font-stretch" to "normal")
      var remainder = font.trim()
      while (remainder.isNotEmpty()) {
        val token = remainder.takeWhile { !it.isWhitespace() }
        val rest = remainder.substring(token.length).trim()
        when {
          token == "italic" || token == "oblique" -> result["font-style"] = token
          token == "bold" || token == "bolder" || token == "lighter" || token.matches(FONT_WEIGHT_NUMBER) -> result["font-weight"] = token
          token == "small-caps" -> result["font-variant"] = token
          token in FONT_STRETCH_KEYWORDS -> result["font-stretch"] = token
          token == "normal" -> {}
          else -> {
            // The token is the font size optionally followed by a slash and the line height. The rest is the font family.
            if (rest.isEmpty()) {
              return null
            }
            result["font-size"] = token.substringBefore('/')
            result["font-family"] = rest
            return result
          }
        }
        remainder = rest
      }
      return null // Font size and font family are mandatory.
    }

    /** Checks if all coordinates of the given shape are finite numbers that can be represented by [Float] values. */
    private fun isRepresentable(shape: Shape): Boolean {
      val bounds = shape.bounds2D
      return listOf(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY).all { abs(it) <= Float.MAX_VALUE }
    }

    /** Converts the given shape to SVG path data. */
    private fun toPathData(shape: Shape): String {
      val builder = PathBuilder()
      val coords = DoubleArray(6)
      val iterator = shape.getPathIterator(null)
      while (!iterator.isDone) {
        when (iterator.currentSegment(coords)) {
          PathIterator.SEG_MOVETO -> builder.absoluteMoveTo(coords[0], coords[1])
          PathIterator.SEG_LINETO -> builder.absoluteLineTo(coords[0], coords[1])
          PathIterator.SEG_QUADTO -> builder.absoluteQuadraticCurveTo(coords[0], coords[1], coords[2], coords[3])
          PathIterator.SEG_CUBICTO -> builder.absoluteCurveTo(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5])
          PathIterator.SEG_CLOSE -> builder.absoluteClose()
        }
        iterator.next()
      }
      return builder.toString()
    }
  }
}
