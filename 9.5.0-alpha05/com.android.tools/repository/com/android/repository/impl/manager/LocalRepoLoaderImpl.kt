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
package com.android.repository.impl.manager

import com.android.ProgressManagerAdapter
import com.android.io.CancellableFileIo
import com.android.repository.api.FallbackLocalRepoLoader
import com.android.repository.api.LocalPackage
import com.android.repository.api.ProgressIndicator
import com.android.repository.api.RepoPackage
import com.android.repository.api.Repository
import com.android.repository.api.SchemaModule
import com.android.repository.impl.installer.AbstractPackageOperation
import com.android.repository.impl.meta.LocalPackageImpl
import com.android.repository.impl.meta.SchemaModuleUtil
import com.google.common.annotations.VisibleForTesting
import com.google.common.hash.Hashing
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.TreeSet
import javax.xml.bind.JAXBException

/** A utility class that finds [LocalPackage]s under a given path based on `package.xml` files. */
class LocalRepoLoaderImpl
@JvmOverloads
constructor(
  /** Directory under which we look for packages. */
  private val root: Path,
  schemaModules: Set<SchemaModule<*>>,
  /**
   * If we can't find a package in a directory, we ask [fallback] to find one. If it does, we write out a `package.xml` so we can read it
   * next time.
   */
  private val fallback: FallbackLocalRepoLoader? = null,
) : LocalRepoLoader {
  private val schemaModules: Set<SchemaModule<*>> = schemaModules.toSet()

  override fun getPackages(progress: ProgressIndicator): Map<String, LocalPackage> {
    fallback?.refresh()
    val possiblePackageDirs = collectPackages()
    val packages = parsePackages(possiblePackageDirs, progress)
    if (packages.isNotEmpty()) {
      writeHashFile(getLocalPackagesHash(possiblePackageDirs))
      val packageNames = packages.keys.sorted().joinToString(" ")
      progress.logVerbose("SDK Manager found the following installed packages: $packageNames")
    }
    return packages
  }

  /**
   * {@inheritDoc}
   *
   * If `deepCheck` is `false`, we just check whether `.knownPackages` has been updated more recently than `lastLocalRefreshMs`. If
   * `deepCheck` is `true`, we check whether the hash in `.knownPackages` accurately reflects the currently-installed packages (traversing
   * the SDK directory tree to do so).
   */
  override fun needsUpdate(lastLocalRefreshMs: Long, deepCheck: Boolean): Boolean {
    return checkKnownPackagesUpdateTime(lastLocalRefreshMs) || (deepCheck && updateKnownPackageHashFileIfNecessary())
  }

  private fun parsePackages(possiblePackageDirs: Collection<Path>, progress: ProgressIndicator): MutableMap<String, LocalPackage> {
    val result = mutableMapOf<String, LocalPackage>()
    for (packageDir in possiblePackageDirs) {
      val packageXml: Path = packageDir.resolve(PACKAGE_XML_FN)
      var p: LocalPackage? = null
      if (CancellableFileIo.exists(packageXml)) {
        try {
          p = parsePackage(packageXml, progress)
        } catch (e: Exception) {
          ProgressManagerAdapter.throwIfCancellation(e)
          // There was a problem parsing the package. Try the fallback loader.
          progress.logWarning("Found corrupted package.xml at $packageXml")
        }
      }
      // Note: Android Studio 2.x was generating a local package.xml file with "Unknown" display name
      // if the name could not be found in source.properties. For AS 3.x we are extending the code
      // to be less strict (ie we use info from manifest.ini too). Checking "Unknown" allows re-generation
      // of package.xml. This check for "Unknown" can be removed after most users have updated to v3.x.
      if ((p == null || p.displayName.startsWith("Unknown")) && fallback != null) {
        p = fallback.parseLegacyLocalPackage(packageDir, progress)
        if (p != null) {
          writePackage(p, packageXml, progress)
        } else if (CancellableFileIo.exists(packageXml)) {
          progress.logWarning("Invalid package.xml found at $packageXml and failed to parse using fallback.")
          /*
          TODO: decide what the behavior should be when an xml is consistently unparsable.
                Leaving it as-is (the above code) will cause there to be a warning each time
                we try to parse the package. But renaming it means we never get a chance
                (e.g. with a future version of the code) to try to recover.
          File bad = new File(packageXml.getPath() + ".bad");
          progress.logWarning(String.format(
                  "Invalid package.xml found and failed to parse using fallback. Renaming %1$s to %2$s",
                  packageXml, bad));
          mFop.renameTo(packageXml, bad);
          */
        }
      }
      if (p != null) {
        addPackage(p, result, progress)
      }
    }
    return result
  }

  /** Gets a sorted set of all paths that might contain packages. */
  private fun collectPackages(): Set<Path> {
    val dirs = TreeSet<Path>()
    collectPackages(dirs, root, 0)
    return dirs
  }

  /**
   * Collect packages under the given root into `collector`.
   *
   * @param collector The collector.
   * @param root Directory we're looking in.
   * @param depth The depth we've descended to so far. Once we reach [MAX_SCAN_DEPTH] we'll stop recursing.
   */
  private fun collectPackages(collector: MutableCollection<Path>, root: Path, depth: Int) {
    if (depth > MAX_SCAN_DEPTH) {
      return
    }
    // Do not scan metadata folders and return right away. Allow the SDK root to start with the
    // prefix though.
    if (
      root != this.root &&
        CancellableFileIo.isDirectory(root) &&
        root.fileName.toString().startsWith(AbstractPackageOperation.METADATA_FILENAME_PREFIX)
    ) {
      return
    }

    val packageXml: Path = root.resolve(PACKAGE_XML_FN)
    if (CancellableFileIo.exists(packageXml) || fallback?.shouldParse(root) == true) {
      collector.add(root)
    } else {
      try {
        val cachePaths = resourceCachePaths()
        CancellableFileIo.list(root).use { contents ->
          contents
            .filter { file -> CancellableFileIo.isDirectory(file) && file !in cachePaths }
            .forEach { file -> collectPackages(collector, file, depth + 1) }
        }
      } catch (_: IOException) {
        // don't add anything
      }
    }
  }

  private fun addPackage(p: LocalPackage, collector: MutableMap<String, LocalPackage>, progress: ProgressIndicator) {
    val filePath = p.path.replace(RepoPackage.PATH_SEPARATOR, File.separatorChar)
    val desired = root.resolve(filePath)
    val actual = p.location
    if (desired != actual) {
      progress.logWarning("Observed package id '${p.path}' in inconsistent location '$actual' (Expected '$desired')")
      val existing = collector[p.path]
      if (existing != null) {
        progress.logWarning("Already observed package id '${p.path}' in '${existing.location}'. Skipping duplicate at '$actual'")
        return
      }
    }
    collector[p.path] = p
  }

  /**
   * If the [FallbackLocalRepoLoader] finds a package, we write out a package.xml so we can load it next time without falling back.
   *
   * @param p The [LocalPackage] to write out.
   * @param packageXml The destination to write to.
   * @param progress [ProgressIndicator] for logging.
   */
  private fun writePackage(p: LocalPackage, packageXml: Path, progress: ProgressIndicator) {
    // We need a LocalPackageImpl to be able to save it.
    val impl = LocalPackageImpl.create(p)
    try {
      Files.newOutputStream(packageXml, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { fos
        ->
        val repo = impl.createFactory().createRepositoryType()
        repo.setLocalPackage(impl)
        impl.license?.let { repo.addLicense(it) }

        val factory = p.createFactory()
        SchemaModuleUtil.marshal(
          factory.generateRepository(repo),
          schemaModules,
          fos,
          SchemaModuleUtil.createResourceResolver(schemaModules, progress),
          progress,
          false,
        )
      }
    } catch (e: IOException) {
      progress.logInfo("Exception while marshalling $packageXml. Probably the SDK is read-only")
    }
  }

  /** Unmarshal a package.xml file and extract the [LocalPackage]. */
  @Throws(JAXBException::class)
  private fun parsePackage(packageXml: Path, progress: ProgressIndicator): LocalPackage? {
    val repo: Repository? =
      try {
        CancellableFileIo.newInputStream(packageXml).use { stream ->
          SchemaModuleUtil.unmarshal(stream, schemaModules, false, progress, packageXml.fileName.toString()) as Repository?
        }
      } catch (e: IOException) {
        progress.logError("XML file $packageXml doesn't exist", e)
        return null
      }
    if (repo == null) {
      progress.logWarning("Failed to parse $packageXml")
      return null
    }
    val p = repo.localPackage
    if (p == null) {
      progress.logWarning("Didn't find any local package in repository")
      return null
    }
    p.setInstalledPath(packageXml.parent)
    return p
  }

  /**
   * Gets a reference to the known packages file, creating it if necessary.
   *
   * @return The file, or `null` if it doesn't exist and couldn't be created.
   */
  private fun getKnownPackagesHashFile(create: Boolean): Path? {
    val f: Path = root.resolve(KNOWN_PACKAGES_HASH_FN)
    if (CancellableFileIo.notExists(f)) {
      if (!create) return null
      try {
        Files.createDirectories(f.parent)
        Files.createFile(f)
      } catch (e: IOException) {
        return null
      }
    }
    return f
  }

  /**
   * Updates the known packages file with the hash of the current packages.
   *
   * @return `true` if the existing hash does not match the expected one (that is, a reload is required).
   */
  private fun updateKnownPackageHashFileIfNecessary(): Boolean {
    val packages = collectPackages()
    val localPackagesHash = getLocalPackagesHash(packages)
    val knownPackagesHashFile = getKnownPackagesHashFile(false)
    if (knownPackagesHashFile != null) {
      // If we haven't updated any package more recently than the file, check the file
      // contents as well before updating. Otherwise we'll always update the file.
      if (getLatestPackageUpdateTime(packages) <= getLastModifiedTime(knownPackagesHashFile)) {
        try {
          val buf = CancellableFileIo.readAllBytes(knownPackagesHashFile)
          if (buf.contentEquals(localPackagesHash)) {
            return false
          }
        } catch (_: IOException) {}
      }
    }
    writeHashFile(localPackagesHash)
    // Even if writing the hash file fails, we still know that we're out of date and
    // should be reloaded, so still return true.
    return true
  }

  private fun getLastModifiedTime(file: Path): Long {
    return try {
      CancellableFileIo.getLastModifiedTime(file).toMillis()
    } catch (e: IOException) {
      0L
    }
  }

  /** Actually writes the data to the hash file. */
  private fun writeHashFile(buf: ByteArray) {
    val knownPackagesHashFile = getKnownPackagesHashFile(true) ?: return
    try {
      Files.write(knownPackagesHashFile, buf)
    } catch (ignore: IOException) {}
  }

  /**
   * Check to see whether the known packages file has been updated since we last loaded the local repo.
   *
   * @return `true` if it has been updated (and thus we should reload our local packages).
   */
  private fun checkKnownPackagesUpdateTime(lastUpdate: Long): Boolean {
    val knownPackagesHashFile = getKnownPackagesHashFile(false)
    return knownPackagesHashFile == null || getLastModifiedTime(knownPackagesHashFile) > lastUpdate
  }

  /**
   * Gets a hash of the known (suspected) package directories. In order to be as fast as possible this doesn't include the content of the
   * packages or package metadata file, just the directories paths themselves.
   */
  private fun getLocalPackagesHash(packages: Collection<Path>): ByteArray {
    @Suppress("DEPRECATION") val digester = Hashing.md5().newHasher()
    for (f in packages) {
      digester.putBytes(f.toAbsolutePath().toString().toByteArray(StandardCharsets.UTF_8))
    }
    return digester.hash().asBytes()
  }

  /** Finds the latest update timestamp of a `package.xml` file under [[root]]. */
  private fun getLatestPackageUpdateTime(packages: Collection<Path>): Long {
    return packages.maxOfOrNull { getLastModifiedTime(it.resolve(PACKAGE_XML_FN)) } ?: 0L
  }

  /** Returns the paths where we cache resources. */
  private fun resourceCachePaths(): Set<Path> {
    return RESOURCE_CACHE_DIRS.mapTo(HashSet()) { root.resolve(it) }
  }

  companion object {
    /** The name of the package metadata file we can read. */
    const val PACKAGE_XML_FN: String = "package.xml"

    /**
     * The maximum depth we'll descend into the directory tree while looking for packages. TODO: adjust once the path of the current deepest
     * package is known (e.g. maven packages).
     */
    private const val MAX_SCAN_DEPTH = 10

    /** The name of the file where we store a hash of the known packages, used for invalidating the cache. */
    @VisibleForTesting const val KNOWN_PACKAGES_HASH_FN: String = ".knownPackages"

    /** Top-level directories where resources are cached; these should not be scanned for packages. */
    private val RESOURCE_CACHE_DIRS = setOf("fonts", "icons", "skins")
  }
}
