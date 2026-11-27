/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.manifmerger

import com.android.SdkConstants
import com.android.ide.common.blame.SourceFile
import com.android.ide.common.blame.SourceFilePosition
import com.android.ide.common.blame.SourceFilePosition.UNKNOWN
import com.android.manifmerger.DeepLink.DeepLinkException
import com.android.utils.PositionXmlParser
import com.android.utils.XmlUtils
import com.google.common.collect.ImmutableList
import java.net.URISyntaxException
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap

/**
 * Represents a loaded navigation xml document.
 *
 * Has a pointer to the root [Element] and provides services to get lists of the directly referenced navigation xml IDs and [DeepLink]s.
 *
 * Note: Instances backed by a DOM [Element] are not safe for concurrent access while [getXml] is traversed or mutated.
 */
class NavigationXmlDocument
private constructor(
  private val sourceFile: SourceFile?,
  private val rootElement: Element?,
  private val precomputedData: NavigationXmlDocumentData?,
) {

  constructor(data: NavigationXmlDocumentData) : this(null, null, data)

  constructor(sourceFile: SourceFile, rootElement: Element) : this(sourceFile, rootElement, null)

  private val parseSourceFile: SourceFile
    get() = checkNotNull(sourceFile)

  private sealed class ElementError(
    val sourceFilePosition: SourceFilePosition,
    val message: String,
  ) {
    // Each access throws a fresh exception so callers get an accurate stack trace.
    abstract fun createException(): RuntimeException

    class IncludeError(sourceFilePosition: SourceFilePosition, message: String) : ElementError(sourceFilePosition, message) {
      override fun createException(): RuntimeException = NavigationXmlDocumentException(message)
    }

    class DeepLinkError(sourceFilePosition: SourceFilePosition, message: String) : ElementError(sourceFilePosition, message) {
      override fun createException(): RuntimeException = NavigationXmlDocumentException(message)
    }

    class InvalidDeepLinkUriError(
      sourceFilePosition: SourceFilePosition,
      message: String,
      private val exceptionMessage: String,
      private val cause: Throwable?,
    ) : ElementError(sourceFilePosition, message) {
      override fun createException(): RuntimeException = DeepLinkException(exceptionMessage, cause)
    }
  }

  private class ParsedDocument(
    val navigationXmlIds: List<String>,
    val deepLinks: List<DeepLink>,
    val errors: List<ElementError> = emptyList(),
  ) {
    val firstIncludeError: ElementError.IncludeError?
      get() = errors.filterIsInstance<ElementError.IncludeError>().firstOrNull()

    val firstDeepLinkError: ElementError?
      get() = errors.firstOrNull { it !is ElementError.IncludeError }
  }

  private val parsedDocument: ParsedDocument by lazy {
    precomputedData?.let {
      return@lazy ParsedDocument(it.navigationXmlIds, it.deepLinks)
    }
    val navigationXmlIds = ArrayList<String>()
    val deepLinks = ArrayList<DeepLink>()
    val errors = ArrayList<ElementError>()
    collectElements(checkNotNull(rootElement), navigationXmlIds, deepLinks, errors)
    ParsedDocument(
      navigationXmlIds = ImmutableList.copyOf(navigationXmlIds),
      deepLinks = ImmutableList.copyOf(deepLinks),
      errors = ImmutableList.copyOf(errors),
    )
  }

  fun convertToData(manifestPlaceHolders: Map<String, String>, useUnknownSourceFilePosition: Boolean) =
    NavigationXmlDocumentData(name!!, navigationXmlIds, processDeepLinks(deepLinks, manifestPlaceHolders, useUnknownSourceFilePosition))

  /**
   * The list of navigation xml IDs found in this document, including duplicates. navigation xml IDs are found in <include> elements as
   * "app:graph" attribute values, trimmed of their "@navigation/" prefix.
   *
   * Throws a fresh [NavigationXmlDocumentException] for the first `<include>` validation error in this document.
   */
  val navigationXmlIds: List<String>
    get() {
      val parsed = parsedDocument
      parsed.firstIncludeError?.let { throw it.createException() }
      return parsed.navigationXmlIds
    }

  /**
   * The list of [DeepLink]s found in this document, including duplicates. [DeepLink]s are found in <deepLink> elements.
   *
   * Throws a fresh [NavigationXmlDocumentException] or [DeepLinkException] for the first `<deepLink>` validation error in this document
   * (including a blank `app:mimeType` even when `app:uri` is valid).
   */
  val deepLinks: List<DeepLink>
    get() {
      val parsed = parsedDocument
      parsed.firstDeepLinkError?.let { throw it.createException() }
      return parsed.deepLinks
    }

  /**
   * All valid navigation xml IDs found in `<include>` elements in this document, skipping any invalid `<include>` elements without
   * throwing. Use [appendElementErrorsTo] to record validation errors into a [MergingReport.Builder].
   */
  internal val validNavigationXmlIds: List<String>
    get() = parsedDocument.navigationXmlIds

  /**
   * All valid [DeepLink]s found in `<deepLink>` elements in this document, skipping any invalid `<deepLink>` elements without throwing. A
   * `<deepLink>` with any validation error (including a blank `app:mimeType` alongside an otherwise valid `app:uri`) is excluded. Use
   * [appendElementErrorsTo] to record validation errors into a [MergingReport.Builder].
   */
  internal val validDeepLinks: List<DeepLink>
    get() = parsedDocument.deepLinks

  /**
   * Appends all `<deepLink>` and `<include>` element validation errors in this document to [mergingReportBuilder] in XML document order.
   *
   * This method is not idempotent: every invocation appends all element errors in this document, so callers such as [NavGraphExpander] must
   * deduplicate per merge pass. Errors are recorded at each invalid element's position in this navigation XML file (including when reached
   * through a nested `<include>`). If a single `<deepLink>` element has multiple independent attribute errors (such as both a blank
   * `app:mimeType` and a missing or invalid `app:uri`), each error is recorded.
   *
   * @see deepLinks
   * @see navigationXmlIds
   */
  internal fun appendElementErrorsTo(mergingReportBuilder: MergingReport.Builder) {
    parsedDocument.errors.forEach {
      mergingReportBuilder.addMessage(
        it.sourceFilePosition,
        MergingReport.Record.Severity.ERROR,
        it.message,
      )
    }
  }

  val name: String? = precomputedData?.name ?: parseSourceFile.description

  /**
   * Single-pass recursive traversal collecting valid `<include>` navigation IDs, valid `<deepLink>` items, and all element-level validation
   * errors in XML document order.
   */
  private fun collectElements(
    element: Element,
    navigationXmlIds: MutableList<String>,
    deepLinks: MutableList<DeepLink>,
    errors: MutableList<ElementError>,
  ) {
    if (element.tagName == SdkConstants.TAG_INCLUDE) {
      val namedNodeMap: NamedNodeMap? = element.attributes
      val graphValue = namedNodeMap?.getNamedItemNS(SdkConstants.AUTO_URI, SdkConstants.ATTR_GRAPH)?.nodeValue
      if (graphValue != null && graphValue.startsWith(SdkConstants.NAVIGATION_PREFIX)) {
        navigationXmlIds.add(graphValue.substring(SdkConstants.NAVIGATION_PREFIX.length))
      } else {
        val nsUriPrefix = XmlUtils.lookupNamespacePrefix(element, SdkConstants.AUTO_URI, false)
        val graphName = nsUriPrefix + XmlUtils.NS_SEPARATOR + SdkConstants.ATTR_GRAPH
        val sourceFilePosition = SourceFilePosition(parseSourceFile, PositionXmlParser.getPosition(element))
        val message =
          "Navigation XML document <include> element must contain a $graphName " +
            "attribute, whose value begins " +
            "with ${SdkConstants.NAVIGATION_PREFIX}."
        errors.add(ElementError.IncludeError(sourceFilePosition, message))
      }
    } else if (element.tagName == SdkConstants.TAG_DEEP_LINK) {
      val namedNodeMap: NamedNodeMap? = element.attributes
      val deepLinkUri = namedNodeMap?.getNamedItemNS(SdkConstants.AUTO_URI, SdkConstants.ATTR_URI)?.nodeValue
      val autoVerifyAttribute = namedNodeMap?.getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_AUTO_VERIFY)
      val action = namedNodeMap?.getNamedItemNS(SdkConstants.AUTO_URI, SdkConstants.ATTR_DEEPLINK_ACTION)?.nodeValue
      val mimeType = namedNodeMap?.getNamedItemNS(SdkConstants.AUTO_URI, SdkConstants.ATTR_DEEPLINK_MIMETYPE)?.nodeValue
      val sourceFilePosition = SourceFilePosition(parseSourceFile, PositionXmlParser.getPosition(element))
      var hasBlankMimeType = false
      if (mimeType != null && mimeType.isBlank()) {
        val message = "Navigation XML document <deepLink> element ${SdkConstants.ATTR_DEEPLINK_MIMETYPE} cannot be blank."
        errors.add(ElementError.DeepLinkError(sourceFilePosition, message))
        hasBlankMimeType = true
      }
      val autoVerify = autoVerifyAttribute?.nodeValue == "true"
      val nsUriPrefix by lazy(LazyThreadSafetyMode.NONE) { XmlUtils.lookupNamespacePrefix(element, SdkConstants.AUTO_URI, false) }
      // Still parse the URI when mimeType is blank so independent URI errors are reported too.
      if (deepLinkUri != null) {
        try {
          val deepLink = DeepLink.fromUri(deepLinkUri, sourceFilePosition, autoVerify, action, mimeType)
          if (!hasBlankMimeType) {
            deepLinks.add(deepLink)
          }
        } catch (e: DeepLinkException) {
          val exceptionMessage = e.message ?: "Error reading navigation XML <deepLink> element."
          val reportMessage =
            if (e.cause is URISyntaxException) {
              val uriName = nsUriPrefix + XmlUtils.NS_SEPARATOR + SdkConstants.ATTR_URI
              "Navigation XML document <deepLink> element $uriName is invalid: $exceptionMessage"
            } else {
              exceptionMessage
            }
          errors.add(ElementError.InvalidDeepLinkUriError(sourceFilePosition, reportMessage, exceptionMessage, e.cause))
        }
      } else {
        val uriName = nsUriPrefix + XmlUtils.NS_SEPARATOR + SdkConstants.ATTR_URI
        val message = "Navigation XML document <deepLink> element must contain a $uriName attribute."
        errors.add(ElementError.DeepLinkError(sourceFilePosition, message))
      }
    }
    for (childNode in XmlUtils.getSubTagsAsList(element)) {
      collectElements(childNode, navigationXmlIds, deepLinks, errors)
    }
  }

  fun getXml() = rootElement

  /** An exception during the evaluation of a [NavigationXmlDocument]. */
  class NavigationXmlDocumentException(s: String) : RuntimeException(s)
}

private fun processDeepLinks(
  deepLinks: List<DeepLink>,
  manifestPlaceHolders: Map<String, String>,
  useUnknownSourceFilePosition: Boolean,
): List<DeepLink> {
  return deepLinks.map { deepLink ->
    DeepLink(
      deepLink.schemes.map { it.performPlaceholderSubstitution(manifestPlaceHolders) },
      deepLink.host?.performPlaceholderSubstitution(manifestPlaceHolders),
      deepLink.port,
      deepLink.path.performPlaceholderSubstitution(manifestPlaceHolders),
      deepLink.query,
      deepLink.fragment,
      if (useUnknownSourceFilePosition) {
        UNKNOWN
      } else {
        SourceFilePosition(SourceFile(deepLink.sourceFilePosition.file.sourceFile.name), deepLink.sourceFilePosition.position)
      },
      deepLink.isAutoVerify,
      deepLink.action,
      deepLink.mimeType,
    )
  }
}

private fun String.performPlaceholderSubstitution(manifestPlaceHolders: Map<String, String>): String {
  var result = this
  manifestPlaceHolders.forEach { result = result.replace("\${${it.key}}", it.value) }
  return result
}
