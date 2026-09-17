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

package com.android.build.api.variant.impl

import com.android.build.api.component.impl.LibrarySourcesProvider
import com.android.build.api.variant.InternalLibrarySources
import com.android.build.gradle.internal.api.DefaultAndroidLibrarySourceSet
import com.android.build.gradle.internal.api.DefaultAndroidSourceDirectorySet
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.services.VariantServices

class LibrarySourcesImpl(
  private val defaultSourceProvider: LibrarySourcesProvider,
  variantServices: VariantServices,
  override val multiFlavorSourceProvider: DefaultAndroidSourceSet?,
  override val variantSourceProvider: DefaultAndroidLibrarySourceSet?,
) : SourcesImpl(defaultSourceProvider, variantServices, multiFlavorSourceProvider, variantSourceProvider), InternalLibrarySources {

  override val aarKeepRules =
    FlatSourceDirectoriesImpl(SourceType.AAR_KEEP_RULES.folder, variantServices, null).also { sourceDirectoriesImpl ->
      defaultSourceProvider.getAarKeepRules(sourceDirectoriesImpl).run { sourceDirectoriesImpl.addStaticOrGeneratedSources(this) }
      updateSourceDirectories(sourceDirectoriesImpl, variantSourceProvider?.aarKeepRules as DefaultAndroidSourceDirectorySet?)
    }

  override fun aarKeepRules(action: (FlatSourceDirectoriesImpl) -> Unit) {
    action(aarKeepRules)
  }
}
