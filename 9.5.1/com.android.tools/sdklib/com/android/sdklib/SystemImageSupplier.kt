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
package com.android.sdklib

import com.android.repository.api.RepoManager
import com.android.repository.api.RepoPackage
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.sdklib.repository.targets.SystemImageManager

class SystemImageSupplier(private val repoManager: RepoManager, private val systemImageManager: SystemImageManager) {
  fun get(): List<ISystemImage> {
    val localImages = systemImageManager.images
    val localPaths = localImages.mapTo(mutableSetOf()) { it.`package`.path }
    return buildList {
      addAll(localImages)
      for ((path, remotePackage) in repoManager.packages.remotePackages) {
        if (path !in localPaths && hasSystemImage(remotePackage)) {
          add(RemoteSystemImage(remotePackage))
        }
      }
    }
  }

  companion object {
    private fun hasSystemImage(repoPackage: RepoPackage): Boolean {
      val details = repoPackage.typeDetails
      return details is DetailsTypes.SysImgDetailsType ||
        details is DetailsTypes.PlatformDetailsType && details.apiLevel <= 13 ||
        details is DetailsTypes.AddonDetailsType && hasSystemImage(details)
    }

    private fun hasSystemImage(details: DetailsTypes.AddonDetailsType): Boolean =
      details.vendor.id == "google" && details.tag in SystemImageTags.TAGS_WITH_GOOGLE_API && details.apiLevel <= 19
  }
}
