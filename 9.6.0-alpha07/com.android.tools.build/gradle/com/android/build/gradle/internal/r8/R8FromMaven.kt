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

package com.android.build.gradle.internal.r8

import com.android.build.gradle.internal.dependency.ShrinkerVersion
import com.android.build.gradle.internal.services.RunOnceBuildServiceImpl
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.dexing.R8Version
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection

/**
 * The R8 compiler JARs as fetched dynamically from Maven or default fallback.
 *
 * Contains a [FileCollection] representing the resolved R8 classpath, and a String identifying the R8 version being used.
 */
class R8FromMaven(val r8Classpath: FileCollection, val version: String) {
  companion object {
    const val R8_GROUP: String = "com.android.tools"
    const val R8_MODULE: String = "r8"

    @JvmStatic fun create(project: Project, projectOptions: ProjectOptions): R8FromMaven = create(project, projectOptions::get)

    @JvmStatic
    fun create(project: Project, stringOption: (option: StringOption) -> String?): R8FromMaven = create(project, stringOption, null)

    /**
     * Returns R8 [R8Version.VERSION_AGP_WAS_SHIPPED_WITH] without reading any version override, so nothing is validated or logged.
     *
     * For plugins that don't run R8 but still need an [R8FromMaven] for their project services.
     */
    @JvmStatic fun withDefaultVersion(project: Project): R8FromMaven = create(project, { null }, null)

    /**
     * Resolves the R8 compiler JARs, prioritizing [settingsR8Version] (from `android { r8 { version = ... } }` in `settings.gradle`) over
     * [StringOption.R8_VERSION_OVERRIDE], falling back to [R8Version.VERSION_AGP_WAS_SHIPPED_WITH].
     *
     * An override must not be older than [R8Version.VERSION_AGP_WAS_SHIPPED_WITH], the minimum R8 version this version of AGP supports,
     * because AGP calls R8 APIs that older versions lack. An older override fails with an [InvalidUserDataException]. Overrides that cannot
     * be parsed as a version are not checked.
     *
     * A custom version is logged at info level once per build.
     *
     * @throws InvalidUserDataException if the override is older than [R8Version.VERSION_AGP_WAS_SHIPPED_WITH]
     */
    @JvmStatic
    fun create(
      project: Project,
      stringOption: (option: StringOption) -> String?,
      settingsR8Version: String?,
    ): R8FromMaven {
      // A blank value means it isn't set, e.g. -Pandroid.r8.versionOverride= on the command line.
      val settingsVersion = settingsR8Version?.trim()?.ifEmpty { null }
      val propertyVersion = stringOption(StringOption.R8_VERSION_OVERRIDE)?.trim()?.ifEmpty { null }
      val version = settingsVersion ?: propertyVersion ?: R8Version.VERSION_AGP_WAS_SHIPPED_WITH
      // Where a custom version was set, for messages, or null for the default version.
      val overrideSource =
        when {
          settingsVersion != null -> "android { r8 { version = \"$settingsVersion\" } } in settings.gradle"
          propertyVersion != null -> "Gradle property ${StringOption.R8_VERSION_OVERRIDE.propertyName}=$propertyVersion"
          else -> null
        }
      if (overrideSource != null) {
        checkMinimumR8Version(version, overrideSource)
        // Once per build rather than once per project. Keyed by version in case included builds use different versions.
        RunOnceBuildServiceImpl.RegistrationAction(project).execute().get().runOnce(version, R8FromMaven::class.java.name) {
          project.logger.info("Using custom R8 version $version")
        }
      }

      val configuration =
        project.configurations.detachedConfiguration(project.dependencies.create("$R8_GROUP:$R8_MODULE:$version")).also {
          it.isTransitive = true
          it.isCanBeConsumed = false
          it.isCanBeResolved = true
          it.description = "Detached configuration for resolving R8 compiler JARs."
        }

      return R8FromMaven(configuration, version)
    }

    private fun checkMinimumR8Version(versionOverride: String, overrideSource: String) {
      val minimumVersion = R8Version.VERSION_AGP_WAS_SHIPPED_WITH
      val parsedOverride = ShrinkerVersion.tryParse(versionOverride) ?: return
      val parsedMinimum = ShrinkerVersion.tryParse(minimumVersion) ?: return
      if (parsedOverride < parsedMinimum) {
        throw InvalidUserDataException(
          "$overrideSource is older than R8 $minimumVersion, the minimum R8 version " +
            "supported by this version of the Android Gradle Plugin. Remove the override or use version $minimumVersion or newer."
        )
      }
    }
  }
}

/**
 * The [ShrinkerVersion] the build runs, for filtering version-targeted consumer rules (`META-INF/com.android.tools/r8-from-...`).
 *
 * Falls back to [ShrinkerVersion.R8] when the version override cannot be parsed as a version (for example, a dynamic version), matching the
 * leniency of [R8FromMaven.create].
 */
fun effectiveShrinkerVersion(r8FromMaven: R8FromMaven): ShrinkerVersion =
  ShrinkerVersion.tryParse(r8FromMaven.version) ?: ShrinkerVersion.R8
