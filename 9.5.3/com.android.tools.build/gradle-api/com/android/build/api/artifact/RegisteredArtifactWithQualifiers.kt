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

package com.android.build.api.artifact

import org.gradle.api.Incubating
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider

/** Encapsulation of a registered [Provider] of [FileTypeT] with qualifiers. */
@Incubating
interface RegisteredArtifactWithQualifiers<FileTypeT : FileSystemLocation> {

  /** Qualifiers used when registering the artifact possibly in a different order. */
  @get:Incubating val qualifiers: Map<String, String>

  /** Registered artifact's [Provider]. */
  @get:Incubating val artifact: Provider<FileTypeT>
}
