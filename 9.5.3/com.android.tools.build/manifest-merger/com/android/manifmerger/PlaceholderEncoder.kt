/*
 * Copyright (C) 2014 The Android Open Source Project
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

import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

/** encode all non resolved placeholders key names. */
object PlaceholderEncoder {
  /**
   * Iterate through each attribute for a placeholder existence. If one is found, encode its name so tools like aapt will not object invalid
   * characters and such.
   *
   * @param node node to visit attributes on
   * @return true if node was changed. False otherwise.
   */
  @JvmStatic
  fun encode(node: Node): Boolean {
    if (node is Element) {
      var changeFlag = false
      val elementAttributes = node.attributes
      for (i in 0 until elementAttributes.length) {
        val attribute = elementAttributes.item(i) as Attr
        changeFlag = changeFlag or handleAttribute(attribute)
      }
      return changeFlag
    }
    return false
  }

  /**
   * Handles an XML attribute, by substituting placeholders to an AAPT friendly encoding.
   *
   * @param attr attribute potentially containing a placeholder.
   * @return true if attribute was changed.
   */
  private fun handleAttribute(attr: Attr): Boolean {
    val matcher = PlaceholderHandler.PATTERN.matcher(attr.value)
    if (matcher.matches()) {
      var maybeSlash = ""
      // Ensure path attribute values start with "/" (b/316057932)
      if (matcher.group(1).isEmpty() && "path" == attr.localName) {
        maybeSlash = "/"
      }
      val encodedValue = "${matcher.group(1)}${maybeSlash}dollar_openBracket_${matcher.group(2)}_closeBracket${matcher.group(3)}"
      attr.value = encodedValue
      return true
    }
    return false
  }
}
