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

package com.android.build.api.artifact.impl

import com.android.build.api.artifact.Artifact

internal class ArtifactTypeQualifiers(unsortedMap: Map<String, String>) {

  val value = unsortedMap.toSortedMap()

  override fun toString(): String {
    return StringBuffer()
      .also {
        it.append(super.toString())
        toString(it)
      }
      .toString()
  }

  fun toString(into: StringBuffer) {
    value.entries.joinTo(buffer = into, transform = { attribute -> attribute.key + "=" + attribute.value })
  }

  /** Provides path segments that can be used to construct a file path that is unique to this set of qualifiers. */
  fun toPath(into: MutableList<String>) {
    // the paths will contain the task name used to produce the artifact, when qualifiers are
    // provided, they will also be used to isolate the task output by appending Key1/Value1,
    // Key2/Value2 to the path...
    value.entries.forEach { attribute ->
      into.add(attribute.key)
      into.add(attribute.value)
    }
  }

  fun ensureAttributesCorrectness(type: Artifact.WithQualifiers) {
    // ensure provided attributes are within the declared keys.
    type.qualifierKeys?.let { declaredKeys ->
      val undeclaredKeys = value.keys.minus(declaredKeys.toSet())
      if (undeclaredKeys.isNotEmpty()) {
        throw RuntimeException(
          StringBuffer()
            .also {
              it.append("An artifact with qualifiers <")
              toString(it)
              it.append("> is using undeclared qualifier key(s) <")
              undeclaredKeys.joinTo(it)
              it.append(">,\npossible keys are <")
              declaredKeys.joinTo(it)
              it.append(">")
            }
            .toString()
        )
      }
    }
  }

  fun ensureAttributesUniqueness(artifactContainer: MultipleArtifactContainer<*>) {
    // ensure attributes uniqueness.
    artifactContainer.getImplWithAttributes(this)?.let { artifactResult ->
      throw RuntimeException(
        StringBuffer()
          .also {
            it.append("An artifact with qualifiers <")
            toString(it)
            it.append("> has already been added")
            artifactResult.producerName?.let { producerName -> it.append(" by Task named `$producerName`") }
          }
          .toString()
      )
    }
  }
}
