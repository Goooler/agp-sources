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
import java.io.Closeable
import java.io.File
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

  /** Same parameters as runTraceReferenceTool in TraceReferenceTool.kt, so the implementation just forwards them. */
  fun runTraceReferenceTool(
    fullBootClasspath: List<Path>,
    desugaredDesugarLib: Collection<Path>,
    dexFiles: Collection<Path>,
    keepRuleOutput: Path,
  )

  /** Same parameters as runL8 in L8Tool.kt, so the implementation just forwards them. Unlike runL8, no parameter has a default value. */
  fun runL8(
    inputClasses: Collection<Path>,
    output: Path,
    libConfiguration: String,
    libraries: Collection<Path>,
    minSdkVersion: Int,
    keepRules: KeepRulesConfig,
    isDebuggable: Boolean,
    outputMode: L8OutputMode,
    inputArtProfile: Path?,
    outputArtProfile: Path?,
    inputMappingFile: Path?,
    outputMappingFile: Path?,
    inputPartitionMappingFile: Path?,
    outputPartitionMappingFile: Path?,
    tempDir: Path?,
  )

  /** Same parameters as D8DesugaredMethodsGenerator.generate, so the implementation just forwards them. */
  fun generateDesugaredMethods(coreLibDesugarConfig: String?, bootclasspath: Set<File>, minSdkVersion: Int): List<String>

  /**
   * Creates a provider of the classes in [paths] for D8 desugaring: a ClassFileProviderFactory from this implementation's ClassLoader.
   *
   * ClassFileProviderFactory uses R8 types, so it can't be shared with AGP's ClassLoader and is returned as a [Closeable]. Only pass it to
   * [createD8DexBuilder] of an [R8Tools] from the same ClassLoader, and close it after all dex builders using it are done.
   */
  fun createClassFileProviderFactory(paths: Collection<Path>): Closeable

  /**
   * Creates a D8 [DexArchiveBuilder]. The parameters are the same as DexParameters, except that [desugarBootclasspath] and
   * [desugarClasspath] must come from [createClassFileProviderFactory] of an [R8Tools] from the same ClassLoader.
   */
  fun createD8DexBuilder(
    minSdkVersion: Int,
    debuggable: Boolean,
    dexPerClass: Boolean,
    withDesugaring: Boolean,
    desugarBootclasspath: Closeable,
    desugarClasspath: Closeable,
    coreLibDesugarConfig: String?,
    enableApiModeling: Boolean,
    messageReceiver: MessageReceiver,
    useNoOpGlobalSyntheticsConsumer: Boolean,
  ): DexArchiveBuilder
}
