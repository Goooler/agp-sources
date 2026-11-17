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

import com.android.SdkConstants
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
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.process.CommandLineArgumentProvider
import org.w3c.dom.Element

internal const val JUNIT_PLATFORM_REPORTING_OUTPUT_DIR_PROPERTY = "junit.platform.reporting.output.dir"

internal class JunitAttachmentsArgumentProvider(
  @get:Internal val attachmentsDir: Provider<Directory>,
  @get:Internal val suppressArgument: Provider<Boolean>? = null,
) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> =
    if (suppressArgument?.getOrElse(false) == true) {
      emptyList()
    } else {
      listOf("-D$JUNIT_PLATFORM_REPORTING_OUTPUT_DIR_PROPERTY=${attachmentsDir.get().asFile.absolutePath}")
    }
}

/**
 * Enriches raw JUnit XML test results in [sourceDir] with suite metadata and collects published file attachments into
 * [xmlResultsDirectory].
 *
 * Attachments are copied for every test, but the HTML report shows them only for tests that also publish `androidx.report.*` entries.
 *
 * @param sourceDir Directory containing raw XML test results from the test engine.
 * @param xmlResultsDirectory Destination directory property for enriched XML results and copied attachments.
 * @param modulePath Gradle project path (for example, `":app"`).
 * @param variantName Name of the tested variant.
 * @param suiteName Name of the test suite (for example, `"unitTest"`).
 * @param target Target platform or device identifier.
 * @param logger Logger used to report processing warnings.
 * @param excludeFileFilter Returns true for result files that must not be aggregated.
 * @param additionalProperties Optional suite-level metadata injected into `<testsuite><properties>` without altering the result file hash.
 * @param allowedAttachmentRoot Optional directory from which JUnit `[[ATTACHMENT|...]]` files may be copied into `attachments/<hash>/`.
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
  additionalProperties: Map<String, String> = emptyMap(),
  allowedAttachmentRoot: File? = null,
) {
  if (!xmlResultsDirectory.isPresent) return

  val routingProperties =
    mapOf(
      TEST_SUITE_METADATA_MODULE_KEY to modulePath,
      TEST_SUITE_METADATA_VARIANT_KEY to variantName,
      TEST_SUITE_METADATA_SUITE_KEY to suiteName,
      TEST_SUITE_METADATA_TARGET_KEY to target,
    )

  injectPropertiesToXmlResults(
    directory = sourceDir,
    routingProperties = routingProperties,
    additionalProperties = additionalProperties,
    outputDir = xmlResultsDirectory.get().asFile,
    logger = logger,
    excludeFileFilter = excludeFileFilter,
    allowedAttachmentRoot = allowedAttachmentRoot,
  )
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
 * Enriches `TEST-*.xml` files from [directory] into [outputDir], copying validated JUnit attachments under `attachments/<hash>/` and
 * copying remaining files verbatim.
 */
