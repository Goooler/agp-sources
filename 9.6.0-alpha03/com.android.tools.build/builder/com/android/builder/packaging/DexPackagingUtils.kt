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

package com.android.builder.packaging

import com.android.builder.files.RelativeFile
import java.io.File

private val CLASSES_DEX_PATTERN = Regex("^classes([0-9]*)\\.dex$")

private fun getClassesDexNumber(name: String): Int {
  val match = CLASSES_DEX_PATTERN.matchEntire(name) ?: return Int.MAX_VALUE
  val numberStr = match.groupValues[1]
  return if (numberStr.isEmpty()) 1 else numberStr.toIntOrNull() ?: Int.MAX_VALUE
}

/** Comparator that compares dex file paths, placing classesN.dex files first sorted by N. */
object DexFileComparator : Comparator<File> {

  override fun compare(file1: File, file2: File): Int {
    val n1 = getClassesDexNumber(file1.name)
    val n2 = getClassesDexNumber(file2.name)
    return if (n1 != n2) n1.compareTo(n2) else file1.absolutePath.compareTo(file2.absolutePath)
  }
}

/** Comparator that compares dex file paths, placing classes.dex always in front. */
object DexRelativeFileComparator : Comparator<RelativeFile> {

  override fun compare(file1: RelativeFile, file2: RelativeFile): Int {
    return DexFileComparator.compare(file1.getFile(), file2.getFile())
  }
}
