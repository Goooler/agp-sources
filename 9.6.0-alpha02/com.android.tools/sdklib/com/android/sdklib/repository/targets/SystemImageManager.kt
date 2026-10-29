/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.sdklib.repository.targets

import com.android.SdkConstants
import com.android.io.CancellableFileIo
import com.android.repository.api.LocalPackage
import com.android.repository.api.RepoManager
import com.android.sdklib.ISystemImage
import com.android.sdklib.SystemImageTags
import com.android.sdklib.devices.Abi
import com.android.sdklib.repository.PackageParserUtils
import com.android.sdklib.repository.meta.DetailsTypes
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** `SystemImageManager` finds [SystemImage]s in the SDK, using a [RepoManager]. */
class SystemImageManager(repoManager: RepoManager) {

  private val _systemImages = MutableStateFlow(buildImageMap(repoManager.packages.localPackages.values))

  /** [StateFlow] of the current map of system image directory [Path]s to [SystemImage]s. */
  val systemImageFlow: StateFlow<Map<Path, SystemImage>> = _systemImages.asStateFlow()

  /** Gets all the [SystemImage]s. */
  val images: Collection<SystemImage>
    get() = systemImageFlow.value.values

  /**
   * Gets all [SystemImage]s contained in the given [localPackage]. While it is theoretically possible, no current SDK packages contain
   * multiple images.
   */
  fun getImagesInPackage(localPackage: LocalPackage): List<SystemImage> = images.filter { it.`package` == localPackage }

  /**
   * Gets the system image in the specified directory. Note that this is the directory containing system.img, not necessarily the top-level
   * package directory: platform and add-on packages have images nested deeper within the package.
   */
  fun getImageAt(imageDir: Path): ISystemImage? = systemImageFlow.value[imageDir]

  init {
    // This object has the same lifecycle as RepoManager, so we don't need to remove the listener.
    repoManager.addLocalChangeListener { repositoryPackages ->
      _systemImages.value = buildImageMap(repositoryPackages.localPackages.values)
    }
  }

  companion object {
    const val SYS_IMG_NAME: String = "system.img"

    private fun buildImageMap(packages: Collection<LocalPackage>): Map<Path, SystemImage> {
      val result = mutableMapOf<Path, SystemImage>()
      for (p in packages) {
        val typeDetails = p.typeDetails
        if (
          typeDetails is DetailsTypes.SysImgDetailsType ||
            typeDetails is DetailsTypes.PlatformDetailsType ||
            typeDetails is DetailsTypes.AddonDetailsType
        ) {
          collectImages(p.location, p, result)
        }
      }
      return result
    }

    private fun collectImages(dir: Path, localPackage: LocalPackage, collector: MutableMap<Path, SystemImage>) {
      if (CancellableFileIo.isRegularFile(dir.resolve(SYS_IMG_NAME))) {
        collector[createSysImg(localPackage, dir).location] = createSysImg(localPackage, dir)
        return
      }

      try {
        CancellableFileIo.walkFileTree(
          dir.resolve(SdkConstants.FD_IMAGES).takeIf { CancellableFileIo.isDirectory(it) } ?: return,
          emptySet(),
          2,
          object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
              if (CancellableFileIo.isRegularFile(dir.resolve(SYS_IMG_NAME))) {
                collector[createSysImg(localPackage, dir).location] = createSysImg(localPackage, dir)
                return FileVisitResult.SKIP_SUBTREE
              }
              return FileVisitResult.CONTINUE
            }
          },
        )
      } catch (_: IOException) {}
    }

    private fun createSysImg(localPackage: LocalPackage, dir: Path): SystemImage {
      val containingDir = dir.fileName.toString()
      val details = localPackage.typeDetails
      val (abis, translatedAbis) =
        if (details is DetailsTypes.SysImgDetailsType) {
          readSysImgAbis(localPackage.location, details)
        } else if (Abi.getEnum(containingDir) != null) {
          AbiLists(listOf(containingDir), emptyList())
        } else {
          AbiLists(listOf(SdkConstants.ABI_ARMEABI), emptyList())
        }

      val vendor =
        when (details) {
          is DetailsTypes.AddonDetailsType -> details.vendor
          is DetailsTypes.SysImgDetailsType -> details.vendor
          else -> null
        }

      val skinDir = dir.resolve(SdkConstants.FD_SKINS)
      val skins =
        when {
          CancellableFileIo.exists(skinDir) -> PackageParserUtils.parseSkinFolder(skinDir)
          else -> emptyList()
        }
      return SystemImage(dir, SystemImageTags.getTags(localPackage), vendor, abis, translatedAbis, skins, localPackage)
    }

    private fun getCpuFamily(abiString: String): String? = Abi.getEnum(abiString)?.displayName

    private fun readSysImgAbis(location: Path, details: DetailsTypes.SysImgDetailsType): AbiLists {
      val detailsClassName = details.javaClass.name
      if (
        detailsClassName.endsWith("v1.SysImgDetailsType") ||
          detailsClassName.endsWith("v2.SysImgDetailsType") ||
          detailsClassName.endsWith("v3.SysImgDetailsType")
      ) {
        // We have an old image, so we won't get more than one ABI from the XML. Read from disk.
        // We also know that there shouldn't be any unknown ABIs (they would use new XML).
        val allAbis = SystemImage.readAbisFromBuildProps(location)
        if (!allAbis.isNullOrEmpty()) {
          // Look at the architecture of the primary ABI to determine whether others are
          // translated or not.
          val primaryCpuFamily = getCpuFamily(allAbis[0])
          if (primaryCpuFamily != null) {
            val abis = mutableListOf<String>()
            val translatedAbis = mutableListOf<String>()
            for (abi in allAbis) {
              if (getCpuFamily(abi) == primaryCpuFamily) {
                abis.add(abi)
              } else {
                translatedAbis.add(abi)
              }
            }
            return AbiLists(abis, translatedAbis)
          }
        }
      }
      return AbiLists(details.abis, details.translatedAbis)
    }
  }
}

private data class AbiLists(val abis: List<String>, val translatedAbis: List<String>)
