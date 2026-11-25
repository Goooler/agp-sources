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

package com.android.build.gradle.internal.services

import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.concurrent.ThreadSafe
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Build service used to instantiate and cache isolated [URLClassLoader] instances for the R8 compiler.
 *
 * R8 is not on AGP's own ClassLoader; the created ClassLoader loads the Maven-resolved R8 alongside `builder-r8` child-first, while
 * delegating shared AGP and JDK classes to AGP's ClassLoader.
 */
@ThreadSafe
abstract class R8ClassloaderBuildService : BuildService<BuildServiceParameters.None>, AutoCloseable {

  private val classLoaderCache = ConcurrentHashMap<List<File>, URLClassLoader>()

  /** Retrieves or creates an isolated [ClassLoader] containing the provided [r8Classpath]. */
  fun getClassLoader(r8Classpath: Set<File>): ClassLoader {
    val key = r8Classpath.map { it.canonicalFile }.sortedBy { it.path }
    return classLoaderCache.computeIfAbsent(key) { files -> createClassLoader(files) }
  }

  @VisibleForTesting fun getCachedClassLoaderCount(): Int = classLoaderCache.size

  override fun close() {
    for (classLoader in classLoaderCache.values) {
      try {
        classLoader.close()
      } catch (_: Throwable) {
        // Ignored during shutdown
      }
    }
    classLoaderCache.clear()
  }

  companion object {
    /**
     * Types that appear in `R8Tools` signatures (e.g. `runR8`, or the D8 dex builder and merger and the entries they read), directly or
     * inside another type, and that don't touch com.android.tools.r8.*. If one did reference R8, the parent copy would fail to link because
     * R8 is not on AGP's ClassLoader (or would link against a buildscript-classpath R8 instead of the isolated one). Anything that touches
     * R8 must be loaded by the child, so it links against the Maven-resolved R8 in this ClassLoader. The isolated loader takes these from
     * the parent (AGP's) loader, so AGP can pass its own objects to the child-loaded `builder-r8` code. Nested classes (e.g.
     * `KeepRuleFile$MavenOrigin`) are matched by their outer class.
     */
    private val PARENT_DELEGATED_DEXING_CLASSES =
      setOf(
        "com.android.builder.dexing.ClassFileEntry",
        "com.android.builder.dexing.ClassFileInput",
        "com.android.builder.dexing.DependencyGraphUpdater",
        "com.android.builder.dexing.DexArchive",
        "com.android.builder.dexing.DexArchiveBuilder",
        "com.android.builder.dexing.DexArchiveBuilderException",
        "com.android.builder.dexing.DexArchiveEntry",
        "com.android.builder.dexing.DexArchiveMerger",
        "com.android.builder.dexing.DexArchiveMergerException",
        "com.android.builder.dexing.DexingType",
        "com.android.builder.dexing.KeepRuleFile",
        "com.android.builder.dexing.KeepRulesConfig",
        "com.android.builder.dexing.L8OutputMode",
        "com.android.builder.dexing.MainDexListConfig",
        "com.android.builder.dexing.PartialShrinking",
        "com.android.builder.dexing.PartialShrinkingConfig",
        "com.android.builder.dexing.PartialShrinkingIncludeAll",
        "com.android.builder.dexing.ProguardConfig",
        "com.android.builder.dexing.ProguardOutputFiles",
        "com.android.builder.dexing.ProguardOutputReports",
        "com.android.builder.dexing.R8OutputType",
        "com.android.builder.dexing.R8Tools",
        "com.android.builder.dexing.ResourceShrinkingConfig",
        "com.android.builder.dexing.ToolConfig",
      )

    /**
     * The only R8-free class in `com.android.builder.multidex` that crosses the isolated ClassLoader boundary: the exception main dex list
     * generation throws. Matched by exact binary name, because the enclosing `D8MainDexList` and its other nested class use R8 and must be
     * loaded child-first.
     */
    private const val MAIN_DEX_LIST_EXCEPTION_CLASS = "com.android.builder.multidex.D8MainDexList\$MainDexListException"

    private fun shouldLoadChildFirst(name: String): Boolean {
      if (name.startsWith("com.android.tools.r8.")) {
        return true
      }
      if (name.startsWith("com.android.builder.dexing.")) {
        return name.substringBefore('$') !in PARENT_DELEGATED_DEXING_CLASSES
      }
      if (name.startsWith("com.android.builder.multidex.")) {
        return name != MAIN_DEX_LIST_EXCEPTION_CLASS
      }
      return false
    }

    /** Creates a new isolated [URLClassLoader] for the provided [files]. */
    fun createClassLoader(files: Collection<File>): URLClassLoader {
      val builderR8Url =
        try {
          Class.forName("com.android.builder.dexing.R8Tool").protectionDomain?.codeSource?.location
        } catch (_: Throwable) {
          null
        }
      val urls = (files.map { it.canonicalFile.toURI().toURL() } + listOfNotNull(builderR8Url)).toTypedArray()
      return R8ClassLoader(urls, R8ClassloaderBuildService::class.java.classLoader)
    }
  }

  private class R8ClassLoader(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {
    companion object {
      init {
        // Many parallel D8 workers share one cached R8ClassLoader. Registering it as parallel capable makes getClassLoadingLock() return a
        // lock per class name instead of locking the whole loader, so workers loading different classes don't block each other. This must
        // run in R8ClassLoader's static initializer (Kotlin puts companion init blocks there).
        registerAsParallelCapable()
      }
    }

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
      synchronized(getClassLoadingLock(name)) {
        var c = findLoadedClass(name)
        if (c == null) {
          if (shouldLoadChildFirst(name)) {
            try {
              c = findClass(name)
            } catch (_: ClassNotFoundException) {
              // Fall back to parent
            }
          }
          if (c == null) {
            c = super.loadClass(name, resolve)
          }
        }
        if (resolve) {
          resolveClass(c)
        }
        return c
      }
    }
  }

  class RegistrationAction(project: Project) :
    ServiceRegistrationAction<R8ClassloaderBuildService, BuildServiceParameters.None>(project, R8ClassloaderBuildService::class.java) {

    override fun configure(parameters: BuildServiceParameters.None) {}
  }
}
