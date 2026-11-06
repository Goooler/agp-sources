/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("ResourceRepositoryUtil")

package com.android.ide.common.resources

import com.android.ide.common.rendering.api.PluralsResourceValue
import com.android.ide.common.rendering.api.PluralsResourceValueImpl
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.TextResourceValue
import com.android.ide.common.rendering.api.TextResourceValueImpl
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.resources.Pseudolocalizer
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.google.common.collect.Table
import com.google.common.collect.Tables
import java.util.EnumMap
import java.util.HashMap
import java.util.SortedSet
import java.util.TreeSet

/**
 * Returns the resource values matching a given [FolderConfiguration].
 *
 * When [referenceConfig] specifies an Android pseudolocale (`en-rXA` or `ar-rXB`; see
 * https://developer.android.com/guide/topics/resources/pseudolocales), [ResourceType.STRING] and [ResourceType.PLURALS] resources that do
 * not have an explicit pseudolocale entry in this repository are dynamically pseudolocalized from default-locale resources via
 * [Pseudolocalizer]. This mirrors AAPT2's compile-time pseudolocalization for IDE previews that resolve resources directly from raw XML
 * source repositories.
 *
 * @param referenceConfig the configuration that each value must match
 * @return a [Table] with one row for every namespace present in this repository, where every row contains an entry for all resource types
 */
fun ResourceRepository.getConfiguredResources(
  referenceConfig: FolderConfiguration
): Table<ResourceNamespace, ResourceType, ResourceValueMap> {
  val backingMap: Map<ResourceNamespace, Map<ResourceType, ResourceValueMap>> =
    if (KnownNamespacesMap.canContainAll(namespaces)) {
      KnownNamespacesMap()
    } else {
      HashMap()
    }
  val result = Tables.newCustomTable(backingMap) { EnumMap(ResourceType::class.java) }

  for (namespace in namespaces) {
    // For performance reasons don't mix framework and non-framework resources since
    // they have different life spans.
    assert(namespaces.size == 1 || namespace !== ResourceNamespace.ANDROID)

    for (type in ResourceType.values()) {
      // get the local results and put them in the map
      result.put(namespace, type, getConfiguredResources(namespace, type, referenceConfig))
    }
  }
  return result
}

/**
 * Returns a map of (resource name, resource value) for the given [ResourceType].
 *
 * The values returned are taken from the resource files best matching a given [FolderConfiguration]. If [referenceConfig] specifies a
 * pseudolocale (`en-rXA` or `ar-rXB`) and [type] is [ResourceType.STRING] or [ResourceType.PLURALS], default-locale resources are
 * dynamically pseudolocalized whenever no explicit pseudolocale resource is defined.
 *
 * @param namespace namespaces of the resources
 * @param type the type of the resources.
 * @param referenceConfig the configuration to best match
 */
fun ResourceRepository.getConfiguredResources(
  namespace: ResourceNamespace,
  type: ResourceType,
  referenceConfig: FolderConfiguration,
): ResourceValueMap {
  val itemsByName = getResources(namespace, type).asMap()
  val result = ResourceValueMap.createWithExpectedSize(itemsByName.size)
  val pseudolocalizerMethod = referenceConfig.getPseudolocalizerMethodForType(type)

  if (pseudolocalizerMethod == Pseudolocalizer.Method.NONE) {
    for (itemGroup in itemsByName.values) {
      // Look for the best match for the given configuration.
      val match = referenceConfig.findMatchingConfigurable(itemGroup)
      if (match != null) {
        val value = match.resourceValue
        if (value != null) {
          result[match.name] = value
        }
      }
    }
    return result
  }

  // Create a copy of the configuration with no locale qualifier (`Locale.ANY`) so we look up the
  // default (untranslated) strings to pseudolocalize, while still respecting non-locale qualifiers
  // such as night mode or screen size.
  val defaultLocaleConfig = FolderConfiguration.copyOf(referenceConfig).apply { localeQualifier = Locale.ANY.qualifier }
  for (itemGroup in itemsByName.values) {
    val resolvedValue =
      resolvePseudolocalizedValue(
        itemGroup,
        referenceConfig,
        defaultLocaleConfig,
        pseudolocalizerMethod,
      )
    if (resolvedValue != null) {
      result[resolvedValue.name] = resolvedValue
    }
  }

  return result
}

