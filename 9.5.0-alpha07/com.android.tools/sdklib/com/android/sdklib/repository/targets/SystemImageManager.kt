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
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** `SystemImageManager` finds [SystemImage]s in the sdk, using a [RepoManager]. */
class SystemImageManager(private val repoManager: RepoManager) {

  private var _imageMap: Multimap<LocalPackage, SystemImage>? = null

  /** Gets a map from all our [SystemImage]s to their containing [LocalPackage]s. */
  val imageMap: Multimap<LocalPackage, SystemImage>
    get() = _imageMap ?: buildImageMap().also { _imageMap = it }

  private var _pathToImage: Map<Path, SystemImage>? = null

  /** Map of directories containing `system.img` files to [SystemImage]s. */
  private val pathToImage: Map<Path, SystemImage>
    get() = _pathToImage ?: imageMap.values().associateBy { it.location }.also { _pathToImage = it }

  /** Gets all the [SystemImage]s. */
  val images: Collection<SystemImage>
    get() = imageMap.values()

  private fun buildImageMap(): Multimap<LocalPackage, SystemImage> {
    val result: Multimap<LocalPackage, SystemImage> = HashMultimap.create()
    val packages: Collection<LocalPackage> = repoManager.packages.localPackages.values
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

  private fun collectImages(dir: Path, localPackage: LocalPackage, collector: Multimap<LocalPackage, SystemImage>) {
    try {
      CancellableFileIo.walkFileTree(
        dir,
        emptySet(),
        MAX_DEPTH,
        object : SimpleFileVisitor<Path>() {
          override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            return when (dir.fileName?.toString()) {
              SdkConstants.FD_DATA,
              SdkConstants.FD_SAMPLES,
              SdkConstants.FD_SKINS -> FileVisitResult.SKIP_SUBTREE
              else -> FileVisitResult.CONTINUE
            }
          }

          override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (file.endsWith(SYS_IMG_NAME)) {
              file.parent?.let { collector.put(localPackage, createSysImg(localPackage, it)) }
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

  fun getImageAt(imageDir: Path): ISystemImage? {
    return pathToImage[imageDir]
  }

  fun clearCache() {
    _imageMap = null
    _pathToImage = null
  }

  companion object {
    const val SYS_IMG_NAME: String = "system.img"

    /** How far down the directory hierarchy we'll search for system images (starting from a package root). */
    private const val MAX_DEPTH = 4

    private fun getCpuFamily(abiString: String): String? = Abi.getEnum(abiString)?.displayName

    private fun readSysImgAbis(
      location: Path,
      details: DetailsTypes.SysImgDetailsType,
    ): AbiLists {
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
