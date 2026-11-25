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
package com.android.repository.api

/**
 * Represents a (revisionless) package, either local, remote, or both. If both a local and remote package are specified, they should
 * represent exactly the same package, excepting the revision. That is, the result of installing the remote package should be (a possibly
 * updated version of) the local package.
 */
class UpdatablePackage(val local: LocalPackage?, val remote: RemotePackage?) : Comparable<UpdatablePackage> {

  constructor(local: LocalPackage) : this(local, null)

  constructor(remote: RemotePackage) : this(null, remote)

  init {
    require(local != null || remote != null) { "At least one of local or remote package must be specified." }
    require(local == null || remote == null || local.path == remote.path) { "Local and remote paths cannot be different." }
  }

  fun hasRemote(): Boolean = remote != null

  fun hasLocal(): Boolean = local != null

  override fun compareTo(other: UpdatablePackage): Int = representative.compareTo(other.representative)

  /**
   * Gets a [RepoPackage] (either local or remote) corresponding to this updatable package. This will be the local package if there is one,
   * and the remote otherwise.
   */
  val representative: RepoPackage
    get() = local ?: remote ?: error("UpdatablePackage must have a local or remote package")

  /** Indicates that this package is installed and a newer version is available. */
  val isUpdate: Boolean
    get() {
      val local = local ?: return false
      val remote = remote ?: return false
      return local.version < remote.version
    }

  /** The [path][RepoPackage.getPath] of the local and/or remote package. */
  val path: String
    get() = representative.path
}
