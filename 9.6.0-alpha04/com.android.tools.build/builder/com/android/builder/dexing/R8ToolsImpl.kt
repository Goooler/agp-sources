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

import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.ide.common.blame.MessageReceiver
import java.io.Closeable
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ExecutorService

/**
 * [R8Tools] implementation that calls R8 directly.
 *
 * It runs whichever R8 its ClassLoader sees: the R8 bundled with AGP when loaded by AGP's ClassLoader, or a Maven-resolved R8 when loaded
 * child-first by the isolated R8 ClassLoader. It is created reflectively by name, so it needs a public no-arg constructor.
 */
class R8ToolsImpl : R8Tools {
  override fun runR8(
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
  ) {
    // Fully qualified: an unqualified runR8(...) here would resolve to this member function and recurse.
    com.android.builder.dexing.runR8(
      inputClasses,
      output,
      inputJavaResJar,
      javaResourcesJar,
      libraries,
      classpath,
      toolConfig,
      proguardConfig,
      mainDexListConfig,
      resourceShrinkingConfig,
      messageReceiver,
      featureClassJars,
      featureJavaResourceJars,
      featureDexDir,
      featureJavaResourceOutputDir,
      libConfiguration,
      inputArtProfile,
      outputArtProfile,
      inputProfileForDexStartupOptimization,
      r8Metadata,
      partialShrinking,
      r8ExecutorService,
    )
  }

  override fun runTraceReferences(sourceFiles: List<Path>, libraryFiles: List<Path>, libraryInMemoryClasses: Map<String, ByteArray>) {
    // Fully qualified for the same reason as runR8 above.
    com.android.builder.dexing.runTraceReferences(sourceFiles, libraryFiles, libraryInMemoryClasses)
  }

  override fun runTraceReferenceTool(
    fullBootClasspath: List<Path>,
    desugaredDesugarLib: Collection<Path>,
    dexFiles: Collection<Path>,
    keepRuleOutput: Path,
  ) {
    // Fully qualified for the same reason as runR8 above.
    com.android.builder.dexing.runTraceReferenceTool(fullBootClasspath, desugaredDesugarLib, dexFiles, keepRuleOutput)
  }

  override fun runL8(
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
  ) {
    // Fully qualified for the same reason as runR8 above.
    com.android.builder.dexing.runL8(
      inputClasses,
      output,
      libConfiguration,
      libraries,
      minSdkVersion,
      keepRules,
      isDebuggable,
      outputMode,
      inputArtProfile,
      outputArtProfile,
      inputMappingFile,
      outputMappingFile,
      inputPartitionMappingFile,
      outputPartitionMappingFile,
      tempDir,
    )
  }

  override fun generateDesugaredMethods(coreLibDesugarConfig: String?, bootclasspath: Set<File>, minSdkVersion: Int): List<String> =
    D8DesugaredMethodsGenerator.generate(coreLibDesugarConfig, bootclasspath, minSdkVersion)

  override fun createClassFileProviderFactory(paths: Collection<Path>): Closeable = ClassFileProviderFactory(paths)

  override fun createD8DexBuilder(
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
  ): DexArchiveBuilder =
    // Not DexArchiveBuilder.createD8DexBuilder: DexArchiveBuilder is loaded by AGP's ClassLoader, so its factory would create AGP's own
    // D8DexArchiveBuilder, which can't take the DexParameters from this ClassLoader.
    D8DexArchiveBuilder(
      DexParameters(
        minSdkVersion = minSdkVersion,
        debuggable = debuggable,
        dexPerClass = dexPerClass,
        withDesugaring = withDesugaring,
        desugarBootclasspath = desugarBootclasspath.asClassFileProviderFactory(),
        desugarClasspath = desugarClasspath.asClassFileProviderFactory(),
        coreLibDesugarConfig = coreLibDesugarConfig,
        enableApiModeling = enableApiModeling,
        messageReceiver = messageReceiver,
        useNoOpGlobalSyntheticsConsumer = useNoOpGlobalSyntheticsConsumer,
      )
    )

  private fun Closeable.asClassFileProviderFactory(): ClassFileProviderFactory =
    this as? ClassFileProviderFactory
      ?: throw IllegalArgumentException(
        "Expected a class path provider created by createClassFileProviderFactory in the same ClassLoader, but got ${javaClass.name} " +
          "from ${javaClass.classLoader}"
      )
}
