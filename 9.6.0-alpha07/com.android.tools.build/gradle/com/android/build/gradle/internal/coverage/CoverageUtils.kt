/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.coverage

import com.android.build.gradle.internal.coverage.report.ReportType
import com.android.build.gradle.internal.coverage.report.createHtmlReportVisitor
import com.android.build.gradle.internal.coverage.report.createXmlReportVisitor
import com.android.build.gradle.internal.instrumentation.ASM_API_VERSION
import com.android.tools.coverage.reporter.CoverageXmlGenerator
import com.android.tools.coverage.reporter.GeneratedCode
import com.android.tools.coverage.reporter.isROrManifest
import com.google.common.io.Closeables
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.gradle.api.logging.Logger
import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.tools.ExecFileLoader
import org.jacoco.report.DirectorySourceFileLocator
import org.jacoco.report.FileMultiReportOutput
import org.jacoco.report.IReportVisitor
import org.jacoco.report.MultiReportVisitor
import org.jacoco.report.MultiSourceFileLocator
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/** Generates an on-the-fly coverage XML report. */
@Throws(IOException::class)
internal fun generateOnTheFlyXml(
  metadataFile: File?,
  hitsFile: File?,
  outputFile: File,
  reportName: String,
  testPackageId: String?,
  exclusions: Set<String>,
  sourceFolders: Collection<File>,
  classFolders: Collection<File>,
) {
  if (metadataFile == null || hitsFile == null) {
    throw IOException("On-the-fly coverage is enabled but required .pb files were not found.")
  }
  CoverageXmlGenerator()
    .generate(
      metadataFile = metadataFile,
      hitsFile = hitsFile,
      outputFile = outputFile,
      reportName = reportName,
      testPackageId = testPackageId,
      exclusions = exclusions,
      sourceFolders = sourceFolders,
      projectClasses = findProjectClasses(classFolders),
      generatedCode = findGeneratedCode(classFolders),
    )
}

/**
 * Returns the internal names, such as `com/example/Foo`, of the classes in the folders among [classFolders], without the R and Manifest
 * classes, which the reports leave out. Other entries, such as jars, are skipped.
 */
internal fun findProjectClasses(classFolders: Collection<File>): Set<String> {
  val classes = mutableSetOf<String>()
  forEachReportedClassFile(classFolders) { name, _ -> classes.add(name) }
  return classes
}

/**
 * Returns the code in the class files of [findProjectClasses] that an annotation marks as generated, as the build-time report does: the
 * classes and the methods annotated with an annotation whose simple name contains `Generated`, such as Dagger's `@DaggerGenerated`, and the
 * boxed variants that the Kotlin compiler generates for `@JvmExposeBoxed` (see [findExposeBoxedVariants]). The annotation needs to be in
 * the class file, so it has class or runtime retention. The dex in the APK drops class retention annotations, so only the class files have
 * them.
 */
internal fun findGeneratedCode(classFolders: Collection<File>): GeneratedCode {
  val classes = mutableSetOf<String>()
  val methods = mutableSetOf<String>()
  forEachReportedClassFile(classFolders) { _, file ->
    val bytes = file.readBytes()
    val visitor = GeneratedAnnotationsVisitor()
    try {
      ClassReader(bytes).accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    } catch (e: IllegalArgumentException) {
      // A class file of a version that ASM doesn't support yet, or an invalid one: its code stays in the report.
      return@forEachReportedClassFile
    } catch (e: IndexOutOfBoundsException) {
      // Not a valid class file, such as one cut short: its code stays in the report.
      return@forEachReportedClassFile
    }
    if (visitor.classIsGenerated) {
      classes.add(visitor.className)
    }
    methods.addAll(visitor.generatedMethods)
    if (visitor.exposeBoxedMethods.isNotEmpty()) {
      methods.addAll(findExposeBoxedVariants(bytes, visitor.exposeBoxedMethods))
    }
  }
  return GeneratedCode(classes, methods)
}

