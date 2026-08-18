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

import com.android.utils.SdkUtils

/**
 * Defines node operation types as it can be provided by user's through attributes on the target xml element.
 *
 * Example:
 * <pre>`<activity android:name="com.foo.bar.ActivityUI"          tools:node="remove_children">     </activity> `</pre>
 */
enum class NodeOperationType(
  /** Returns true if this operation supports a [Selector] */
  val isSelectable: Boolean
) : ConvertibleName {
  /** Merges further definitions of the same element with this one. */
  MERGE(false),

  /**
   * Remove all children from the target element before merging it into the resulting merged manifest. This basically merges attributes only
   * (attributes annotation still applies).
   */
  MERGE_ONLY_ATTRIBUTES(false),

  /**
   * Replace further definitions of the same element with this one. There can be 0..n similar elements replaced with the annotated xml
   * element.
   */
  REPLACE(false),

  /**
   * Remove the next definition of the same element from the resulting merged manifest. There can be only one similar element removed. If
   * further definition are encountered, a merging failure will be initiated.
   */
  REMOVE(true),

  /** Remove all definitions of the same element from the resulting merged manifest. */
  REMOVE_ALL(true),

  /**
   * Remove all children from the target element before merging it into the resulting merged manifest. This basically merges all attributes
   * only (attributes annotation still applies).
   */
  REMOVE_CHILDREN(false),

  /** No further definition of this element should be encountered. A merging tool failure will be generated if there is one. */
  STRICT(false);

  override fun toXmlName(): String {
    return SdkUtils.constantNameToXmlName(name)
  }

  override fun toCamelCaseName(): String {
    return SdkUtils.constantNameToCamelCase(name)
  }

  /** Returns true if the element will override (remove or replace) lower priority elements. */
  val isOverriding: Boolean
    get() = this == REMOVE || this == REMOVE_ALL || this == REPLACE

  companion object {
    /** Local xml name of node operation types. */
    const val NODE_LOCAL_NAME: String = "node" // $NON-NLS-1$

    /** local xml name for overriding uses-sdk operation types. */
    const val OVERRIDE_USES_SDK: String = "overrideLibrary"

    /**
     * Flag that indicate to Play Store that the annotated element is required when running in the compatibility mode of the Privacy Sandbox
     * runtime.
     */
    const val REQUIRED_BY_PRIVACY_SANDBOX_SDK_ATTRIBUTE_NAME: String = "requiredByPrivacySandboxSdk"
  }
}
