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

import com.android.build.api.variant.TestSuiteSourceSet
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.tasks.JacocoTask
import com.android.build.gradle.options.StringOption
import kotlin.text.isNullOrEmpty
import org.gradle.api.Project
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

/**
 * This version will be set for the Jacoco plugin extension in AndroidUnitTest, and the JacocoReportTask Test Ant configuration.
 *
 * The priority of version that will be chosen is:
 * 1. Gradle Property: [StringOption.JACOCO_TOOL_VERSION]
 * 2. Android DSL: android.testCoverage.jacocoVersion
 * 3. Jacoco DSL: jacoco.toolVersion
 * 4. JacocoOptions.DEFAULT_VERSION
 */
fun getUnitTestJacocoVersion(project: Project, creationConfig: ComponentCreationConfig): String {
  val jacocoVersionProjectOption = creationConfig.services.projectOptions[StringOption.JACOCO_TOOL_VERSION]
  if (!jacocoVersionProjectOption.isNullOrEmpty()) {
    return jacocoVersionProjectOption
  }
  if ((creationConfig.global.testCoverage as JacocoOptions).versionSetByUser) {
    return creationConfig.global.testCoverage.jacocoVersion
  }
  val pluginExtension = project.extensions.findByType(JacocoPluginExtension::class.java)
  if (pluginExtension != null) {
    return pluginExtension.toolVersion
  }
  return JacocoOptions.DEFAULT_VERSION
}

/**
 * Resolves the Jacoco version for a Test Suite, ensuring that mixed suites containing both host jar and device specific targets have
 * aligned versions to prevent serialization issues when merging execution data. Returns null if mixed suites contain host and device
 * targets with mismatched versions.
 */
fun getTestSuiteJacocoVersion(project: Project, creationConfig: TestSuiteCreationConfig): String? {
  val hasHostJar = creationConfig.sourceContainers.any { it.source is TestSuiteSourceSet.HostJar }
  val hasDeviceSpecific = creationConfig.sourceContainers.any { it.source is TestSuiteSourceSet.TestApk }

  return if (hasHostJar && hasDeviceSpecific) {
    val hostVersion = getUnitTestJacocoVersion(project, creationConfig.testedVariant)
    val deviceVersion = JacocoTask.getAndroidTestJacocoVersion(creationConfig.testedVariant)
    if (hostVersion != deviceVersion) {
      null
    } else {
      hostVersion
    }
  } else if (hasHostJar) {
    getUnitTestJacocoVersion(project, creationConfig.testedVariant)
  } else {
    JacocoTask.getAndroidTestJacocoVersion(creationConfig.testedVariant)
  }
}