/** Records which of a class and its methods have an annotation that [isGeneratedAnnotation] accepts. */
private class GeneratedAnnotationsVisitor : ClassVisitor(ASM_API_VERSION) {
  var className = ""
    private set

  var classIsGenerated = false
    private set

  private val methods = mutableListOf<String>()

  /** The methods with such an annotation, such as `com/example/Foo.get()V`. */
  val generatedMethods: List<String> = methods

  private val exposeBoxed = mutableListOf<String>()

  /** The methods with the class retention annotation `@JvmExposeBoxed`, in the same form. */
  val exposeBoxedMethods: List<String> = exposeBoxed

  override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
    className = name
  }

  override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
    if (isGeneratedAnnotation(descriptor)) {
      classIsGenerated = true
    }
    return null
  }

  override fun visitMethod(
    access: Int,
    name: String,
    descriptor: String,
    signature: String?,
    exceptions: Array<out String>?,
  ): MethodVisitor =
    object : MethodVisitor(ASM_API_VERSION) {
      override fun visitAnnotation(annotationDescriptor: String, visible: Boolean): AnnotationVisitor? {
        if (isGeneratedAnnotation(annotationDescriptor)) {
          methods.add("$className.$name$descriptor")
        }
        if (!visible && annotationDescriptor == "Lkotlin/jvm/JvmExposeBoxed;") {
          exposeBoxed.add("$className.$name$descriptor")
        }
        return null
      }
    }
}

/**
 * Returns the methods among [candidates] in the class file [bytes] that the Kotlin compiler generates for `@JvmExposeBoxed`, as the
 * build-time report finds them: the boxed variant of a function that takes or returns a value class, whose first call of `unbox-impl`
 * unboxes a value that it just loaded from a local variable, or whose first call of `box-impl` boxes the value that it returns. The
 * function itself takes and returns the underlying values and doesn't have the annotation, so it stays in the report.
 */
private fun findExposeBoxedVariants(bytes: ByteArray, candidates: List<String>): List<String> {
  val variants = mutableListOf<String>()
  val visitor =
    object : ClassVisitor(ASM_API_VERSION) {
      private var className = ""

      override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
        className = name
      }

      override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
      ): MethodVisitor? {
        val method = "$className.$name$descriptor"
        return if (method in candidates) ExposeBoxedVariantVisitor { variants.add(method) } else null
      }
    }
  try {
    // Without skipping the labels, lines and frames, which the build-time report sees between the instructions too.
    ClassReader(bytes).accept(visitor, 0)
  } catch (e: IllegalArgumentException) {
    return emptyList()
  } catch (e: IndexOutOfBoundsException) {
    return emptyList()
  }
  return variants
}

/**
 * Calls [onVariant] if the method that it visits is a boxed variant of [findExposeBoxedVariants]. A label, a line or a frame counts as a
 * node without an opcode between the instructions, as in the build-time report.
 */
private class ExposeBoxedVariantVisitor(private val onVariant: () -> Unit) : MethodVisitor(ASM_API_VERSION) {
  private var previousOpcode = NO_OPCODE
  private var afterBox = false
  private var done = false

