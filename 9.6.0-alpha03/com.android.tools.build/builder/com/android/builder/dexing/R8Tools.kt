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

package com.android.builder.dexing

import com.android.ide.common.blame.MessageReceiver
import java.nio.file.Path
import java.util.concurrent.ExecutorService

/**
 * Entry points into R8 that AGP can call across the isolated R8 ClassLoader boundary.
 *
 * This interface is loaded by AGP's own ClassLoader (see `R8ClassloaderBuildService`), while its implementation may be loaded next to a
 * different R8 version in the isolated ClassLoader. It must therefore only use types that come from AGP's ClassLoader: JDK types,
 * [MessageReceiver], and the R8-free config types such as [ToolConfig]. It must not reference `com.android.tools.r8.*`.
 */
interface R8Tools {
  /** The parameters are a 1:1 copy of runR8 in r8Tool.kt, same names and order, so the implementation just forwards them. */
  fun runR8(
    inputClasses: Collection<Path>,
    output: Path?,
    inputJavaResJar: Path,
    javaResourcesJar: Path?,
    libraries: Collection<Path>,
    classpath: Collection<Path>,
    toolConfig: ToolConfig,
    proguardConfig: ProguardConfig,
    mainDexListConfig: MainDexListConfig,
    resourceShrinkingConfig: ResourceShrinkingConfig?,
    messageReceiver: MessageReceiver,
    featureClassJars: Collection<Path>,
    featureJavaResourceJars: Collection<Path>,
    featureDexDir: Path?,
    featureJavaResourceOutputDir: Path?,
    libConfiguration: String?,
    inputArtProfile: Path?,
    outputArtProfile: Path?,
    inputProfileForDexStartupOptimization: Path?,
    r8Metadata: Path?,
    partialShrinking: PartialShrinking?,
    r8ExecutorService: ExecutorService?,
  )

  /** Same parameters as runTraceReferences in TraceReferences.kt, so the implementation just forwards them. */
  fun runTraceReferences(sourceFiles: List<Path>, libraryFiles: List<Path>, libraryInMemoryClasses: Map<String, ByteArray>)
}
