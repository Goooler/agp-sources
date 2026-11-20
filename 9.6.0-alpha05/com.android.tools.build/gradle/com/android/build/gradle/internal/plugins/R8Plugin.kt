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
package com.android.build.gradle.internal.plugins

import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptionService
import org.gradle.api.Plugin
import org.gradle.api.Project

class R8Plugin : Plugin<Project> {
  override fun apply(project: Project) {
    val projectOptions = ProjectOptionService.RegistrationAction(project).execute().get().projectOptions
    val shouldApply = projectOptions[BooleanOption.R8_PLUGIN_SUPPORT]
    check(shouldApply) {
      "R8 plugin is incubating, and requires an *explicit opt-in* to use it. " +
        "Set '${BooleanOption.R8_PLUGIN_SUPPORT.propertyName}=true' in gradle.properties to enable."
    }

    project.tasks.register("generateKeepAnnotations") {
      // some configuration
    }
  }
}
