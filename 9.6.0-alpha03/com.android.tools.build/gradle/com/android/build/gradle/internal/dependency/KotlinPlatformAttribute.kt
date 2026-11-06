/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.build.gradle.internal.utils.ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID
import com.android.build.gradle.internal.utils.isKotlinAndroidPluginApplied
import com.android.build.gradle.internal.utils.isKotlinPluginAppliedInTheSameClassloader
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

private const val ANDROID_JVM_PLATFORM_TYPE = "androidJvm"
private const val JVM_PLATFORM_TYPE = "jvm"
private const val COMMON_TYPE = "common"
private const val KOTLIN_PLATFORM_TYPE_ATTRIBUTE_NAME = "org.jetbrains.kotlin.platform.type"

/**
 * Requests the `androidJvm` Kotlin platform on [configuration]: the typed [KotlinPlatformType.attribute] when a Kotlin plugin is in AGP's
 * classloader, and [configureKotlinPlatformAttribute] when no Kotlin plugin is applied. Suite host runtime classpaths rely on this request
 * to resolve Android variants, because no Kotlin plugin owns them. A Kotlin plugin loads in a separate classloader only when the Kotlin
 * Gradle plugin is excluded from AGP's dependencies, and [configuration] then relies on `TargetJvmEnvironment=android`.
 */
fun requestAndroidJvmKotlinPlatform(configuration: Configuration, project: Project, projectOptions: ProjectOptions) {
  val builtInKotlin =
    projectOptions.get(BooleanOption.BUILT_IN_KOTLIN) || project.pluginManager.hasPlugin(ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID)
  val kgpInSameClassloader = builtInKotlin || isKotlinPluginAppliedInTheSameClassloader(project)
  when {
    kgpInSameClassloader -> configuration.attributes.attribute(KotlinPlatformType.attribute, KotlinPlatformType.androidJvm)
    !isKotlinAndroidPluginApplied(project) -> configureKotlinPlatformAttribute(listOf(configuration), project)
  }
}

fun configureKotlinPlatformAttribute(configs: List<Configuration>, project: Project) {
  val kotlinPlatformTypeAttribute = Attribute.of(KOTLIN_PLATFORM_TYPE_ATTRIBUTE_NAME, String::class.java)

  configs.forEach { it.attributes.attribute(kotlinPlatformTypeAttribute, ANDROID_JVM_PLATFORM_TYPE) }

  project.dependencies.attributesSchema.attribute(kotlinPlatformTypeAttribute).also {
    it.compatibilityRules.add(KotlinPlatformCompatibilityRule::class.java)
    it.disambiguationRules.add(KotlinPlatformDisambiguationRule::class.java)
  }
}

class KotlinPlatformCompatibilityRule : AttributeCompatibilityRule<String> {
  override fun execute(details: CompatibilityCheckDetails<String>) =
    with(details) {
      if (producerValue == JVM_PLATFORM_TYPE && consumerValue == ANDROID_JVM_PLATFORM_TYPE) compatible()

      if (consumerValue == COMMON_TYPE) compatible()
    }
}

class KotlinPlatformDisambiguationRule : AttributeDisambiguationRule<String> {
  override fun execute(details: MultipleCandidatesDetails<String>) =
    with(details) {
      if (consumerValue in candidateValues) {
        closestMatch(checkNotNull(consumerValue))
        return@with
      }

      if (consumerValue == null && ANDROID_JVM_PLATFORM_TYPE in candidateValues && JVM_PLATFORM_TYPE in candidateValues) {
        closestMatch(JVM_PLATFORM_TYPE)
        return@with
      }

      if (COMMON_TYPE in candidateValues && JVM_PLATFORM_TYPE !in candidateValues && ANDROID_JVM_PLATFORM_TYPE !in candidateValues) {
        closestMatch(COMMON_TYPE)
        return@with
      }
    }
}
