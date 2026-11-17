/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.coverage.renderer.utils

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import org.gradle.api.file.DirectoryProperty

/** A helper for copying resources needed for the HTML coverage and test reports. */

/**
 * Copies all specified resource files to the report output directory.
 *
 * @param reportOutputDir The directory where the resources will be copied.
 * @param resourceFiles A list of resource file names to copy.
 */
fun copyResources(reportOutputDir: DirectoryProperty, resourceFiles: List<String>, contextClass: Class<*>) {
  resourceFiles.forEach { resourceFile -> copyResource(resourceFile, reportOutputDir, contextClass) }
}

/**
 * Copies a single resource file to the report output directory.
 *
 * This function will create the necessary parent directories for the output file. The resource is loaded from the classpath.
 *
 * @param resourceName The name of the resource to copy.
 * @param reportOutputDir The directory where the resource will be copied.
 * @throws IOException if the resource is not found or if the file cannot be written.
 */
fun copyResource(resourceName: String, reportOutputDir: DirectoryProperty, contextClass: Class<*>) {
  openResourceStream(resourceName, contextClass).use { input ->
    val outputFile = File(reportOutputDir.get().asFile, resourceName)
    val parentDir = outputFile.parentFile

    parentDir.mkdirs()
    if (!parentDir.isDirectory) {
      throw IOException("Cannot create directory '$parentDir'.")
    }

    FileOutputStream(outputFile).use { output -> input.copyTo(output) }
  }
}

/**
 * Opens the resource [resourceName] of [contextClass] without the JVM-wide cache of open JAR files. See [openUncachedStream].
 *
 * @throws IOException if the resource is not found.
 */
internal fun openResourceStream(resourceName: String, contextClass: Class<*>): InputStream {
  val url = contextClass.getResource(resourceName) ?: throw IOException("Could not find resource '$resourceName'.")
  return openUncachedStream(url)
}

/**
 * Opens [url] without the JVM-wide cache of open JAR files.
 *
 * [Class.getResourceAsStream] reads JAR resources through that cache, so several class loaders of the same JAR share one open JarFile. When
 * one of them is closed, it closes the shared JarFile too, and reads in the other class loaders fail with "Stream closed". This happens
 * when Gradle closes the isolated class loader of a finished worker action while another action, such as the coverage report of another
 * variant, is still copying its resources.
 */
fun openUncachedStream(url: URL): InputStream = url.openConnection().apply { useCaches = false }.getInputStream()