fun ResourceRepository.getConfiguredValue(
  type: ResourceType,
  name: String,
  referenceConfig: FolderConfiguration,
): ResourceValue? {
  val items = getResources(ResourceNamespace.RES_AUTO, type, name)
  val pseudolocalizerMethod = referenceConfig.getPseudolocalizerMethodForType(type)
  if (pseudolocalizerMethod == Pseudolocalizer.Method.NONE) {
    // Look for the best match for the given configuration.
    val match = referenceConfig.findMatchingConfigurable(items)
    return match?.resourceValue
  }

  val defaultLocaleConfig = FolderConfiguration.copyOf(referenceConfig).apply { localeQualifier = Locale.ANY.qualifier }
  return resolvePseudolocalizedValue(
    items,
    referenceConfig,
    defaultLocaleConfig,
    pseudolocalizerMethod,
  )
}

/** Returns the sorted list of locales used in the resources. */
fun ResourceRepository.getLocales(): SortedSet<LocaleQualifier> {
  // As an optimization we could just look for values since that's typically where
  // the languages are defined -- not on layouts, menus, etc -- especially if there
  // are no translations for it.
  val locales = TreeSet<LocaleQualifier>()

  for (repository in leafResourceRepositories) {
    repository.accept {
      val locale = it.configuration.localeQualifier
      if (locale != null) {
        locales.add(locale)
      }
      ResourceVisitor.VisitResult.CONTINUE
    }
  }

  return locales
}

/**
 * Returns the [Pseudolocalizer.Method] to apply when configuring resources of [type] for this [FolderConfiguration]. Only
 * [ResourceType.STRING] and [ResourceType.PLURALS] are pseudolocalized by AAPT2.
 */
private fun FolderConfiguration.getPseudolocalizerMethodForType(type: ResourceType): Pseudolocalizer.Method {
  if (type != ResourceType.STRING && type != ResourceType.PLURALS) {
    return Pseudolocalizer.Method.NONE
  }
  return localeQualifier?.getPseudolocalizerMethod() ?: Pseudolocalizer.Method.NONE
}

/**
 * Returns the [Pseudolocalizer.Method] corresponding to this [LocaleQualifier]:
 * - [Pseudolocalizer.Method.ACCENT] for `en-rXA` / `b+en+XA` (or script `Enxa`),
 * - [Pseudolocalizer.Method.BIDI] for `ar-rXB` / `b+ar+XB` (or script `Arxb`),
 * - [Pseudolocalizer.Method.NONE] for all other locales.
 */
internal fun LocaleQualifier.getPseudolocalizerMethod(): Pseudolocalizer.Method =
  when {
    (language == "en" && region == "XA") || script == "Enxa" -> Pseudolocalizer.Method.ACCENT
    (language == "ar" && region == "XB") || script == "Arxb" -> Pseudolocalizer.Method.BIDI
    else -> Pseudolocalizer.Method.NONE
  }

/**
 * Resolves a single resource group [items] for a pseudolocale [referenceConfig]:
 * 1. If an explicit pseudolocale resource exists in [items] (e.g. from `values-en-rXA` or a precompiled APK table), returns it as-is
 *    without re-pseudolocalizing.
 * 2. Otherwise, resolves the default-locale resource matching [defaultLocaleConfig] and dynamically pseudolocalizes it using [method]. This
 *    avoids picking a regional translation (such as `values-en-rUS` for `en-rXA` or `values-ar` for `ar-rXB`).
 * 3. If the resource has no default-locale entry at all, falls back to the [referenceConfig] match.
 */
