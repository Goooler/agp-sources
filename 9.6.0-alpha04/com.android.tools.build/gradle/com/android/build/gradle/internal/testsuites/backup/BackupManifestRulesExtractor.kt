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

package com.android.build.gradle.internal.testsuites.backup

import com.android.build.gradle.internal.test.report.androidAttr
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.VersionQualifier
import com.android.sdklib.AndroidApiLevel
import com.android.utils.XmlUtils
import java.io.File

internal const val INPUT_TARGET_SDK_VERSION = "com.android.agp.test.TARGET_SDK_VERSION"
internal const val INPUT_ALLOW_BACKUP = "com.android.agp.test.ALLOW_BACKUP"
internal const val INPUT_FULL_BACKUP_CONTENT = "com.android.agp.test.FULL_BACKUP_CONTENT"
internal const val INPUT_FULL_BACKUP_CONTENT_FILE = "com.android.agp.test.FULL_BACKUP_CONTENT_FILE"
internal const val INPUT_FULL_BACKUP_CONTENT_XML = "com.android.agp.test.FULL_BACKUP_CONTENT_XML"
internal const val INPUT_DATA_EXTRACTION_RULES = "com.android.agp.test.DATA_EXTRACTION_RULES"
internal const val INPUT_DATA_EXTRACTION_RULES_FILE = "com.android.agp.test.DATA_EXTRACTION_RULES_FILE"
internal const val INPUT_DATA_EXTRACTION_RULES_XML = "com.android.agp.test.DATA_EXTRACTION_RULES_XML"

internal const val KEY_ALLOW_BACKUP = "allowBackup"
internal const val KEY_FULL_BACKUP_CONTENT = "fullBackupContent"
internal const val KEY_FULL_BACKUP_CONTENT_FILE = "fullBackupContentFile"
internal const val KEY_FULL_BACKUP_CONTENT_XML = "fullBackupContentXml"
internal const val KEY_DATA_EXTRACTION_RULES = "dataExtractionRules"
internal const val KEY_DATA_EXTRACTION_RULES_FILE = "dataExtractionRulesFile"
internal const val KEY_DATA_EXTRACTION_RULES_XML = "dataExtractionRulesXml"

internal val BACKUP_SUITE_ENVIRONMENT_KEYS =
  setOf(
    KEY_ALLOW_BACKUP,
    KEY_FULL_BACKUP_CONTENT,
    KEY_FULL_BACKUP_CONTENT_FILE,
    KEY_FULL_BACKUP_CONTENT_XML,
    KEY_DATA_EXTRACTION_RULES,
    KEY_DATA_EXTRACTION_RULES_FILE,
    KEY_DATA_EXTRACTION_RULES_XML,
  )

/** Extracted Android manifest backup configuration and resolved XML rule resources. */
internal data class ExtractedBackupRules(
  val targetSdkVersion: Int? = null,
  val allowBackup: Boolean? = null,
  val fullBackupContent: String? = null,
  val fullBackupContentFile: String? = null,
  val fullBackupContentXml: String? = null,
  val dataExtractionRules: String? = null,
  val dataExtractionRulesFile: String? = null,
  val dataExtractionRulesXml: String? = null,
)

/**
 * Extracts `android:allowBackup`, `android:fullBackupContent`, and `android:dataExtractionRules` from a tested variant's merged manifest
 * and resolves referenced `@xml/...` rule files across resource overlays.
 */
internal object BackupManifestRulesExtractor {

  fun extract(manifestFile: File?, resDirs: List<File>): ExtractedBackupRules? {
    if (manifestFile == null || !manifestFile.isFile) return null
    val docFactory = XmlUtils.createDocumentBuilderFactory().apply { isNamespaceAware = true }
    val document = runCatching { docFactory.newDocumentBuilder().parse(manifestFile) }.getOrNull() ?: return null
    val manifestEl = document.documentElement ?: return null
    val manifestChildren = XmlUtils.getSubTagsAsList(manifestEl)

    val targetSdkVersion =
      manifestChildren.firstOrNull { (it.localName ?: it.tagName) == "uses-sdk" }?.androidAttr("targetSdkVersion")?.toIntOrNull()
    val appEl = manifestChildren.firstOrNull { (it.localName ?: it.tagName) == "application" }
    if (appEl == null) {
      return targetSdkVersion?.let { ExtractedBackupRules(targetSdkVersion = it) }
    }

    val rawAllowBackup = appEl.androidAttr("allowBackup")
    val allowBackup =
      when {
        rawAllowBackup == null -> true
        rawAllowBackup.equals("true", ignoreCase = true) -> true
        rawAllowBackup.equals("false", ignoreCase = true) -> false
        else -> null
      }
    val fullBackupContent = appEl.androidAttr("fullBackupContent")
    val (fullBackupContentFile, fullBackupContentXml) = resolveXmlRuleResource(fullBackupContent, resDirs, targetSdkVersion)
    val dataExtractionRules = appEl.androidAttr("dataExtractionRules")
    val (dataExtractionRulesFile, dataExtractionRulesXml) = resolveXmlRuleResource(dataExtractionRules, resDirs, targetSdkVersion)

    return ExtractedBackupRules(
      targetSdkVersion = targetSdkVersion,
      allowBackup = allowBackup,
      fullBackupContent = fullBackupContent,
      fullBackupContentFile = fullBackupContentFile,
      fullBackupContentXml = fullBackupContentXml,
      dataExtractionRules = dataExtractionRules,
      dataExtractionRulesFile = dataExtractionRulesFile,
      dataExtractionRulesXml = dataExtractionRulesXml,
    )
  }

