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

package com.android.build.gradle.internal.dexing

import com.android.build.gradle.internal.services.R8ClassloaderBuildService
import com.android.builder.dexing.R8Tools
import com.android.builder.dexing.R8ToolsImpl
import java.io.File
import org.gradle.api.provider.Property

/**
 * Runs [block] with an isolated R8 [ClassLoader] for [r8Classpath], or with `null` when [r8Classpath] is empty (meaning: use AGP's own R8).
 *
 * The loader comes from [r8ClassloaderBuildService] when present (cached for the build); otherwise a one-off loader is created and closed
 * after [block] returns.
 */
internal inline fun <T> withR8ClassLoader(
  r8Classpath: Iterable<File>,
  r8ClassloaderBuildService: Property<R8ClassloaderBuildService>? = null,
  block: (ClassLoader?) -> T,
): T {
  val files = r8Classpath.toSet()
  if (files.isEmpty()) {
    return block(null)
  }
  if (r8ClassloaderBuildService != null && r8ClassloaderBuildService.isPresent) {
    return block(r8ClassloaderBuildService.get().getClassLoader(files))
  }
  return R8ClassloaderBuildService.createClassLoader(files).use { classLoader -> block(classLoader) }
}

/**
 * Returns the [R8Tools] to run R8 with: AGP's own [R8ToolsImpl] (bundled R8) when [r8ClassLoader] is null, or the [R8ToolsImpl] loaded by
 * the isolated [r8ClassLoader] (next to the Maven-resolved R8) otherwise.
 *
 * The isolated implementation is created by name because AGP's code can only refer to its own copy of [R8ToolsImpl]. [R8Tools] is loaded
 * from AGP's ClassLoader on both sides, so the result can be used without reflection.
 */
internal fun createR8Tools(r8ClassLoader: ClassLoader?): R8Tools =
  if (r8ClassLoader == null) {
    R8ToolsImpl()
  } else {
    r8ClassLoader.loadClass("com.android.builder.dexing.R8ToolsImpl").getConstructor().newInstance() as R8Tools
  }

/**
 * Runs [block], which calls into [R8Tools] created for [r8ClassLoader].
 *
 * On the isolated path ([r8ClassLoader] is not null), linkage/reflection failures from inside R8 are reported as a likely R8 version
 * mismatch. Everything else, and every failure on the bundled path, is rethrown unchanged.
 */
internal inline fun <T> wrapIsolatedR8Failures(r8ClassLoader: ClassLoader?, block: () -> T): T {
  try {
    return block()
  } catch (e: Throwable) {
    if (r8ClassLoader != null && (e is LinkageError || e is NoSuchMethodException || e is NoSuchFieldException)) {
      throw RuntimeException(
        "Failed to execute R8 via isolated ClassLoader. The configured R8 version may be " +
          "incompatible with this version of the Android Gradle Plugin: ${e.message}",
        e,
      )
    }
    throw e
  }
}