private fun resolvePseudolocalizedValue(
  items: Collection<ResourceItem>,
  referenceConfig: FolderConfiguration,
  defaultLocaleConfig: FolderConfiguration,
  method: Pseudolocalizer.Method,
): ResourceValue? {
  val match = referenceConfig.findMatchingConfigurable(items)
  if (match != null && match.configuration.localeQualifier?.isPseudoLocale == true) {
    return match.resourceValue
  }

  val defaultMatch = defaultLocaleConfig.findMatchingConfigurable(items)
  if (defaultMatch != null) {
    val value = defaultMatch.resourceValue ?: return null
    return pseudolocalizeResourceValue(value, method)
  }

  return match?.resourceValue
}

/**
 * Pseudolocalizes a single string [text] using [method], returning [text] unchanged if [method] is [Pseudolocalizer.Method.NONE] or if
 * [text] is a resource reference (e.g. `"@string/other"` or `"?attr/foo"`).
 */
internal fun pseudolocalizeString(text: String, method: Pseudolocalizer.Method): String {
  if (method == Pseudolocalizer.Method.NONE) return text
  // Do not pseudolocalize resource references (e.g. "@string/other" or "?attr/foo"), otherwise
  // ResourceResolver will fail to parse and dereference the target resource URL.
  if (ResourceUrl.parse(text) != null) return text
  return Pseudolocalizer(method).localize(text)
}

/**
 * Returns a copy of [original] with its string or plural text values pseudolocalized using [method]. Non-string/plural resources and
 * resource reference values (`@string/...`) are returned unchanged.
 */
internal fun pseudolocalizeResourceValue(
  original: ResourceValue,
  method: Pseudolocalizer.Method,
): ResourceValue {
  if (method == Pseudolocalizer.Method.NONE) return original
  return when (original.resourceType) {
    ResourceType.STRING -> {
      val value = original.value ?: return original
      if (ResourceUrl.parse(value) != null) return original
      val localizedValue = pseudolocalizeString(value, method)

      // TextResourceValue may also carry a separate `rawXmlValue` (containing raw XML markup such
      // as <xliff:g> tags or CDATA sections) and a `renderingValue` (the text with <xliff:g> tags
      // stripped for preview rendering, which may differ from `value` when <xliff:g example="...">
      // attributes are present).
      val rawXmlValue = (original as? TextResourceValue)?.rawXmlValue
      val localizedRawXmlValue =
        when {
          rawXmlValue == null -> null
          rawXmlValue == value -> localizedValue
          else -> pseudolocalizeString(rawXmlValue, method)
        }
      val rawRenderingValue = (original as? TextResourceValue)?.renderingValue
      // Note: PsiTextResourceValue returns raw XML text for `renderingValue` when no <xliff:g> tag
      // is present, so unescape XML whitespace/quotes before comparing and pseudolocalizing.
      val unescapedRenderingValue = rawRenderingValue?.let { ValueXmlHelper.unescapeResourceString(it, false, true) }
      val localizedRenderingValue =
        when {
          unescapedRenderingValue == null -> null
          unescapedRenderingValue == value -> localizedValue
          else -> pseudolocalizeString(unescapedRenderingValue, method)
        }
      TextResourceValueImpl(
          original.namespace,
          original.name,
          localizedValue,
          localizedRawXmlValue,
          original.libraryName,
        )
        .apply {
          renderingValue = localizedRenderingValue
          namespaceResolver = original.namespaceResolver
        }
    }
    ResourceType.PLURALS -> {
      if (original !is PluralsResourceValue) return original
      val rawValue = original.value
      val localizedDefaultValue =
        if (rawValue != null && ResourceUrl.parse(rawValue) == null) {
          pseudolocalizeString(rawValue, method)
        } else {
          rawValue
        }
      PluralsResourceValueImpl(
          original.namespace,
          original.name,
          localizedDefaultValue,
          original.libraryName,
        )
        .apply {
          for (i in 0 until original.pluralsCount) {
            val quantity = original.getQuantity(i)
            val quantityValue = original.getValue(i)
            val localizedQuantityValue =
              if (ResourceUrl.parse(quantityValue) == null) {
                pseudolocalizeString(quantityValue, method)
              } else {
                quantityValue
              }
            addPlural(quantity, localizedQuantityValue)
          }
          namespaceResolver = original.namespaceResolver
        }
    }
    else -> original
  }
}
