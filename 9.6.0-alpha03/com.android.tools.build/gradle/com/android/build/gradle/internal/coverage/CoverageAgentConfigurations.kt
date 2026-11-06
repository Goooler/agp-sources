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

package com.android.build.gradle.internal.coverage

import com.android.Version
import com.android.build.gradle.options.StringOption
import com.android.builder.errors.IssueReporter
import com.android.ide.common.repository.AgpVersion

/** Configurations for the on-device coverage agent. */
object CoverageAgentConfigurations {
  private const val AGENT_DEPENDENCY_GROUP = "com.android.tools.test"
  private const val AGENT_DEPENDENCY_NAME = "coverage-agent"

  /**
   * Returns the Maven coordinate of the on-device coverage agent.
   *
   * The agent is built and released in lockstep with the Android Gradle plugin (same version, same gmaven release), so by default it
   * resolves to the exact AGP version. A project can pin a different release of the agent with the
   * [StringOption.COVERAGE_AGENT_VERSION_OVERRIDE] gradle property ([versionOverride]), e.g. to pick up an agent fix without changing its
   * AGP version, as long as AGP supports that release (see [isAgentVersionSupported]).
   *
   * Either way, the coordinate is added to the androidTest runtime classpath and forced on both the compile and runtime classpaths of the
   * test component (see `TaskManager.handleOnTheFlyCoverageDependencies`), so a different version declared by the build author cannot win
   * conflict resolution.
   */
  @JvmStatic
  fun getAgentRuntimeDependency(versionOverride: String?, issueReporter: IssueReporter?): String {
    return "$AGENT_DEPENDENCY_GROUP:$AGENT_DEPENDENCY_NAME:${getAgentVersion(versionOverride, issueReporter)}"
  }

  /**
   * Returns the version of the on-device coverage agent to use: [versionOverride] if it is set, valid and supported by [agpVersion] (see
   * [isAgentVersionSupported]), otherwise [agpVersion]. Problems with the override are reported to [issueReporter] as a side effect.
   */
  @JvmStatic
  @JvmOverloads
  fun getAgentVersion(
    versionOverride: String?,
    issueReporter: IssueReporter?,
    agpVersion: String = Version.ANDROID_GRADLE_PLUGIN_VERSION,
  ): String {
    val override = versionOverride?.trim()
    if (override.isNullOrEmpty()) {
      return agpVersion
    }
    val parsedOverride = AgpVersion.tryParse(override)
    if (parsedOverride == null) {
      issueReporter?.reportError(
        IssueReporter.Type.GENERIC,
        """
        Could not parse on-device coverage agent version override '$override'.
        Recommendation: Remove or fix the gradle property ${StringOption.COVERAGE_AGENT_VERSION_OVERRIDE.propertyName}; without it the agent version defaults to the Android Gradle plugin version ($agpVersion).
        """
          .trimIndent(),
      )
      return agpVersion
    }
    val parsedAgpVersion = AgpVersion.parse(agpVersion)
    if (!isAgentVersionSupported(parsedOverride, parsedAgpVersion)) {
      val supportedVersions = "${parsedAgpVersion.major}.${parsedAgpVersion.minor}.x"
      issueReporter?.reportError(
        IssueReporter.Type.GENERIC,
        """
        The on-device coverage agent version override '$override' is not compatible with the Android Gradle plugin version ($agpVersion), which only supports $supportedVersions versions of the agent.
        Recommendation: Remove the gradle property ${StringOption.COVERAGE_AGENT_VERSION_OVERRIDE.propertyName} or set it to a $supportedVersions version; without it the agent version defaults to the Android Gradle plugin version ($agpVersion).
        """
          .trimIndent(),
      )
      return agpVersion
    }
    if (parsedOverride < parsedAgpVersion) {
      issueReporter?.reportWarning(
        IssueReporter.Type.GENERIC,
        """
        The on-device coverage agent version override '$override' is older than the Android Gradle plugin version ($agpVersion).
        Remove the gradle property ${StringOption.COVERAGE_AGENT_VERSION_OVERRIDE.propertyName} to use the agent released with the plugin.
        """
          .trimIndent(),
      )
    }
    return override
  }

  /**
   * Returns whether the Android Gradle plugin at [agpVersion] supports the on-device coverage agent at [agentVersion].
   *
   * The agent is released in lockstep with the plugin, and a plugin version only supports the agent versions with its own major and minor
   * version: AGP 9.6.x supports agent 9.6.y for any y, from 9.6.0-alpha01 (the first release of the series) up to the last 9.6 release, and
   * no agent of an older (e.g. 9.5) or newer (e.g. 9.7 or 10.6) series.
   */
  @JvmStatic
  fun isAgentVersionSupported(agentVersion: AgpVersion, agpVersion: AgpVersion): Boolean =
    agentVersion.major == agpVersion.major && agentVersion.minor == agpVersion.minor
}