  override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
    val searching = !done && !afterBox
    val previous = previousOpcode
    visitNode(opcode)
    if (searching && opcode == Opcodes.INVOKEVIRTUAL && name == "unbox-impl") {
      done = true
      if (previous == Opcodes.ALOAD) {
        onVariant()
      }
    } else if (searching && opcode == Opcodes.INVOKESTATIC && name == "box-impl") {
      afterBox = true
    }
  }

  override fun visitInsn(opcode: Int) = visitNode(opcode)

  override fun visitIntInsn(opcode: Int, operand: Int) = visitNode(opcode)

  override fun visitVarInsn(opcode: Int, varIndex: Int) = visitNode(opcode)

  override fun visitTypeInsn(opcode: Int, type: String) = visitNode(opcode)

  override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) = visitNode(opcode)

  override fun visitInvokeDynamicInsn(
    name: String,
    descriptor: String,
    bootstrapMethodHandle: Handle,
    vararg bootstrapMethodArguments: Any,
  ) = visitNode(Opcodes.INVOKEDYNAMIC)

  override fun visitJumpInsn(opcode: Int, label: Label) = visitNode(opcode)

  override fun visitLdcInsn(value: Any) = visitNode(Opcodes.LDC)

  override fun visitIincInsn(varIndex: Int, increment: Int) = visitNode(Opcodes.IINC)

  override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) = visitNode(Opcodes.TABLESWITCH)

  override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) = visitNode(Opcodes.LOOKUPSWITCH)

  override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) = visitNode(Opcodes.MULTIANEWARRAY)

  override fun visitLabel(label: Label) = visitNode(NO_OPCODE)

  override fun visitLineNumber(line: Int, start: Label) = visitNode(NO_OPCODE)

  override fun visitFrame(type: Int, numLocal: Int, local: Array<out Any>?, numStack: Int, stack: Array<out Any>?) = visitNode(NO_OPCODE)

  /** Visits a node: an instruction with the [opcode], or a label, a line or a frame with [NO_OPCODE]. */
  private fun visitNode(opcode: Int) {
    if (afterBox) {
      afterBox = false
      done = true
      if (opcode == Opcodes.ARETURN) {
        onVariant()
      }
    }
    previousOpcode = opcode
  }

  private companion object {
    const val NO_OPCODE = -1
  }
}

/**
 * Whether an annotation with the [descriptor], such as `Ldagger/internal/DaggerGenerated;`, has a simple name that contains `Generated`.
 */
private fun isGeneratedAnnotation(descriptor: String): Boolean =
  descriptor.substring(maxOf(descriptor.lastIndexOf('/'), descriptor.lastIndexOf('$')) + 1).contains("Generated")

/**
 * Calls [action] with the internal name and the file of each class in the folders among [classFolders], without the R and Manifest classes.
 */
private fun forEachReportedClassFile(classFolders: Collection<File>, action: (name: String, file: File) -> Unit) {
  for (folder in classFolders.filter { it.isDirectory }) {
    folder
      .walkTopDown()
      // A class folder inside another one gets its own walk, which names its classes relative to it.
      .onEnter { it.path == folder.path || it !in classFolders }
      .filter { it.isFile && isReportedClassFile(it.name) }
      .forEach { action(it.relativeTo(folder).invariantSeparatorsPath.removeSuffix(".class"), it) }
  }
}

/** The files the on-device coverage agent writes in one test run. */
internal data class OnDeviceCoverageFiles(val metadata: File, val hits: File)

// File names written by the native on-device coverage agent in
// tools/base/android-test/coverage/agent/native/metadata_collector.cc and hits_extractor.cc.
internal const val ON_DEVICE_COVERAGE_METADATA_FILE = "coverage_metadata.pb"
internal const val ON_DEVICE_COVERAGE_HITS_FILE = "coverage_hits.pb"

/**
 * Finds the files of every on-device coverage run under [roots], sorted by directory.
 *
 * The block ids in a hits file only match the metadata file of the same run, so the two files are paired by directory, and a directory
 * without both files is skipped.
 */
internal fun findOnDeviceCoverageFiles(roots: Collection<File>): List<OnDeviceCoverageFiles> =
  roots
    .filter { it.isDirectory }
    .flatMap { root -> root.walkTopDown().filter { it.isFile && it.name == ON_DEVICE_COVERAGE_METADATA_FILE } }
    .mapNotNull { metadata ->
      File(metadata.parentFile, ON_DEVICE_COVERAGE_HITS_FILE).takeIf { it.isFile }?.let { OnDeviceCoverageFiles(metadata, it) }
    }
    .distinct()
    .sortedBy { it.metadata.path }

