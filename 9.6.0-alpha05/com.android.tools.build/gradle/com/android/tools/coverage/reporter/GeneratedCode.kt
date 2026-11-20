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

package com.android.tools.coverage.reporter

/**
 * The code that annotations mark as generated in the class files of the project, which the report leaves out.
 *
 * @param classes Internal names, such as `com/example/Foo`, of the classes whose code is generated.
 * @param methods Methods whose code is generated, as the internal name of their class, a dot, their name and their descriptor, such as
 *   `com/example/Foo.bar()V`.
 */
data class GeneratedCode(val classes: Set<String> = emptySet(), val methods: Set<String> = emptySet()) {

  /** Whether the code of the method [name] with the [descriptor] in the class [className] is generated. */
  fun contains(className: String, name: String, descriptor: String): Boolean =
    className in classes || (methods.isNotEmpty() && "$className.$name$descriptor" in methods)
}