private fun injectPropertiesToXmlResults(
  directory: File,
  routingProperties: Map<String, String>,
  additionalProperties: Map<String, String>,
  outputDir: File,
  logger: Logger,
  excludeFileFilter: (File) -> Boolean,
  allowedAttachmentRoot: File?,
) {
  if (!directory.exists()) return

  val hashString = calculateHash(routingProperties)
  val allProperties = if (additionalProperties.isEmpty()) routingProperties else additionalProperties + routingProperties
  val existingMaxSlot =
    outputDir.resolve(ATTACHMENTS_DIR_NAME).resolve(hashString).listFiles()?.mapNotNull { it.name.toIntOrNull() }?.maxOrNull() ?: 0
  val attachmentSlotCounter = intArrayOf(existingMaxSlot)

  try {
    val docFactory = XmlUtils.createDocumentBuilderFactory()
    val transformerFactory = createSafeTransformerFactory()

    Files.walk(directory.toPath()).use { allPaths ->
      allPaths.forEach { sourcePath ->
        if (Files.isSymbolicLink(sourcePath)) return@forEach
        val sourceFile = sourcePath.toFile()
        if (sourceFile.isFile && excludeFileFilter(sourceFile)) return@forEach
        val relativePath = directory.toPath().relativize(sourcePath)
        val targetPath = outputDir.toPath().resolve(relativePath)

        if (sourceFile.isDirectory) {
          Files.createDirectories(targetPath)
        } else if (sourceFile.name.startsWith(JUNIT_XML_FILE_PREFIX) && sourceFile.name.endsWith(".xml", ignoreCase = true)) {
          val newFileName = "${sourceFile.nameWithoutExtension}_${hashString}.xml"
          val xmlTargetFile = targetPath.parent.resolve(newFileName).toFile()
          injectProperties(
            xmlFile = sourceFile,
            properties = allProperties,
            outputDir = outputDir,
            hashString = hashString,
            attachmentSlotCounter = attachmentSlotCounter,
            allowedAttachmentRoot = allowedAttachmentRoot,
            targetFile = xmlTargetFile,
            docFactory = docFactory,
            transformerFactory = transformerFactory,
            logger = logger,
          )
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

/** Injects [properties] and rewrites validated `<system-out>` attachment markers in [xmlFile], writing the result to [targetFile]. */
private fun injectProperties(
  xmlFile: File,
  properties: Map<String, String>,
  outputDir: File,
  hashString: String,
  attachmentSlotCounter: IntArray,
  allowedAttachmentRoot: File?,
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
      val existingByName =
        XmlUtils.getSubTagsAsList(propertiesNode).filter { it.tagName == TAG_PROPERTY }.associateBy { it.getAttribute(ATTR_NAME) }
      properties.forEach { (key, value) ->
        val existingElement = existingByName[key]
        if (existingElement != null) {
          existingElement.setAttribute(ATTR_VALUE, value)
        } else {
          val propertyElement = document.createElement(TAG_PROPERTY)
          propertyElement.setAttribute(ATTR_NAME, key)
          propertyElement.setAttribute(ATTR_VALUE, value)
          propertiesNode.appendChild(propertyElement)
        }
      }

      rewriteSuiteElementAttachments(
        suiteElement = suiteElement,
        xmlFile = xmlFile,
        outputDir = outputDir,
        hashString = hashString,
        attachmentSlotCounter = attachmentSlotCounter,
        allowedAttachmentRoot = allowedAttachmentRoot,
        logger = logger,
      )
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

private fun rewriteSuiteElementAttachments(
  suiteElement: Element,
  xmlFile: File,
  outputDir: File,
  hashString: String,
  attachmentSlotCounter: IntArray,
  allowedAttachmentRoot: File?,
  logger: Logger,
) {
  for (child in XmlUtils.getSubTagsAsList(suiteElement)) {
    when (child.tagName) {
      TAG_SYSTEM_OUT ->
        rewriteSystemOutAttachments(child, xmlFile, outputDir, hashString, attachmentSlotCounter, allowedAttachmentRoot, logger)
      TAG_TESTCASE ->
        for (tcChild in XmlUtils.getSubTagsAsList(child)) {
          if (tcChild.tagName == TAG_SYSTEM_OUT) {
            rewriteSystemOutAttachments(tcChild, xmlFile, outputDir, hashString, attachmentSlotCounter, allowedAttachmentRoot, logger)
          }
        }
      TAG_TESTSUITE ->
        rewriteSuiteElementAttachments(child, xmlFile, outputDir, hashString, attachmentSlotCounter, allowedAttachmentRoot, logger)
    }
  }
}

private fun rewriteSystemOutAttachments(
  systemOutElement: Element,
  xmlFile: File,
  outputDir: File,
  hashString: String,
  attachmentSlotCounter: IntArray,
  allowedAttachmentRoot: File?,
  logger: Logger,
) {
  val rawText = systemOutElement.textContent ?: return
  if (!rawText.contains(ATTACHMENT_MARKER_PREFIX)) return

  val rewritten = StringBuilder(rawText.length)
  var mutated = false
  var index = 0
  while (index < rawText.length) {
    val newlineIndex = rawText.indexOf('\n', index)
    val lineEnd = if (newlineIndex == -1) rawText.length else newlineIndex
    val hasCr = lineEnd > index && rawText[lineEnd - 1] == '\r'
    val contentEnd = if (hasCr) lineEnd - 1 else lineEnd
    val line = rawText.substring(index, contentEnd)
    val lineEnding =
      when {
        newlineIndex == -1 -> ""
        hasCr -> "\r\n"
        else -> "\n"
      }

    val trimmed = line.trim()
    if (trimmed.startsWith(ATTACHMENT_MARKER_PREFIX) && trimmed.endsWith(ATTACHMENT_MARKER_SUFFIX)) {
      mutated = true
      val rawMarkerPath = trimmed.substring(ATTACHMENT_MARKER_PREFIX.length, trimmed.length - ATTACHMENT_MARKER_SUFFIX.length).trim()
      val validatedFile =
        PathSafetyUtils.resolveAllowedAttachmentFile(rawMarkerPath, xmlFile.parentFile, allowedAttachmentRoot, logger::warn)
      if (validatedFile != null) {
        val slot = ++attachmentSlotCounter[0]
        val safeName = PathSafetyUtils.sanitizeFileName(validatedFile.name, "attachment.bin")
        val relativeDestPath = "$ATTACHMENTS_DIR_NAME/$hashString/$slot/$safeName"
        val destFile = outputDir.resolve(relativeDestPath)
        try {
          Files.createDirectories(destFile.parentFile.toPath())
          FileUtils.copyFile(validatedFile, destFile)
          rewritten.append(ATTACHMENT_MARKER_PREFIX).append(relativeDestPath).append(ATTACHMENT_MARKER_SUFFIX).append(lineEnding)
        } catch (e: IOException) {
          // Only this attachment is dropped; the test results in the file are still reported.
          logger.warn("Failed to copy test attachment '$rawMarkerPath': ${e.message}")
        }
      }
    } else {
      rewritten.append(line).append(lineEnding)
    }

    index = if (newlineIndex == -1) rawText.length else newlineIndex + 1
  }

  if (mutated) {
    systemOutElement.textContent = rewritten.toString()
  }
}

private fun createSafeTransformerFactory(): TransformerFactory =
  TransformerFactory.newInstance().apply {
    runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
    runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
    runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "") }
  }

internal fun Element.androidAttr(localName: String): String? {
  val nsValue = getAttributeNS(SdkConstants.ANDROID_URI, localName)
  if (nsValue.isNotBlank()) return nsValue
  return getAttribute("android:$localName").takeIf { it.isNotBlank() }
}
