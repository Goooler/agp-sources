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
   * Specifies a resource name prefix required for all resources defined in this module.
   *
   * This setting is used by Android Studio editor features and Android Lint checks (the `ResourceName` detector) to ensure that all
   * resources in this module adhere to the prefix convention.
   *
   * This property does not automatically prepend the prefix to resources at build time; rather, it validates and enforces that
   * developer-defined resource names begin with the prefix, flagging violations during IDE editing and command-line `./gradlew lint` runs.
   *
   * Including unique prefixes for module resources helps avoid naming collisions when resources from multiple libraries are merged into a
   * single application namespace.
   *
   * When specifying a prefix:
   * - It is recommended to include a trailing underscore (e.g. `"mylib_"`).
   * - XML and file resource names must start with the snake_case prefix (e.g., `mylib_header_icon.xml`, `@string/mylib_button_label`).
   * - Styleables and attributes can use the camelCase equivalent (e.g., `<declare-styleable name="MyLibView">` or `myLibAttribute`).
   *
   * You can specify this prefix as shown below:
   * ```
   * android {
   *     androidResources {
   *         resourcePrefix = "mylib_"
   *     }
   * }
   * ```
   */
  var resourcePrefix: String
}
