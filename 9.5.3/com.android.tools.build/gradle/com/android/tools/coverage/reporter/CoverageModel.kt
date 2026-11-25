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

package com.android.tools.coverage.reporter

import java.util.TreeMap

/**
 * Domain model for a code coverage report.
 *
 * Holds the execution status of a single metric (e.g., instructions, lines). Missed items are those not touched by any test; covered items
 * were hit at least once.
 *
 * @property covered The number of items hit during execution.
 * @property missed The number of items not hit during execution.
 */
data class Counter(var covered: Int = 0, var missed: Int = 0) {
  /** Returns the total number of items (covered + missed). */
  val total: Int
    get() = covered + missed

  /** Adds the values of another counter to this one. */
  fun add(other: Counter) {
    covered += other.covered
    missed += other.missed
  }
}

/**
 * Fine-grained statistics for a single source line.
 *
 * @property ci Covered instructions.
 * @property mi Missed instructions.
 * @property cb Covered branches.
 * @property mb Missed branches.
 */
class LineStats(var ci: Int = 0, var mi: Int = 0, var cb: Int = 0, var mb: Int = 0)

/**
 * Base class for all model elements that hold coverage counters.
 *
 * @property instructions Instruction-level coverage.
 * @property lines Line-level coverage.
 * @property branches Branch-level coverage.
 * @property methodsCounter Method-level coverage status.
 */
open class CoverageElement {
  val instructions = Counter()
  val lines = Counter()
  val branches = Counter()
  val methodsCounter = Counter()
}

/**
 * Base class for elements that also aggregate class counts.
 *
 * @property classesCounter Class-level coverage status.
 */
open class AggregateElement : CoverageElement() {
  val classesCounter = Counter()
}

/**
 * Representation of a single method within a class.
 *
 * @property name Internal method name (e.g., "onCreate").
 * @property desc Method descriptor (e.g., "(Landroid/os/Bundle;)V").
 * @property line The starting line number in the source file.
 */
class MethodModel(val name: String, val desc: String, val line: Int) : CoverageElement()

/**
 * Representation of a Java or Kotlin class.
 *
 * @property name Internal class name (e.g., "com/example/MainActivity").
 * @property sourceFilename Name of the file containing this class (e.g., "MainActivity.kt").
 * @property methods List of methods belonging to this class.
 */
class ClassModel(val name: String, val sourceFilename: String) : AggregateElement() {
  val methods = mutableListOf<MethodModel>()
}

/**
 * Aggregated data for a source file, potentially containing multiple classes.
 *
 * @property name File name (e.g., "Utils.kt").
 * @property lineMap Map of line numbers to their detailed statistics.
 */
class SourceFileModel(val name: String) : AggregateElement() {
  val lineMap = TreeMap<Int, LineStats>()
}

/**
 * Aggregated data for a package.
 *
 * @property name Package internal name (e.g., "com/example").
 * @property classes Map of class names to their models.
 * @property sourceFiles Map of file names to their models.
 */
class PackageModel(val name: String) : AggregateElement() {
  val classes = TreeMap<String, ClassModel>()
  val sourceFiles = TreeMap<String, SourceFileModel>()
}

/**
 * The root model of the coverage report.
 *
 * @property name The report name (e.g., "debug").
 * @property packages Map of package names to their models.
 */
class ReportModel(val name: String) : AggregateElement() {
  val packages = TreeMap<String, PackageModel>()
}
