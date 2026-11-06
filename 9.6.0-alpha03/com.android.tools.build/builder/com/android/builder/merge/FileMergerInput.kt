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

package com.android.builder.merge

import java.io.InputStream

interface FileMergerInput : OpenableCloseable {

  /**
   * Obtains all OS-independent paths of all files that in this input, regardless of being changed or not.
   *
   * @return the paths, may be empty if the relative tree of this input is empty
   */
  fun getAllPaths(): Set<String>

  /**
   * Obtains the name of this input.
   *
   * @return the name
   */
  fun getName(): String

  /**
   * Opens a path for reading. This method should only be called when the input is open.
   *
   * @param path the path
   * @return the input stream that should be closed by the caller before [.close] is called
   */
  fun openPath(path: String): InputStream
}
