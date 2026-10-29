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

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Merges JaCoCo-format XML coverage reports, for example a report built from JaCoCo's execution data and one built from the on-device
 * coverage agent's data, which can't be merged at the data level.
 */
class CoverageXmlMerger {

  private val writer = CoverageXmlWriter()

  fun merge(reports: Collection<File>, reportName: String, outputFile: File) {
    val merged = ReportModel(reportName)
    reports.forEach { mergeInto(merged, readReport(it)) }
    computeCounters(merged)
    writer.write(merged, outputFile)
  }

  /** What one report says about one source line. */
  private class LineInput(val mi: Int, val ci: Int, val mb: Int, val cb: Int)

  /** What one report says about one method. */
  private class MethodInput(
    val name: String,
    val desc: String,
    val line: Int,
    val instructions: Counter,
    val branches: Counter,
    val lines: Counter,
  )

  private class ClassInput(val name: String, val sourceFilename: String, val methods: List<MethodInput>)

  private class SourceFileInput(val name: String, val lines: Map<Int, LineInput>)

  private class PackageInput(val name: String, val classes: List<ClassInput>, val sourceFiles: List<SourceFileInput>)

  private fun readReport(file: File): List<PackageInput> {
    val factory = DocumentBuilderFactory.newInstance()
    // JaCoCo's XML declares a DTD, which must not be loaded.
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    val root = factory.newDocumentBuilder().parse(file).documentElement
    return root.childElements("package").map { pkg ->
      PackageInput(
        name = pkg.getAttribute("name"),
        classes =
          pkg.childElements("class").map { cls ->
            ClassInput(
              name = cls.getAttribute("name"),
              sourceFilename = cls.getAttribute("sourcefilename"),
              methods =
                cls.childElements("method").map { method ->
                  MethodInput(
                    name = method.getAttribute("name"),
                    desc = method.getAttribute("desc"),
                    line = method.getAttribute("line").toIntOrNull() ?: 0,
                    instructions = method.counter("INSTRUCTION"),
                    branches = method.counter("BRANCH"),
                    lines = method.counter("LINE"),
                  )
                },
            )
          },
        sourceFiles =
          pkg.childElements("sourcefile").map { sourceFile ->
            SourceFileInput(
              name = sourceFile.getAttribute("name"),
              lines =
                sourceFile.childElements("line").associate { line ->
                  line.getAttribute("nr").toInt() to
                    LineInput(
                      mi = line.intAttribute("mi"),
                      ci = line.intAttribute("ci"),
                      mb = line.intAttribute("mb"),
                      cb = line.intAttribute("cb"),
                    )
                },
            )
          },
      )
    }
  }

  private fun mergeInto(merged: ReportModel, packages: List<PackageInput>) {
    for (pkg in packages) {
      val mergedPackage = merged.packages.getOrPut(pkg.name) { PackageModel(pkg.name) }
      for (cls in pkg.classes) {
        val mergedClass = mergedPackage.classes.getOrPut(cls.name) { ClassModel(cls.name, cls.sourceFilename) }
        for (method in cls.methods) {
          val mergedMethod =
            mergedClass.methods.find { it.name == method.name && it.desc == method.desc }
              ?: MethodModel(method.name, method.desc, method.line).also { mergedClass.methods.add(it) }
          mergedMethod.instructions.mergeMax(method.instructions)
          mergedMethod.branches.mergeMax(method.branches)
          mergedMethod.lines.mergeMax(method.lines)
        }
      }
      for (sourceFile in pkg.sourceFiles) {
        val mergedSourceFile = mergedPackage.sourceFiles.getOrPut(sourceFile.name) { SourceFileModel(sourceFile.name) }
        for ((nr, line) in sourceFile.lines) {
          val mergedLine = mergedSourceFile.lineMap.getOrPut(nr) { LineStats() }
          val instructions =
            Counter(covered = mergedLine.ci, missed = mergedLine.mi).apply {
              mergeMax(Counter(covered = line.ci, missed = line.mi))
            }
          val branches =
            Counter(covered = mergedLine.cb, missed = mergedLine.mb).apply {
              mergeMax(Counter(covered = line.cb, missed = line.mb))
            }
          mergedLine.ci = instructions.covered
          mergedLine.mi = instructions.missed
          mergedLine.cb = branches.covered
          mergedLine.mb = branches.missed
        }
      }
    }
  }

  private fun computeCounters(report: ReportModel) {
    for (pkg in report.packages.values) {
      for (cls in pkg.classes.values) {
        for (method in cls.methods) {
          method.methodsCounter.set(covered = method.instructions.covered > 0)
          cls.add(method)
        }
        cls.classesCounter.set(covered = cls.methodsCounter.covered > 0)
        pkg.instructions.add(cls.instructions)
        pkg.branches.add(cls.branches)
        pkg.methodsCounter.add(cls.methodsCounter)
        pkg.classesCounter.add(cls.classesCounter)
      }
      for (sourceFile in pkg.sourceFiles.values) {
        for (line in sourceFile.lineMap.values) {
          sourceFile.instructions.add(Counter(covered = line.ci, missed = line.mi))
          sourceFile.branches.add(Counter(covered = line.cb, missed = line.mb))
          when {
            line.ci > 0 -> sourceFile.lines.covered++
            line.mi > 0 -> sourceFile.lines.missed++
          }
        }
        for (cls in pkg.classes.values.filter { it.sourceFilename == sourceFile.name }) {
          sourceFile.methodsCounter.add(cls.methodsCounter)
          sourceFile.classesCounter.add(cls.classesCounter)
        }
        pkg.lines.add(sourceFile.lines)
      }
      report.instructions.add(pkg.instructions)
      report.branches.add(pkg.branches)
      report.lines.add(pkg.lines)
      report.methodsCounter.add(pkg.methodsCounter)
      report.classesCounter.add(pkg.classesCounter)
    }
  }

  private fun CoverageElement.add(method: MethodModel) {
    instructions.add(method.instructions)
    branches.add(method.branches)
    lines.add(method.lines)
    methodsCounter.add(method.methodsCounter)
  }

  private fun Counter.mergeMax(other: Counter) {
    if (
      compareValuesBy(
        other,
        this,
        { it.covered > 0 },
        { it.covered > 0 && it.missed == 0 },
        { if (it.total == 0) 0.0 else it.covered.toDouble() / it.total },
        Counter::total,
      ) > 0
    ) {
      covered = other.covered
      missed = other.missed
    }
  }

  private fun Counter.set(covered: Boolean) {
    this.covered = if (covered) 1 else 0
    this.missed = if (covered) 0 else 1
  }

  private fun Element.childElements(tagName: String): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName == tagName }

  private fun Element.intAttribute(name: String): Int = getAttribute(name).toIntOrNull() ?: 0

  private fun Element.counter(type: String): Counter =
    childElements("counter")
      .find { it.getAttribute("type") == type }
      ?.let { Counter(covered = it.intAttribute("covered"), missed = it.intAttribute("missed")) } ?: Counter()
}
