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

import com.android.annotations.concurrency.Immutable

/** Represents a selector to be able to identify manifest file xml elements. */
@Immutable
class Selector(private val commaSeparatedPackageNames: String) {
  private val packages: List<String> = commaSeparatedPackageNames.split(",").dropLastWhile { it.isEmpty() }

  /**
   * Returns true if the passed element is "selected" by this selector. If so, any action this selector decorated will be applied to the
   * element.
   */
  fun appliesTo(element: XmlElement): Boolean {
    val packageName = element.document.getPackage().orElse(null)?.value ?: return false
    return packageName in packages
  }

  /** Returns true if the passed resolver can resolve this selector, false otherwise. */
  fun isResolvable(resolver: KeyResolver<String>): Boolean {
    return packages.all { resolver.resolve(it) != null }
  }

  override fun toString(): String {
    return commaSeparatedPackageNames
  }

  companion object {
    /** local name for tools:selector attributes. */
    const val SELECTOR_LOCAL_NAME: String = "selector"
  }
}
