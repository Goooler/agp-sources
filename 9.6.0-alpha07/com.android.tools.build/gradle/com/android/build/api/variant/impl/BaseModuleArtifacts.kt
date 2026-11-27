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

package com.android.build.api.variant.impl

/**
 * Returns the single artifact published by the base application module and consumed by a dynamic feature module (e.g. base module metadata
 * or feature set metadata), failing with an actionable message when the base module cannot be found on the dynamic feature's compile
 * classpath, or when more than one is found.
 *
 * A dynamic feature only receives these artifacts if it declares a dependency on the base application module (e.g.
 * `implementation(project(":app"))`). Without it, the artifact collection is empty.
 *
 * Only Strings must be passed (no Project / services) so this can be safely called from within lazy provider transformations.
 *
 * @param files the resolved artifact files
 * @param featurePath the Gradle path of the dynamic feature module, e.g. `:feature`
 * @param variantName the name of the dynamic feature variant
 * @param artifactDescription human-readable description of the artifact, used in error messages
 */
internal fun <T> requireSingleBaseModuleArtifact(
  files: Collection<T>,
  featurePath: String,
  variantName: String,
  artifactDescription: String,
): T {
  return when (files.size) {
    1 -> files.first()
    0 ->
      throw RuntimeException(
        "Dynamic feature module '$featurePath' (variant '$variantName') could not find a base application module on its compile " +
          "classpath, so its $artifactDescription cannot be determined.\n" +
          "Add a dependency from the dynamic feature module to the base application module, e.g. " +
          "`implementation(project(\":app\"))` in the build file of '$featurePath', and list '$featurePath' in the base module's " +
          "`android.dynamicFeatures`.\n" +
          "The dependency must go from the dynamic feature to the base application module (not the reverse) and must be on the " +
          "compile classpath (not runtimeOnly). If the dependency is already declared, check that variant '$variantName' can be " +
          "matched to a variant of the base application module (product flavors and build types)."
      )
    else ->
      throw RuntimeException(
        "Dynamic feature module '$featurePath' (variant '$variantName') found ${files.size} base application modules providing the " +
          "$artifactDescription on its compile classpath (${files.joinToString()}); a dynamic feature module must depend on " +
          "exactly one base application module."
      )
  }
}
