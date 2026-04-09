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

package com.android.build.gradle.internal.dependency

import com.android.SdkConstants
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath

/* Compresses Java resources (directory or JAR) into a single JAR. */
@CacheableTransform
abstract class JavaResCompressionTransform : TransformAction<GenericTransformParameters> {

  @get:Classpath @get:InputArtifact abstract val inputArtifact: Provider<FileSystemLocation>

  override fun transform(outputs: TransformOutputs) {
    val inputFile = inputArtifact.get().asFile
    val outputFile = outputs.file("${inputFile.nameWithoutExtension}${SdkConstants.DOT_JAR}")
    UncompressedJavaRes.Jar(inputFile).compressToJar(outputFile)
  }
}

/* Compresses Java resources from an exploded aar into a single JAR. */
@CacheableTransform
abstract class JavaResCompressionFromExplodedAarTransform : TransformAction<GenericTransformParameters> {

  @get:Classpath @get:InputArtifact abstract val inputArtifact: Provider<FileSystemLocation>

  override fun transform(outputs: TransformOutputs) {
    val explodedAarJars = getJars(inputArtifact.get().asFile)
    val outputFile = outputs.file("${inputArtifact.get().asFile.name}${SdkConstants.DOT_JAR}")
    UncompressedJavaRes.MultipleJars(explodedAarJars).compressToJar(outputFile)
  }
}
