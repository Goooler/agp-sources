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
  fun aggregate(
    data: CoverageData,
    reportName: String,
    testPackageId: String? = null,
    exclusions: Set<String> = emptySet(),
    sourceFolders: Collection<java.io.File> = emptyList(),
  ): ReportModel {
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

      // Filter 4: Check if the physical source file exists in any of our source folders.
      // If it doesn't, this class belongs to an external compiled library, and we exclude it!
      if (sourceFolders.isNotEmpty() && !sourceFilename.isBlank()) {
        val pkgPath = className.substringBeforeLast('/', "")
        val relativeSourcePath = if (pkgPath.isEmpty()) sourceFilename else "$pkgPath/$sourceFilename"
        val exists = sourceFolders.any { folder -> java.io.File(folder, relativeSourcePath).exists() }
        if (!exists) {
          continue
        }
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

        val methodLinesTouched = mutableSetOf<Int>()
        var methodHit = false

        for (blockMeta in methodMeta.blocksList) {
          val isHit = data.hits.get(blockMeta.blockId.toInt())
          if (isHit) methodHit = true

          // 1. In DEX, the conditional branch is always the LAST instruction of the block.
          val lastLineMeta = blockMeta.linesList.lastOrNull()
          val branchFile = lastLineMeta?.let { smapResolver.resolve(it.lineNumber, sourceFilename).second } ?: sourceFilename

          // 2. The branch must be placed on the line of the branch instruction itself.
          // Since the branch instruction is the last instruction of the block, we resolve lastLineMeta.
          val trueBranchLine =
            if (branchFile == sourceFilename) {
              lastLineMeta?.let { smapResolver.resolve(it.lineNumber, sourceFilename).first }
            } else {
              null
            }

          // Generic Inline Function Filter:
          // If the branch belongs to an external inlined file, we strip it from our local report.
          // Also, strip compiler-generated coroutine state machine branches on the suspend method declaration line.
          val isSuspendFunction = methodMeta.signature.endsWith("Lkotlin/coroutines/Continuation;)Ljava/lang/Object;")
          val isSuspendLambda =
            methodMeta.name == "invokeSuspend" &&
              methodMeta.signature == "(Ljava/lang/Object;)Ljava/lang/Object;" &&
              classMeta.className.contains("$")

          val isBoilerplateMethod =
            (methodMeta.name == "equals" && methodMeta.signature == "(Ljava/lang/Object;)Z") ||
              (methodMeta.name == "hashCode" && methodMeta.signature == "()I") ||
              (methodMeta.name == "toString" && methodMeta.signature == "()Ljava/lang/String;") ||
              methodMeta.name.contains("\$default") ||
              methodMeta.name.contains("\$copy")

          // We can't use FQN for ComposableSingleton here because these classes are dyanmically generated by the compose compiler, so
          // there is no single static FQN to filter them out.
          val isComposableSingletonWrapper =
            (className.startsWith("ComposableSingletons$") || className.contains("/ComposableSingletons$")) &&
              !className.contains("\$lambda-")

          val blockBranches =
            if (branchFile != sourceFilename) {
              0
            } else if (isComposableSingletonWrapper) {
              0
            } else if (isBoilerplateMethod) {
              0
            } else if ((isSuspendFunction || isSuspendLambda) && trueBranchLine == methodStartLine) {
              0
            } else {
              blockMeta.branchCount.toInt()
            }

          // Exact branch coverage reconstruction using successor hits:
          var coveredBranches = 0
          if (blockBranches > 1) {
            coveredBranches = blockMeta.successorBlockIdsList.count { succId -> data.hits.get(succId.toInt()) }
            if (coveredBranches > blockBranches) {
              coveredBranches = blockBranches.toInt()
            }
            // An executed conditional line must show at least 1 covered branch.
            if (isHit && coveredBranches == 0) {
              coveredBranches = 1
            }

            method.branches.covered += coveredBranches
            method.branches.missed += (blockBranches.toInt() - coveredBranches)
          }

          for (lineMeta in blockMeta.linesList) {
            val (trueLine, resolvedFile) = smapResolver.resolve(lineMeta.lineNumber, sourceFilename)

            // 3. Skip tracking instructions that belong to an external inline file.
            // This prevents the caller's class from artificially ballooning in line count.
            if (resolvedFile != sourceFilename) {
              continue
            }

            val instrs = lineMeta.instructionCount.toInt()

            methodLinesTouched.add(trueLine)

            // Update Method Instructions
            if (isHit) method.instructions.covered += instrs else method.instructions.missed += instrs

            // Update Source File line-level data (shared across classes in the same file)
            val lineStats = srcFile.lineMap.getOrPut(trueLine) { LineStats() }
            if (isHit) lineStats.ci += instrs else lineStats.mi += instrs

            // Line-level branch aggregation - ONLY on the true branch line of the block to prevent duplicate branches
            if (blockBranches > 1 && trueLine == trueBranchLine) {
              lineStats.cb += coveredBranches
              lineStats.mb += (blockBranches.toInt() - coveredBranches)
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

        // TODO(b/556724270): Filter other compiler-generated methods.
        val isR8Lambda = methodMeta.name.contains("\$r8\$lambda\$")
        if (!isR8Lambda) {
          cls.methods.add(method)
          cls.methodsCounter.add(method.methodsCounter)
        }

        cls.instructions.add(method.instructions)
        cls.branches.add(method.branches)
        cls.lines.add(method.lines)
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
