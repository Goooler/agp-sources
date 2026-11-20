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

package com.android.build.gradle.internal.test.report

import com.android.SdkConstants
import com.android.ide.common.resources.ValueXmlHelper
import com.android.utils.XmlUtils
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

internal fun Element.androidAttr(localName: String): String? {
  val nsValue = getAttributeNS(SdkConstants.ANDROID_URI, localName)
  if (nsValue.isNotBlank()) return nsValue
  return getAttribute("android:$localName").takeIf { it.isNotBlank() }
}

/** Parsed `<manifest>` and `<application>` DOM elements and `targetSdkVersion` from a merged `AndroidManifest.xml`. */
internal data class ParsedManifestDocument(
  val manifestElement: Element,
  val applicationElement: Element?,
  val targetSdkVersion: Int?,
  val documentBuilderFactory: DocumentBuilderFactory,
)

/** Application identity and version metadata extracted from a tested variant's merged manifest and string resources. */
internal data class ParsedManifestMetadata(
  val packageName: String?,
  val applicationName: String?,
  val versionName: String?,
  val versionCode: Int?,
  val targetSdkVersion: Int?,
)

internal fun MutableMap<String, String>.putNonBlank(key: String, value: String?) {
  if (!value.isNullOrBlank()) put(key, value)
}

/**
 * Extracts merged-manifest metadata, resolves `@string/...` application labels across resource overlays, and builds suite-environment
 * report properties.
 */
internal object SuiteEnvironmentMetadataExtractor {

  fun parseManifestDocument(manifestFile: File?): ParsedManifestDocument? {
    if (manifestFile == null || !manifestFile.isFile) return null
    val docFactory = XmlUtils.createDocumentBuilderFactory().apply { isNamespaceAware = true }
    val document = runCatching { docFactory.newDocumentBuilder().parse(manifestFile) }.getOrNull() ?: return null
    val manifestEl = document.documentElement ?: return null
    val manifestChildren = XmlUtils.getSubTagsAsList(manifestEl)
    val targetSdkVersion =
      manifestChildren.firstOrNull { (it.localName ?: it.tagName) == "uses-sdk" }?.androidAttr("targetSdkVersion")?.toIntOrNull()
    val appEl = manifestChildren.firstOrNull { (it.localName ?: it.tagName) == "application" }
    return ParsedManifestDocument(
      manifestElement = manifestEl,
      applicationElement = appEl,
      targetSdkVersion = targetSdkVersion,
      documentBuilderFactory = docFactory,
    )
  }

  fun parseMergedManifest(
    manifestFile: File?,
    resDirs: List<File>,
  ): ParsedManifestMetadata? {
    val parsed = parseManifestDocument(manifestFile) ?: return null
    val manifestEl = parsed.manifestElement

    val packageName = manifestEl.getAttribute("package").takeIf { it.isNotBlank() }
    val versionName = manifestEl.androidAttr("versionName")
    val versionCode = manifestEl.androidAttr("versionCode")?.toIntOrNull()
    val rawLabel = parsed.applicationElement?.androidAttr("label")
    val applicationName =
      when {
        rawLabel.isNullOrBlank() -> null
        rawLabel.startsWith("@string/") -> resolveStringResource(rawLabel.removePrefix("@string/"), resDirs, parsed.documentBuilderFactory)
        else -> rawLabel
      }

    return ParsedManifestMetadata(
      packageName = packageName,
      applicationName = applicationName,
      versionName = versionName,
      versionCode = versionCode,
      targetSdkVersion = parsed.targetSdkVersion,
    )
  }

  fun resolveStringResource(
    stringName: String,
    resDirs: List<File>,
    docFactory: DocumentBuilderFactory = XmlUtils.createDocumentBuilderFactory().apply { isNamespaceAware = true },
  ): String? {
    if (stringName.isBlank()) return null
    for (resDir in resDirs) {
      val valuesDir = File(resDir, "values")
      if (!valuesDir.isDirectory) continue
      val xmlFiles =
        valuesDir
          .listFiles()
          ?.filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
          ?.sortedWith(compareBy<File> { it.name != "strings.xml" }.thenBy { it.name })
          .orEmpty()
      for (xmlFile in xmlFiles) {
        val doc = runCatching { docFactory.newDocumentBuilder().parse(xmlFile) }.getOrNull() ?: continue
        val root = doc.documentElement ?: continue
        for (child in XmlUtils.getSubTagsAsList(root)) {
          if ((child.localName ?: child.tagName) == "string" && child.getAttribute("name") == stringName) {
            val rawText = child.textContent ?: continue
            val unescaped = ValueXmlHelper.unescapeResourceString(rawText, false, true)
            if (unescaped.isNotEmpty()) return unescaped
          }
        }
      }
    }
    return null
  }

  fun buildSuiteEnvironmentProperties(
    suiteType: String? = null,
    testedApplicationId: String?,
    agpVersion: String,
    gradleVersion: String?,
    testLibraryVersion: String?,
    runner: String?,
    manifestMetadata: ParsedManifestMetadata?,
    extraProperties: Map<String, String> = emptyMap(),
  ): Map<String, String> = buildMap {
    putNonBlank(KEY_SUITE_TYPE, suiteType)
    putNonBlank(KEY_APPLICATION_ID, testedApplicationId ?: manifestMetadata?.packageName)
    putNonBlank(KEY_APPLICATION_NAME, manifestMetadata?.applicationName)
    putNonBlank(KEY_VERSION_NAME, manifestMetadata?.versionName)
    putNonBlank(KEY_VERSION_CODE, manifestMetadata?.versionCode?.toString())
    putNonBlank(KEY_TARGET_SDK_VERSION, manifestMetadata?.targetSdkVersion?.toString())
    putNonBlank(KEY_AGP_VERSION, agpVersion)
    putNonBlank(KEY_GRADLE_VERSION, gradleVersion)
    putNonBlank(KEY_TEST_LIBRARY_VERSION, testLibraryVersion)
    putNonBlank(KEY_RUNNER, runner)
    extraProperties.forEach { (key, value) -> putNonBlank(key, value) }
  }
}
