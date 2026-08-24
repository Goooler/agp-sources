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
import com.android.build.gradle.internal.tasks.MergeJavaResourceTask
import com.android.zipflinger.Entry
import com.android.zipflinger.Sources
import com.android.zipflinger.StableArchive
import com.android.zipflinger.ZipArchive
import com.android.zipflinger.ZipSource
import java.io.File
import java.util.zip.Deflater

/** Provides utilities for compressing types representing Java Resources into Jar files for merging. */
sealed interface UncompressedJavaRes {

  fun compressToJar(outputFile: File): File

  class FileTree(val fileTree: org.gradle.api.file.FileTree) : UncompressedJavaRes {

    override fun compressToJar(outputFile: File): File {
      outputFile.delete()
      StableArchive(ZipArchive(outputFile.toPath())).use { compressedJavaResJar ->
        fileTree.visit { details ->
          if (!details.isDirectory) {
            compressedJavaResJar.add(Sources.from(details.file, details.relativePath.pathString, Deflater.DEFAULT_COMPRESSION))
          }
        }
      }
      return outputFile
    }
  }

  class Jar(val jar: File) : UncompressedJavaRes {

    private val source = ZipSource(jar.toPath())
    private val entries: Map<String, Entry> = source.entries()

    init {
      if (!jar.isFile || jar.extension != SdkConstants.EXT_JAR) {
        error("$jar must be a jar containing java resources.")
      }
    }

    override fun compressToJar(outputFile: File): File {
      outputFile.delete()
      StableArchive(ZipArchive(outputFile.toPath())).use { compressedJavaResJar ->
        entries.values.forEach { entry ->
          if (!entry.isDirectory && MergeJavaResourceTask.Companion.predicate.test(entry.name)) {
            source.select(entry.name, entry.name, Deflater.DEFAULT_COMPRESSION, 0L)
          }
        }
        compressedJavaResJar.add(source)
      }
      return outputFile
    }
  }

  class MultipleJars(val jars: List<File>) : UncompressedJavaRes {

    override fun compressToJar(outputFile: File): File {
      outputFile.delete()
      StableArchive(ZipArchive(outputFile.toPath())).use { compressedJavaResJar ->
        val addedEntries = mutableSetOf<String>()
        jars.forEach { jar ->
          val source = ZipSource(jar.toPath())
          source.entries().values.forEach { entry ->
            if (!entry.isDirectory && MergeJavaResourceTask.Companion.predicate.test(entry.name) && !addedEntries.contains(entry.name)) {
              source.select(entry.name, entry.name, Deflater.DEFAULT_COMPRESSION, 0L)
              addedEntries.add(entry.name)
            }
          }
          compressedJavaResJar.add(source)
        }
      }
      return outputFile
    }
  }
}
