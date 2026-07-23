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

/** Service for aggregating raw binary coverage data into a structured report model. */
class ReportAggregator {

  /**
   * Transforms raw [CoverageData] into a [ReportModel].
   *
   * @param data The raw coverage data (hits + metadata).
   * @param reportName The name of the report.
   * @param testPackageId Optional package ID of the test app to filter out.
   * @param exclusions Optional set of internal class names or package prefixes to exclude.
   */
  fun aggregate(data: CoverageData, reportName: String, testPackageId: String? = null, exclusions: Set<String> = emptySet()): ReportModel {
    val report = ReportModel(reportName)
    val testPackagePrefix = testPackageId?.replace('.', '/')?.let { if (it.endsWith('/')) it else "$it/" }
    val internalExclusions = exclusions.map { it.replace('.', '/') }.toSet()

    // Step 1: Initial population from Classes
    for (classMeta in data.metadata.classesList) {
      val smapResolver = SmapResolver(classMeta.smap)
      // Strip 'L' prefix and ';' suffix if present
      val rawClassName = classMeta.className
      val className =
        if (rawClassName.startsWith('L') && rawClassName.endsWith(';')) {
          rawClassName.substring(1, rawClassName.length - 1)
        } else {
          rawClassName
        }

      // Filter 1: testPackageId
      if (testPackagePrefix != null && className.startsWith(testPackagePrefix)) {
        continue
      }

      // Filter 2: Explicit exclusions (Exact match or prefix match)
      if (internalExclusions.any { className == it || className.startsWith("$it/") }) {
        continue
      }

      val sourceFilename = classMeta.sourceFile

      // Filter 3: Generated Android R classes (Safe R filter)
      if ((className.endsWith("/R") || className.contains("/R$")) && sourceFilename.isBlank()) {
        continue
      }

      val pkgName = className.substringBeforeLast('/', "")

      val pkg = report.packages.getOrPut(pkgName) { PackageModel(pkgName) }
      val cls = pkg.classes.getOrPut(className) { ClassModel(className, sourceFilename) }
      val srcFile = pkg.sourceFiles.getOrPut(sourceFilename) { SourceFileModel(sourceFilename) }

      for (methodMeta in classMeta.methodsList) {
        // Determine method start line
        val methodStartLine =
          methodMeta.blocksList.flatMap { it.linesList }.map { smapResolver.resolve(it.lineNumber, sourceFilename).first }.minOrNull() ?: 0

        val method = MethodModel(methodMeta.name, methodMeta.signature, methodStartLine)
        cls.methods.add(method)

        val methodLinesTouched = mutableSetOf<Int>()
        var methodHit = false

        for (blockMeta in methodMeta.blocksList) {
          val isHit = data.hits.get(blockMeta.blockId.toInt())
          if (isHit) methodHit = true
          val blockBranches = blockMeta.branchCount.toInt()

          // Branch counter logic:
          if (blockBranches > 1) {
            if (isHit) {
              method.branches.covered += 1
              method.branches.missed += (blockBranches - 1)
            } else {
              method.branches.missed += blockBranches
            }
          }

          for (lineMeta in blockMeta.linesList) {
            val (trueLine, _) = smapResolver.resolve(lineMeta.lineNumber, sourceFilename)
            val instrs = lineMeta.instructionCount.toInt()

            methodLinesTouched.add(trueLine)

            // Update Method Instructions
            if (isHit) method.instructions.covered += instrs else method.instructions.missed += instrs

            // Update Source File line-level data (shared across classes in the same file)
            val lineStats = srcFile.lineMap.getOrPut(trueLine) { LineStats() }
            if (isHit) lineStats.ci += instrs else lineStats.mi += instrs

            // Line-level branch aggregation
            if (blockBranches > 1) {
              if (isHit) {
                lineStats.cb += 1
                lineStats.mb += (blockBranches - 1)
              } else {
                lineStats.mb += blockBranches
              }
            }
          }
        }

        // Update Method Line Counters
        for (lineNr in methodLinesTouched) {
          val lineStats = srcFile.lineMap[lineNr]
          if (lineStats != null) {
            if (lineStats.ci > 0) method.lines.covered++ else method.lines.missed++
          }
        }

        // Aggregate Method -> Class
        method.methodsCounter.covered = if (methodHit) 1 else 0
        method.methodsCounter.missed = if (methodHit) 0 else 1

        cls.instructions.add(method.instructions)
        cls.branches.add(method.branches)
        cls.lines.add(method.lines)
        cls.methodsCounter.add(method.methodsCounter)
      }
      cls.classesCounter.covered = if (cls.instructions.covered > 0) 1 else 0
      cls.classesCounter.missed = if (cls.instructions.covered == 0) 1 else 0
    }

    // Step 2: Hierarchical Aggregation
    for (pkg in report.packages.values) {
      // 1. Derive SourceFile instruction/branch/line stats from their LineMaps.
      for (src in pkg.sourceFiles.values) {
        for (line in src.lineMap.values) {
          src.instructions.covered += line.ci
          src.instructions.missed += line.mi
          src.branches.covered += line.cb
          src.branches.missed += line.mb
          if (line.ci > 0) src.lines.covered++ else src.lines.missed++
        }
      }

      // 2. Aggregate method/class counts from classes into their respective source files.
      for (cls in pkg.classes.values) {
        val srcFile = pkg.sourceFiles[cls.sourceFilename]!!
        srcFile.methodsCounter.add(cls.methodsCounter)
        srcFile.classesCounter.add(cls.classesCounter)
      }

      // 3. Aggregate SourceFiles into the Package.
      for (src in pkg.sourceFiles.values) {
        pkg.instructions.add(src.instructions)
        pkg.lines.add(src.lines)
        pkg.branches.add(src.branches)
        pkg.methodsCounter.add(src.methodsCounter)
        pkg.classesCounter.add(src.classesCounter)
      }

      // 4. Aggregate Packages into the Report.
      report.instructions.add(pkg.instructions)
      report.lines.add(pkg.lines)
      report.branches.add(pkg.branches)
      report.methodsCounter.add(pkg.methodsCounter)
      report.classesCounter.add(pkg.classesCounter)
    }

    return report
  }
}
