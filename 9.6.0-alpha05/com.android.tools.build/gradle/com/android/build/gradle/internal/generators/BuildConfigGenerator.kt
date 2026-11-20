/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.gradle.internal.generators

import com.android.build.api.variant.BuildConfigField
import com.android.builder.compiling.GeneratedCodeFileCreator
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.nio.charset.StandardCharsets

/**
 * Class able to generate a BuildConfig class in an Android project. The BuildConfig class contains constants related to the build target.
 */
class BuildConfigGenerator(buildConfigData: BuildConfigData) : GeneratedCodeFileCreator {
  private val genFolder: String = buildConfigData.outputPath.toString()
  private val namespace: String = buildConfigData.namespace
  private val fields: Map<String, BuildConfigField<out Serializable>> = buildConfigData.buildConfigFields

  /** Returns a File representing where the BuildConfig class will be. */
  override val folderPath = File(genFolder, namespace.replace('.', File.separatorChar))

  override val generatedFilePath = File(folderPath, BUILD_CONFIG_NAME)

  /** Generates the BuildConfig class. */
  @Throws(IOException::class)
  override fun generate() {
    if (!folderPath.isDirectory && !folderPath.mkdirs()) {
      throw RuntimeException("Failed to create " + folderPath.absolutePath)
    }
    generatedFilePath.bufferedWriter(StandardCharsets.UTF_8).use { out ->
      // Not Javadoc: javac 23+ reports a /** before `package` as dangling (b/567587949).
      out.write(FILE_HEADER)
      out.write("package $namespace;\n\n")
      out.write("public final class BuildConfig {\n")
      for ((key, value) in fields) {
        value.emit(key, out, namespace)
      }
      out.write("}\n")
    }
  }

  companion object {
    const val BUILD_CONFIG_NAME = "BuildConfig.java"
    private const val FILE_HEADER = "/*\n * Automatically generated file. DO NOT MODIFY\n */\n"
  }
}