/** Computes automated exclusions by scanning test class files. */
internal fun computeAutomatedExclusions(testClassFiles: Set<File>): Set<String> {
  val automatedExclusions = mutableSetOf<String>()
  testClassFiles.forEach { file ->
    if (file.isDirectory) {
      file
        .walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .forEach { classFile ->
          automatedExclusions.add(classFile.relativeTo(file).path.removeSuffix(".class").replace(File.separatorChar, '/'))
        }
    }
  }
  return automatedExclusions
}

@Throws(IOException::class)
@JvmOverloads
internal fun generateReport(
  coverageFiles: Collection<File>,
  reportDir: File,
  classFolders: Collection<File>,
  sourceFolders: Collection<File>,
  tabWidth: Int,
  reportName: String,
  logger: Logger,
  reportTypes: List<ReportType> = listOf(ReportType.HTML, ReportType.XML),
  xmlReportName: String = "report",
) {
  // Load data
  val loader = ExecFileLoader()
  for (coverageFile in coverageFiles) {
    loader.load(coverageFile)
  }
  val sessionInfoStore = loader.sessionInfoStore
  val executionDataStore = loader.executionDataStore

  val output = FileMultiReportOutput(reportDir)
  var xmlReportOutput: OutputStream? = null

  try {
    val visitor: IReportVisitor =
      MultiReportVisitor(
        reportTypes.map {
          when (it) {
            ReportType.HTML -> createHtmlReportVisitor(output)
            ReportType.XML -> {
              xmlReportOutput = output.createFile("${xmlReportName}.xml")
              createXmlReportVisitor(xmlReportOutput)
            }
          }
        }
      )

    // Generate report
    visitor.visitInfo(sessionInfoStore.infos, executionDataStore.contents)
    val builder = CoverageBuilder()
    val analyzer = Analyzer(executionDataStore, builder)
    analyzeAll(analyzer, classFolders)
    val locator = MultiSourceFileLocator(0)
    for (folder in sourceFolders) {
      locator.add(DirectorySourceFileLocator(folder, "UTF-8", tabWidth))
    }
    val bundle = builder.getBundle(reportName)
    visitor.visitBundle(bundle, locator)
    visitor.visitEnd()
  } finally {
    try {
      xmlReportOutput?.close()
    } catch (e: IOException) {
      logger.error("Could not close xml report file", e)
    }
  }
}

@Throws(IOException::class)
private fun analyzeAll(analyzer: Analyzer, classFolders: Collection<File>) {
  for (folder in classFolders) {
    analyze(analyzer, folder, classFolders)
  }
}

/**
 * Analyzes code coverage on file if it's a class file, or recursively analyzes descendants if file is a folder.
 *
 * @param analyzer Jacoco Analyzer
 * @param file a file or folder
 * @param originalClassFolders the original collection of class folders to be analyzed; e.g., this.classFileCollection.getFiles(). This
 *   parameter is included to avoid redundant computation in the case when one of the original class folders is a descendant of another.
 */
@Throws(IOException::class)
private fun analyze(analyzer: Analyzer, file: File, originalClassFolders: Collection<File>) {
  if (file.isDirectory) {
    val files = file.listFiles()
    if (files != null) {
      for (f in files) {
        // check that f is not in originalClassFolders to avoid redundant
        // computation
        if (!originalClassFolders.contains(f)) {
          analyze(analyzer, f, originalClassFolders)
        }
      }
    }
  } else {
    if (!isReportedClassFile(file.name)) {
      return
    }
    val `in`: InputStream = FileInputStream(file)
    try {
      analyzer.analyzeClass(`in`, file.absolutePath)
    } finally {
      Closeables.closeQuietly(`in`)
    }
  }
}

/** Whether a file named [name] is a class file that the reports cover: R and Manifest classes are left out. */
private fun isReportedClassFile(name: String): Boolean = name.endsWith(".class") && !isROrManifest(name.removeSuffix(".class"))
