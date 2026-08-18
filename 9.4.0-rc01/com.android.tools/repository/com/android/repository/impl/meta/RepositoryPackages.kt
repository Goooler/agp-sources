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
import com.android.repository.api.RepoPackage
import com.android.repository.api.UpdatablePackage
import com.android.repository.util.getAllRepoPackagePrefixes
import com.google.common.collect.Multimap
import com.google.common.collect.TreeMultimap
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
  var localPackages: Map<String, LocalPackage> = mutableMapOf()
    private set

  /**
   * Map from `path` (the unique ID of a package) to [RemotePackage]. There may be more than one version of the same [RemotePackage]
   * available, for example if there is a stable and a preview version available.
   */
  var remotePackages: Map<String, RemotePackage> = TreeMap()
    private set

  /** Multimap from all prefixes of `path`s (the unique IDs of packages) to [LocalPackage]s with that path prefix. */
  private var mLocalPackagesByPrefix: Multimap<String, LocalPackage> = TreeMultimap.create()

  /** Multimap from all prefixes of `path`s (the unique IDs of packages) to [RemotePackage]s with that path prefix. */
  private var mRemotePackagesByPrefix: Multimap<String, RemotePackage> = TreeMultimap.create()

  /** All the packages that are locally-installed and have a remotely-available update. */
  private var mUpdatedPkgs: MutableSet<UpdatablePackage>? = sortedSetOf()

  /** All the packages that are available remotely and don't have an installed version. */
  private var mNewPkgs: MutableSet<RemotePackage>? = sortedSetOf()

  /** Map from `path` (the unique ID of a package) to [UpdatablePackage], including all packages installed or available. */
  private var mConsolidatedPkgs: MutableMap<String, UpdatablePackage>? = TreeMap()

  private val mLock = Any()

  /**
   * Returns the set of packages that have local updates available.
   *
   * @return A non-null, possibly empty Set of update candidates.
   */
  val updatedPkgs: Set<UpdatablePackage>
    get() {
      synchronized(mLock) {
        if (mUpdatedPkgs == null) {
          computeUpdates()
        }
        return mUpdatedPkgs!!
      }
    }

  /**
   * Returns the set of new remote packages that are not locally present and that the user could install.
   *
   * @return A non-null, possibly empty Set of new install candidates.
   */
  val newPkgs: Set<RemotePackage>
    get() {
      synchronized(mLock) {
        if (mNewPkgs == null) {
          computeUpdates()
        }
        return mNewPkgs!!
      }
    }

  /**
   * Returns a map of package install ids to [UpdatablePackage]s representing all known local and remote packages. Remote packages
   * corresponding to local packages will be represented by a single item containing both the local and remote info. {@see *
   * IPkgDesc#getInstallId()}
   */
  val consolidatedPkgs: Map<String, UpdatablePackage>
    get() {
      synchronized(mLock) {
        if (mConsolidatedPkgs == null) {
          computeUpdates()
        }
        return mConsolidatedPkgs!!
      }
    }

  fun getLocalPackagesForPrefix(pathPrefix: String?): Collection<LocalPackage> {
    return pathPrefix?.let { mLocalPackagesByPrefix.get(it) } ?: emptyList()
  }

  fun getRemotePackagesForPrefix(pathPrefix: String?): Collection<RemotePackage> {
    return pathPrefix?.let { mRemotePackagesByPrefix.get(it) } ?: emptyList()
  }

  /**
   * Sets the collection of known [LocalPackage]s, and recomputes the list of updates and new packages, if [RemotePackage]s have been set.
   */
  fun setLocalPkgInfos(packages: Collection<LocalPackage>) {
    synchronized(mLock) {
      this.localPackages = packages.associateBy { it.path }
      invalidate()
      mLocalPackagesByPrefix = computePackagePrefixes(this.localPackages)
    }
  }

  /**
   * Sets the collection of known [RemotePackage]s, and recomputes the list of updates and new packages, if [LocalPackage]s have been set.
   */
  fun setRemotePkgInfos(packages: Collection<RemotePackage>) {
    synchronized(mLock) {
      this.remotePackages = packages.associateByTo(TreeMap()) { it.path }
      invalidate()
      mRemotePackagesByPrefix = computePackagePrefixes(this.remotePackages)
    }
  }

  private fun invalidate() {
    mConsolidatedPkgs = null
    mNewPkgs = null
    mUpdatedPkgs = null
  }

  private fun computeUpdates() {
    val newConsolidatedPkgs = TreeMap<String, UpdatablePackage>()
    val updates = mutableSetOf<UpdatablePackage>()

    for ((path, local) in localPackages) {
      val updatable = UpdatablePackage(local)
      newConsolidatedPkgs[path] = updatable
      remotePackages[path]?.let { remote ->
        updatable.setRemote(remote)
        if (updatable.isUpdate) {
          updates.add(updatable)
        }
      }
    }

    val news = mutableSetOf<RemotePackage>()
    for ((path, remote) in remotePackages) {
      if (!newConsolidatedPkgs.containsKey(path)) {
        news.add(remote)
        val updatable = UpdatablePackage(remote)
        newConsolidatedPkgs[path] = updatable
      }
    }

    mNewPkgs = news
    mUpdatedPkgs = updates
    mConsolidatedPkgs = newConsolidatedPkgs
  }

  companion object {
    private fun <P : RepoPackage> computePackagePrefixes(packages: Map<String, P>): Multimap<String, P> {
      val packagesByPrefix: Multimap<String, P> = TreeMultimap.create()
      for ((path, p) in packages) {
        val prefixes = getAllRepoPackagePrefixes(path)
        for (prefix in prefixes) {
          packagesByPrefix.put(prefix, p)
        }
      }
      return packagesByPrefix
    }
  }
}
