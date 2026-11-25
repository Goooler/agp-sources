/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.builder.model.v2.ide

import com.android.builder.model.v2.AndroidModel

/**
 * A target of a test suite: a context in which the tests of the suite run, such as a set of Gradle Managed Devices. Each target has its own
 * test task.
 *
 * Targets are declared in the DSL with `testOptions.suites.<suite>.targets.create("<name>")`.
 */
interface TestSuiteTarget : AndroidModel {
  /** The name of the target, as declared in the DSL (e.g. `tabletRun`). */
  val name: String

  /**
   * Returns the name of the task used to run the test suite
   *
   * @return name of the task used to run the tests.
   */
  val testTaskName: String

  /** The names of the Gradle Managed Devices or device groups the target runs on. Empty if the target doesn't use managed devices. */
  val targetedDevices: Collection<String>
}
