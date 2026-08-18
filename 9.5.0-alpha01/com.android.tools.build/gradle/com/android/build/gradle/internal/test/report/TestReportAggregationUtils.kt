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

@file:JvmName("TestReportAggregationUtils")

package com.android.build.gradle.internal.test.report

import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_SUITE_METADATA_MODULE_KEY
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_SUITE_METADATA_SUITE_KEY
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_SUITE_METADATA_TARGET_KEY
import com.android.build.gradle.tasks.TestSuiteTestTask.Companion.TEST_SUITE_METADATA_VARIANT_KEY
import com.android.utils.FileUtils
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.w3c.dom.Node

/**
 * Processes test results for aggregation by enriching XML files with suite metadata.
 *
 * This function orchestrates the copying and property injection of JUnit XML results. Enriched results are copied from [sourceDir] to the
 * directory specified by [xmlResultsDirectory].
 *
 * @param sourceDir The directory containing the raw XML test results from the test engine.
 * @param xmlResultsDirectory Property specifying where the enriched results should be written.
 * @param modulePath The Gradle project path (e.g., ":app").
 * @param variantName The name of the variant being tested.
 * @param suiteName The name of the test suite (e.g., "unitTest").
 * @param target The target platform or device description.
 * @param logger The logger to use for reporting issues during processing.
 */
fun processTestReportAggregation(
  sourceDir: File,
  xmlResultsDirectory: DirectoryProperty,
  modulePath: String,
  variantName: String,
  suiteName: String,
  target: String,
  logger: Logger,
) {
  if (!xmlResultsDirectory.isPresent) return

  val properties =
    mapOf(
      TEST_SUITE_METADATA_MODULE_KEY to modulePath,
      TEST_SUITE_METADATA_VARIANT_KEY to variantName,
      TEST_SUITE_METADATA_SUITE_KEY to suiteName,
      TEST_SUITE_METADATA_TARGET_KEY to target,
    )

  injectPropertiesToXmlResults(sourceDir, properties, xmlResultsDirectory.get().asFile, logger)
}

/**
 * Injects properties into all XML files in the given directory (recursively) and writes them to the outputDir.
 *
 * @param directory The directory containing the XML files to process.
 * @param properties The properties to inject into the XML files.
 * @param outputDir The directory where the processed XML files will be written.
 * @param logger The logger to use for warnings.
 */
private fun injectPropertiesToXmlResults(directory: File, properties: Map<String, String>, outputDir: File, logger: Logger) {
  if (!directory.exists()) return

  val hashString = calculateHash(properties)

  try {
    val docFactory = DocumentBuilderFactory.newInstance()
    val transformerFactory = TransformerFactory.newInstance()

    Files.walk(directory.toPath()).use { allPaths ->
      allPaths.forEach { sourcePath ->
        val sourceFile = sourcePath.toFile()
        val relativePath = directory.toPath().relativize(sourcePath)
        val targetPath = outputDir.toPath().resolve(relativePath)

        if (sourceFile.isDirectory) {
          Files.createDirectories(targetPath)
        } else if (sourceFile.name.endsWith(".xml", ignoreCase = true)) {
          val newFileName = "${sourceFile.nameWithoutExtension}_${hashString}.xml"
          val xmlTargetFile = targetPath.parent.resolve(newFileName).toFile()
          injectProperties(sourceFile, properties, xmlTargetFile, docFactory, transformerFactory, logger)
        } else {
          FileUtils.copyFile(sourceFile, targetPath.toFile())
        }
      }
    }
  } catch (e: IOException) {
    logger.warn("Failed to inject properties into XML test results: ${e.message}")
  }
}

private fun calculateHash(properties: Map<String, String>): String {
  val content = properties.entries.sortedBy { it.key }.joinToString("\n") { (key, value) -> "$key=$value" } + "\n"

  val digest = MessageDigest.getInstance("MD5")
  return BigInteger(1, digest.digest(content.toByteArray(StandardCharsets.UTF_8))).toString(16).padStart(32, '0')
}

/**
 * Injects properties into a single XML file and writes it to the targetFile.
 *
 * @param xmlFile The XML file to process.
 * @param properties The properties to inject into the XML file.
 * @param targetFile The file where the processed XML will be written.
 * @param docFactory The factory to use for creating DocumentBuilders.
 * @param transformerFactory The factory to use for creating Transformers.
 * @param logger The logger to use for warnings.
 */
private fun injectProperties(
  xmlFile: File,
  properties: Map<String, String>,
  targetFile: File,
  docFactory: DocumentBuilderFactory,
  transformerFactory: TransformerFactory,
  logger: Logger,
) {
  try {
    val docBuilder = docFactory.newDocumentBuilder()
    val document = docBuilder.parse(xmlFile)

    val propertiesList = document.getElementsByTagName("properties")

    val propertiesNode: Node
    if (propertiesList.length == 0) {
      propertiesNode = document.createElement("properties")
      document.documentElement.appendChild(propertiesNode)
    } else {
      propertiesNode = propertiesList.item(0)
    }
    properties.forEach { (key, value) ->
      val propertyElement = document.createElement("property")
      propertyElement.setAttribute("name", key)
      propertyElement.setAttribute("value", value)
      propertiesNode.appendChild(propertyElement)
    }

    val transformer = transformerFactory.newTransformer()
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")

    val source = DOMSource(document)
    targetFile.parentFile.mkdirs()
    val result = StreamResult(targetFile)

    transformer.transform(source, result)
  } catch (e: Exception) {
    logger.warn("Failed to inject properties into XML test results: ${e.message}")
    xmlFile.copyTo(targetFile, overwrite = true)
  }
}
