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
package com.android.repository.impl.meta

import com.android.repository.api.LocalPackage
import com.android.repository.api.RemotePackage
import com.android.repository.api.UpdatablePackage
import com.google.common.collect.ImmutableSortedMap
import java.util.TreeMap
import javax.xml.bind.annotation.XmlTransient

/** Store of currently-known local and remote packages, in convenient forms. */
@XmlTransient
class RepositoryPackages() {
  constructor(localPkgs: Collection<LocalPackage>, remotePkgs: Collection<RemotePackage>) : this() {
    setLocalPkgInfos(localPkgs)
    setRemotePkgInfos(remotePkgs)
  }

  /** Map from `path` (the unique ID of a package) to [LocalPackage], including all installed packages. */
  var localPackages: ImmutableSortedMap<String, LocalPackage> = ImmutableSortedMap.of()
    private set

  /**
   * Map from `path` (the unique ID of a package) to [RemotePackage]. There may be more than one version of the same [RemotePackage]
   * available, for example if there is a stable and a preview version available.
   */
  var remotePackages: ImmutableSortedMap<String, RemotePackage> = ImmutableSortedMap.of()
    private set

  /** Map from `path` (the unique ID of a package) to [UpdatablePackage], including all packages installed or available. */
  private var _consolidatedPkgs: MutableMap<String, UpdatablePackage>? = TreeMap()

  private val lock = Any()

  /**
   * Returns the set of packages that have local updates available.
   *
   * @return A non-null, possibly empty Set of update candidates.
   */
  val updatedPkgs: Set<UpdatablePackage>
    get() = consolidatedPkgs.values.filterTo(HashSet()) { it.isUpdate }

  /**
   * Returns the set of new remote packages that are not locally present and that the user could install.
   *
   * @return A non-null, possibly empty Set of new install candidates.
   */
  val newPkgs: Set<RemotePackage>
    get() = consolidatedPkgs.values.filter { !it.hasLocal() }.mapTo(HashSet()) { it.remote!! }

  /**
   * Returns a map of package install ids to [UpdatablePackage]s representing all known local and remote packages. Remote packages
   * corresponding to local packages will be represented by a single item containing both the local and remote info.
   */
  val consolidatedPkgs: Map<String, UpdatablePackage>
    get() {
      synchronized(lock) {
        _consolidatedPkgs?.let {
          return it
        }
        return computeUpdates().also { _consolidatedPkgs = it }
      }
    }

  fun getLocalPackagesForPrefix(pathPrefix: String): Collection<LocalPackage> {
    return localPackages.tailMap(pathPrefix).values.takeWhile { it.path == pathPrefix || it.path.startsWith("$pathPrefix;") }
  }

  fun getRemotePackagesForPrefix(pathPrefix: String): Collection<RemotePackage> {
    return remotePackages.tailMap(pathPrefix).values.takeWhile { it.path == pathPrefix || it.path.startsWith("$pathPrefix;") }
  }

  /**
   * Sets the collection of known [LocalPackage]s, and recomputes the list of updates and new packages, if [RemotePackage]s have been set.
   */
  fun setLocalPkgInfos(packages: Collection<LocalPackage>) {
    synchronized(lock) {
      this.localPackages = ImmutableSortedMap.copyOf(packages.associateBy { it.path })
      invalidate()
    }
  }

  /**
   * Sets the collection of known [RemotePackage]s, and recomputes the list of updates and new packages, if [LocalPackage]s have been set.
   */
  fun setRemotePkgInfos(packages: Collection<RemotePackage>) {
    synchronized(lock) {
      this.remotePackages = ImmutableSortedMap.copyOf(packages.associateBy { it.path })
      invalidate()
    }
  }

  private fun invalidate() {
    _consolidatedPkgs = null
  }

  private fun computeUpdates(): TreeMap<String, UpdatablePackage> {
    val consolidatedPkgs = TreeMap<String, UpdatablePackage>()

    for (path in localPackages.keys + remotePackages.keys) {
      val local = localPackages[path]
      val remote = remotePackages[path]

      consolidatedPkgs[path] =
        when {
          local != null && remote != null -> UpdatablePackage(local, remote)
          local != null -> UpdatablePackage(local)
          remote != null -> UpdatablePackage(remote)
          else -> throw IllegalStateException() // not possible
        }
    }
    return consolidatedPkgs
  }
}
