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

package com.android.build.api.dsl

import org.gradle.api.Incubating
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.artifacts.dsl.GradleDependencies

@Incubating
/** @suppress */
interface DependenciesExtension : GradleDependencies {
  // main configurations
  @get:Incubating val api: DependencyCollector
  @get:Incubating val implementation: DependencyCollector

  // test configurations
  @get:Incubating val testImplementation: DependencyCollector

  // android test configurations
  @get:Incubating val androidTestImplementation: DependencyCollector
}
