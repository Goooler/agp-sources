/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.utp.gradle.api

import org.gradle.api.NonExtensible
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Optional

private const val ANDROID_TOOLS_UTP_PLUGIN_MAVEN_GROUP_ID = "com.android.tools.utp"

/** Available Unified Test Platform dependencies. */
enum class UtpDependency(
  val artifactId: String,
  val mainClass: String,
  val mapperFunc: (UtpDependencies) -> ConfigurableFileCollection,
  private val groupId: String,
) {
  GRADLE_WORK_ACTION("gradle-work-action", "", UtpDependencies::gradleWorkAction, ANDROID_TOOLS_UTP_PLUGIN_MAVEN_GROUP_ID);

  val configurationName: String = "unified-test-platform-${artifactId}"

  /** Returns a maven coordinate string to download dependencies from the Maven repository. */
  fun mavenCoordinate(androidToolsBaseVersion: String): String {
    return "${groupId}:${artifactId}:${androidToolsBaseVersion}"
  }
}

@NonExtensible
abstract class UtpDependencies {

  @get:Optional @get:Classpath abstract val gradleWorkAction: ConfigurableFileCollection
}
