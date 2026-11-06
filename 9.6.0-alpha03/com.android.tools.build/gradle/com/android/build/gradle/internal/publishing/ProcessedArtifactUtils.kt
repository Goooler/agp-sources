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
@file:JvmName("ProcessedArtifactUtils")

package com.android.build.gradle.internal.publishing

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions

/**
 * Designates which artifacts are to be used by consumers according to whether AAR/JAR processors are enabled.
 *
 * Currently only considers jetifier as a processor.
 *
 * TODO(b/288263843): Pull more logic around artifact types such as JACOCO_ASM_INSTRUMENTED_JARS here and consider renaming the intermediate
 *   types used below.
 */
class AarOrJarTypeToConsume(val aar: ArtifactType, val jar: ArtifactType)

/** Returns the appropriate artifact type to use according to whether jetifier is enabled. */
fun getAarOrJarTypeToConsume(projectOptions: ProjectOptions) =
  // The logic here should be kept in sync with the logic at DependencyConfigurator
  // We don't need to take into account the Dagger plugin existence, because it's only
  // provided so that the Dagger plugin keep working, we do the right thing here regardless.
  if (projectOptions[BooleanOption.ENABLE_JETIFIER]) {
    // When jetifier is enabled consumers should use the artifacts processed by it.
    AarOrJarTypeToConsume(aar = ArtifactType.PROCESSED_AAR, jar = ArtifactType.PROCESSED_JAR)
  } else {
    // In any other case, consumers should use the original artifact directly.
    AarOrJarTypeToConsume(aar = ArtifactType.AAR, jar = ArtifactType.JAR)
  }
