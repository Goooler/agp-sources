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

package com.android.build.api.dsl

/** DSL object for configuring Android resource options for Library plugins. This is accessed via [LibraryExtension.androidResources] */
interface LibraryAndroidResources : AndroidResources {
  /**
   * Flag to enable Android resource processing in this library module Default value is 'true' for plain android libraries and 'false' for
   * multiplatform libraries.
   */
  var enable: Boolean

  /**
   * Specifies this project's resource prefix to Android Studio for editor features, such as Lint checks. This property is useful only when
   * using Android Studio.
   *
   * Including unique prefixes for project resources helps avoid naming collisions with resources from other projects.
   *
   * For example, when creating a library with String resources, you may want to name each resource with a unique prefix, such as "`mylib_`"
   * to avoid naming collisions with similar resources that the consumer defines.
   *
   * You can then specify this prefix, as shown below, so that Android Studio expects this prefix when you name project resources:
   * ```
   * // This property is useful only when developing your project in Android Studio.
   * resourcePrefix = "mylib_"
   * ```
   */
  var resourcePrefix: String
}
