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
package com.android.ide.common.xml

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

private val XML =
  DocumentBuilderFactory.newInstance()
    .apply {
      // isXml() only needs well-formedness — never DTD semantics. Disable DTDs entirely.
      runCatching {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
      }
      isXIncludeAware = false
      isExpandEntityReferences = false
    }
    .newDocumentBuilder()

object XmlDetector {
  // TODO(b/235501148): Detect partial XML. See LintSyntaxHighlighter#tokenizeXml() for a starting
  //  point.
  fun isXml(text: String): Boolean {
    return try {
      XML.parse(InputSource(StringReader(text)))
      true
    } catch (_: Exception) {
      false
    }
  }
}
