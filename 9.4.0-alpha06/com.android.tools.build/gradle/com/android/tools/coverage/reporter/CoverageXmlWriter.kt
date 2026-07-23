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
import java.io.PrintWriter

/** Writer for serializing [ReportModel] into a standardized XML format. */
class CoverageXmlWriter {

  fun write(model: ReportModel, outputFile: File) {
    outputFile.bufferedWriter().use { writer ->
      val out = PrintWriter(writer)
      out.println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
      out.println("<report name=\"${escapeXml(model.name)}\">")

      out.println(
        "  <sessioninfo id=\"session-${System.currentTimeMillis()}\" start=\"${System.currentTimeMillis()}\" dump=\"${System.currentTimeMillis()}\"/>"
      )

      for (pkg in model.packages.values) {
        out.println("  <package name=\"${pkg.name}\">")

        // Classes
        for (cls in pkg.classes.values) {
          out.println("    <class name=\"${cls.name}\" sourcefilename=\"${cls.sourceFilename}\">")
          for (m in cls.methods) {
            out.println("      <method name=\"${escapeXml(m.name)}\" desc=\"${escapeXml(m.desc)}\" line=\"${m.line}\">")
            writeCounters(out, "        ", m)
            out.println("      </method>")
          }
          writeCounters(out, "      ", cls)
          out.println("    </class>")
        }

        // Source Files
        for (src in pkg.sourceFiles.values) {
          out.println("    <sourcefile name=\"${src.name}\">")
          for ((lineNr, stats) in src.lineMap) {
            out.println("      <line nr=\"$lineNr\" mi=\"${stats.mi}\" ci=\"${stats.ci}\" mb=\"${stats.mb}\" cb=\"${stats.cb}\"/>")
          }
          writeCounters(out, "      ", src)
          out.println("    </sourcefile>")
        }

        writeCounters(out, "    ", pkg)
        out.println("  </package>")
      }

      writeCounters(out, "  ", model)
      out.println("</report>")
    }
  }

  private fun writeCounters(out: PrintWriter, indent: String, element: CoverageElement) {
    out.println(
      "${indent}<counter type=\"INSTRUCTION\" missed=\"${element.instructions.missed}\" covered=\"${element.instructions.covered}\"/>"
    )

    if (element.branches.total > 0) {
      out.println("${indent}<counter type=\"BRANCH\" missed=\"${element.branches.missed}\" covered=\"${element.branches.covered}\"/>")
    }

    out.println("${indent}<counter type=\"LINE\" missed=\"${element.lines.missed}\" covered=\"${element.lines.covered}\"/>")
    out.println(
      "${indent}<counter type=\"METHOD\" missed=\"${element.methodsCounter.missed}\" covered=\"${element.methodsCounter.covered}\"/>"
    )

    if (element is AggregateElement) {
      out.println(
        "${indent}<counter type=\"CLASS\" missed=\"${element.classesCounter.missed}\" covered=\"${element.classesCounter.covered}\"/>"
      )
    }
  }

  private fun escapeXml(str: String): String =
    str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
