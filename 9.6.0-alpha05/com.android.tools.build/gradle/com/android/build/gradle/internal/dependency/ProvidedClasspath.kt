/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("ProvidedClasspath")

package com.android.build.gradle.internal.dependency

import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.FileCollection

fun getProvidedClasspath(compileClasspath: ArtifactCollection, runtimeClasspath: ArtifactCollection): FileCollection =
  ProvidedClasspathSubtractor(compileClasspath, runtimeClasspath).getFileCollection()

/**
 * Computes [FileCollection] containing all files in the provided classpath. Invoke [getFileCollection] to compute that file collection.
 *
 * All artifacts in the compile classpath, whose component variant is not in the runtime classpath are in the provided classpath. E.g. for
 * library projects that publish two separate files to compile and runtime configurations, compile artifact will not be in the provided
 * classpath. If file collection subtraction were to be used, compile artifact would be in the provided classpath, which is not what we
 * want.
 *
 * A component variant is identified by its component identifier together with its capabilities, as a single component can expose several
 * variants with different capabilities that are selected independently, e.g. a project and its test fixtures. Matching on the component
 * identifier only would cause e.g. the tested app's classes (compile classpath) to be dropped from the provided classpath of its
 * androidTest component when the app's test fixtures are on the runtime classpath (b/548701041). Conversely, if the compile and runtime
 * variants of a component were to declare different capabilities, its compile artifact would now be part of the provided classpath.
 */
private class ProvidedClasspathSubtractor(
  private val compileClasspath: ArtifactCollection,
  private val runtimeClasspath: ArtifactCollection,
) : Serializable {

  @Transient private var fileToIds = lazy { this.getFileToIds() }
  @Transient private var excludes = lazy { this.getExcludes() }

  fun getFileCollection(): FileCollection {
    return compileClasspath.artifactFiles.filter { f -> isSatisfiedBy(f) }
  }

  private fun isSatisfiedBy(file: File): Boolean {
    val fileIds = fileToIds.value.get(file)
    return !excludes.value.containsAll(fileIds)
  }

  private fun getFileToIds(): Multimap<File, ComponentVariantKey> {
    val info: Multimap<File, ComponentVariantKey> =
      Multimaps.newSetMultimap(HashMap(compileClasspath.artifacts.size)) { HashSet<ComponentVariantKey>() }

    for (artifact in compileClasspath.artifacts) {
      info.put(artifact.file, artifact.toComponentVariantKey())
    }
    return info
  }

  private fun getExcludes(): Set<ComponentVariantKey> {
    val excludes = HashSet<ComponentVariantKey>(runtimeClasspath.artifacts.size)
    for (runtimeArtifacts in runtimeClasspath.artifacts) {
      excludes.add(runtimeArtifacts.toComponentVariantKey())
    }
    return excludes
  }

  private fun writeObject(objectOutputStream: ObjectOutputStream) {
    objectOutputStream.defaultWriteObject()
  }

  private fun readObject(objectInputStream: ObjectInputStream) {
    objectInputStream.defaultReadObject()
    fileToIds = lazy { this.getFileToIds() }
    excludes = lazy { this.getExcludes() }
  }
}

/**
 * Identifies the variant of a component an artifact belongs to. Capability versions are ignored, as the version is already part of the
 * component identifier for external modules, and is not relevant for project dependencies.
 */
private data class ComponentVariantKey(val componentIdentifier: ComponentIdentifier, val capabilities: Set<String>)

private fun ResolvedArtifactResult.toComponentVariantKey() =
  ComponentVariantKey(id.componentIdentifier, variant.capabilities.mapTo(HashSet()) { "${it.group}:${it.name}" })
