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

import com.android.build.api.artifact.RegisteredArtifactWithQualifiers
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider

internal class RegisteredArtifactWithQualifiersImpl<FileTypeT : FileSystemLocation>(
  val producerName: String,
  private val typeAttributes: ArtifactTypeQualifiers,
  override val artifact: Provider<FileTypeT>,
) : RegisteredArtifactWithQualifiers<FileTypeT> {

  override val qualifiers: Map<String, String>
    get() = typeAttributes.value

  override fun toString(): String {
    return StringBuffer()
      .also {
        it.append("RegisteredArtifactWithQualifiers@${hashCode()}")
        it.append(' ')
        toString(it)
      }
      .toString()
  }

  fun toString(buffer: StringBuffer) {
    buffer.append("\nwith qualifiers : <")
    buffer.append(typeAttributes.toString(buffer))
    buffer.append(">")
    buffer.append("\nproduced by Task `$producerName`")
    buffer.append("\nartifact : $artifact")
  }
}
