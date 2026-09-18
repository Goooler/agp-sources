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
import com.android.builder.packaging.ParsedPackagingOptions
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input

/* Compresses Java resources (directory or JAR) into a single JAR. */
@CacheableTransform
abstract class JavaResCompressionTransform : TransformAction<JavaResCompressionTransform.Parameters> {

  interface Parameters : GenericTransformParameters {
    @get:Input val excludes: SetProperty<String>
    @get:Input val pickFirsts: SetProperty<String>
    @get:Input val merges: SetProperty<String>
  }

  @get:Classpath @get:InputArtifact abstract val inputArtifact: Provider<FileSystemLocation>

  override fun transform(outputs: TransformOutputs) {
    val inputFile = inputArtifact.get().asFile
    if (!inputFile.exists()) return
    val outputFile = outputs.file("${inputFile.nameWithoutExtension}${SdkConstants.DOT_JAR}")
    val packagingOptions = ParsedPackagingOptions(parameters.excludes.get(), parameters.pickFirsts.get(), parameters.merges.get())
    UncompressedJavaRes.Jar(inputFile, packagingOptions).compressToJar(outputFile)
  }
}

/* Compresses Java resources from an exploded aar into a single JAR. */
@CacheableTransform
abstract class JavaResCompressionFromExplodedAarTransform : TransformAction<JavaResCompressionTransform.Parameters> {

  @get:Classpath @get:InputArtifact abstract val inputArtifact: Provider<FileSystemLocation>

  override fun transform(outputs: TransformOutputs) {
    val explodedAarJars = getJars(inputArtifact.get().asFile)
    val outputFile = outputs.file("${inputArtifact.get().asFile.name}${SdkConstants.DOT_JAR}")
    val packagingOptions = ParsedPackagingOptions(parameters.excludes.get(), parameters.pickFirsts.get(), parameters.merges.get())
    UncompressedJavaRes.MultipleJars(explodedAarJars, packagingOptions).compressToJar(outputFile)
  }
}