  fun toEngineInputProperties(rules: ExtractedBackupRules?): Map<String, String> = buildMap {
    if (rules == null) return@buildMap
    putNonBlank(INPUT_TARGET_SDK_VERSION, rules.targetSdkVersion?.toString())
    putNonBlank(INPUT_ALLOW_BACKUP, rules.allowBackup?.toString())
    putNonBlank(INPUT_FULL_BACKUP_CONTENT, rules.fullBackupContent)
    putNonBlank(INPUT_FULL_BACKUP_CONTENT_FILE, rules.fullBackupContentFile)
    putNonBlank(INPUT_FULL_BACKUP_CONTENT_XML, rules.fullBackupContentXml)
    putNonBlank(INPUT_DATA_EXTRACTION_RULES, rules.dataExtractionRules)
    putNonBlank(INPUT_DATA_EXTRACTION_RULES_FILE, rules.dataExtractionRulesFile)
    putNonBlank(INPUT_DATA_EXTRACTION_RULES_XML, rules.dataExtractionRulesXml)
  }

  fun toSuiteReportProperties(rules: ExtractedBackupRules?): Map<String, String> = buildMap {
    if (rules == null) return@buildMap
    putNonBlank(KEY_ALLOW_BACKUP, rules.allowBackup?.toString())
    putNonBlank(KEY_FULL_BACKUP_CONTENT, rules.fullBackupContent)
    putNonBlank(KEY_FULL_BACKUP_CONTENT_FILE, rules.fullBackupContentFile)
    putNonBlank(KEY_FULL_BACKUP_CONTENT_XML, rules.fullBackupContentXml)
    putNonBlank(KEY_DATA_EXTRACTION_RULES, rules.dataExtractionRules)
    putNonBlank(KEY_DATA_EXTRACTION_RULES_FILE, rules.dataExtractionRulesFile)
    putNonBlank(KEY_DATA_EXTRACTION_RULES_XML, rules.dataExtractionRulesXml)
  }

  /**
   * Resolves an `@xml/<name>` backup rule reference across [resDirs] (ordered highest-priority overlay first), bounding `xml-vNN` buckets
   * by [targetSdkVersion] at build time before runtime target devices are selected.
   */
  fun resolveXmlRuleResource(
    ref: String?,
    resDirs: List<File>,
    targetSdkVersion: Int? = null,
  ): Pair<String?, String?> {
    if (ref == null || !ref.startsWith("@xml/")) return null to null
    val xmlName =
      ref.removePrefix("@xml/").takeIf { it.isNotBlank() && !it.contains('/') && !it.contains('\\') && !it.contains("..") }
        ?: return null to null
    val fileName = "$xmlName.xml"
    val maxApiLevel = AndroidApiLevel(targetSdkVersion ?: Int.MAX_VALUE)

    // Resource merging keeps every qualifier bucket, and the device picks the best one at runtime. Walk the
    // overlays from lowest to highest priority so a later overlay replaces only the same bucket.
    var defaultFile: File? = null
    var bestVersioned: Pair<AndroidApiLevel, File>? = null
    for (resDir in resDirs.asReversed()) {
      val subDirs = resDir.listFiles()?.filter { it.isDirectory && (it.name == "xml" || it.name.startsWith("xml-")) }.orEmpty()
      for (dir in subDirs) {
        val candidate = File(dir, fileName)
        if (!candidate.isFile) continue
        val config = FolderConfiguration.getConfigForFolder(dir.name) ?: continue
        if (config.any { it !is VersionQualifier }) continue
        val apiLevel = config.versionQualifier?.androidApiLevel
        if (apiLevel == null) {
          defaultFile = candidate
        } else if (apiLevel <= maxApiLevel && (bestVersioned == null || apiLevel >= bestVersioned.first)) {
          bestVersioned = apiLevel to candidate
        }
      }
    }
    val matched = bestVersioned?.second ?: defaultFile ?: return fileName to null
    // An unreadable rules file is left out of the report instead of failing the build.
    return fileName to runCatching { matched.readText(Charsets.UTF_8) }.getOrNull()
  }

  private fun MutableMap<String, String>.putNonBlank(key: String, value: String?) {
    if (!value.isNullOrBlank()) put(key, value)
  }
}
