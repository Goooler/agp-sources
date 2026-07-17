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

package com.android.build.gradle.internal.api

import com.android.build.api.dsl.AndroidLibrarySourceSet
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.api.artifact.SourceArtifactType
import javax.inject.Inject
import org.gradle.api.Project

open class DefaultAndroidLibrarySourceSet @Inject constructor(name: String, project: Project, publishPackage: Boolean) :
  DefaultAndroidSourceSet(name, project, publishPackage), AndroidLibrarySourceSet {

  final override val aarKeepRules: com.android.build.api.dsl.AndroidSourceDirectorySet

  init {
    aarKeepRules = DefaultAndroidSourceDirectorySet(displayName, "aarKeepRules", project, SourceArtifactType.AAR_KEEP_RULES)
    aarKeepRules.filter.include("**/*.keep")

    initRoot("src/${sourceSetName.name}")
  }

  override fun aarKeepRules(action: com.android.build.api.dsl.AndroidSourceDirectorySet.() -> Unit) {
    action.invoke(aarKeepRules)
  }

  override fun setRoot(path: String): AndroidSourceSet {
    super.setRoot(path)
    return initRoot(path)
  }

  private fun initRoot(path: String): AndroidSourceSet {
    aarKeepRules.setSrcDirs(listOf("$path/aarKeepRules"))
    return this
  }
}
