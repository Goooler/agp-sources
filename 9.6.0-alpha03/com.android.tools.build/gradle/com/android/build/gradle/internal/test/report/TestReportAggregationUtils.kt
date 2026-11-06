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
import com.android.utils.XmlUtils
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger

/**
 * Enriches raw JUnit XML test results in [sourceDir] with suite metadata into [xmlResultsDirectory].
 *
 * @param sourceDir Directory containing raw XML test results and sidecars from the test engine.
 * @param xmlResultsDirectory Destination directory property for enriched XML results and sidecars.
 * @param modulePath Gradle project path (for example, `":app"`).
 * @param variantName Name of the tested variant.
 * @param suiteName Name of the test suite (for example, `"unitTest"`).
 * @param target Target platform or device identifier.
 * @param logger Logger used to report processing warnings.
 * @param excludeFileFilter Returns true for result files that must not be aggregated.
 */
@JvmOverloads
fun processTestReportAggregation(
  sourceDir: File,
  xmlResultsDirectory: DirectoryProperty,
  modulePath: String,
  variantName: String,
  suiteName: String,
  target: String,
  logger: Logger,
  excludeFileFilter: (File) -> Boolean = { false },
) {
  if (!xmlResultsDirectory.isPresent) return

  val properties =
    mapOf(
      TEST_SUITE_METADATA_MODULE_KEY to modulePath,
      TEST_SUITE_METADATA_VARIANT_KEY to variantName,
      TEST_SUITE_METADATA_SUITE_KEY to suiteName,
      TEST_SUITE_METADATA_TARGET_KEY to target,
    )

  injectPropertiesToXmlResults(sourceDir, properties, xmlResultsDirectory.get().asFile, logger, excludeFileFilter)
}

/**
 * Removes results of previous runs from [xmlResultsDirectory].
 *
 * Tasks that do not clear outputs automatically call this before [processTestReportAggregation] so deleted test classes do not linger in
 * aggregated reports.
 */
fun clearTestReportAggregationResults(xmlResultsDirectory: DirectoryProperty) {
  if (xmlResultsDirectory.isPresent) {
    FileUtils.cleanOutputDir(xmlResultsDirectory.get().asFile)
  }
}

/**
 * Enriches JUnit XML files and namespaces suite sidecars from [directory] into [outputDir].
 *
 * Injects [properties] into `TEST-*.xml` files, hashes [SUITE_ENVIRONMENT_FILE_NAME] to `suite-environment_<hash>.json`, namespaces
 * [SUITE_DETAILS_DIR_NAME] files under `suite-details/<hash>/`, and copies remaining files verbatim.
 *
 * @param excludeFileFilter Returns true for result files that must not be aggregated.
 */
private fun injectPropertiesToXmlResults(
  directory: File,
  properties: Map<String, String>,
  outputDir: File,
  logger: Logger,
  excludeFileFilter: (File) -> Boolean,
) {
  if (!directory.exists()) return

  val hashString = calculateHash(properties)

  try {
    val docFactory = XmlUtils.createDocumentBuilderFactory()
    val transformerFactory = createSafeTransformerFactory()

    Files.walk(directory.toPath()).use { allPaths ->
      allPaths.forEach { sourcePath ->
        if (Files.isSymbolicLink(sourcePath)) return@forEach
        val sourceFile = sourcePath.toFile()
        if (sourceFile.isFile && excludeFileFilter(sourceFile)) return@forEach
        val relativePath = directory.toPath().relativize(sourcePath)
        val isUnderSuiteDetails = relativePath.nameCount > 0 && relativePath.getName(0).toString() == SUITE_DETAILS_DIR_NAME

        if (isUnderSuiteDetails) {
          val namespacedBase = outputDir.toPath().resolve(SUITE_DETAILS_DIR_NAME).resolve(hashString)
          val suiteDetailsTargetPath =
            if (relativePath.nameCount == 1) {
              namespacedBase
            } else {
              namespacedBase.resolve(relativePath.subpath(1, relativePath.nameCount))
            }
          if (sourceFile.isDirectory) {
            Files.createDirectories(suiteDetailsTargetPath)
          } else {
            FileUtils.copyFile(sourceFile, suiteDetailsTargetPath.toFile())
          }
        } else {
          val targetPath = outputDir.toPath().resolve(relativePath)

          if (sourceFile.isDirectory) {
            Files.createDirectories(targetPath)
          } else if (sourceFile.name.startsWith(JUNIT_XML_FILE_PREFIX) && sourceFile.name.endsWith(".xml", ignoreCase = true)) {
            val newFileName = "${sourceFile.nameWithoutExtension}_${hashString}.xml"
            val xmlTargetFile = targetPath.parent.resolve(newFileName).toFile()
            injectProperties(sourceFile, properties, xmlTargetFile, docFactory, transformerFactory, logger)
          } else if (sourceFile.name == SUITE_ENVIRONMENT_FILE_NAME) {
            val envTargetFile = targetPath.parent.resolve("${SUITE_ENVIRONMENT_FILE_PREFIX}${hashString}.json").toFile()
            FileUtils.copyFile(sourceFile, envTargetFile)
          } else {
            FileUtils.copyFile(sourceFile, targetPath.toFile())
          }
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

/** Injects [properties] into [xmlFile] and writes the updated XML document to [targetFile]. */
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

    val root = document.documentElement
    val suiteElements =
      when (root.tagName) {
        TAG_TESTSUITE -> listOf(root)
        TAG_TESTSUITES -> XmlUtils.getSubTagsAsList(root).filter { it.tagName == TAG_TESTSUITE }.ifEmpty { listOf(root) }
        else -> listOf(root)
      }

    for (suiteElement in suiteElements) {
      val propertiesNode =
        XmlUtils.getSubTagsAsList(suiteElement).firstOrNull { it.tagName == TAG_PROPERTIES }
          ?: document.createElement(TAG_PROPERTIES).also { created ->
            val firstChild = suiteElement.firstChild
            if (firstChild != null) {
              suiteElement.insertBefore(created, firstChild)
            } else {
              suiteElement.appendChild(created)
            }
          }
      properties.forEach { (key, value) ->
        val propertyElement = document.createElement(TAG_PROPERTY)
        propertyElement.setAttribute(ATTR_NAME, key)
        propertyElement.setAttribute(ATTR_VALUE, value)
        propertiesNode.appendChild(propertyElement)
      }
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

private fun createSafeTransformerFactory(): TransformerFactory =
  TransformerFactory.newInstance().apply {
    runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
    runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
    runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "") }
  }
