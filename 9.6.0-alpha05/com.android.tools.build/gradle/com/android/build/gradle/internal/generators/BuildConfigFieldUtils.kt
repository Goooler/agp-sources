/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.generators

import com.android.build.api.variant.BuildConfigField
import java.io.IOException
import java.io.Serializable
import java.io.Writer
import java.util.regex.Pattern
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

@Throws(IOException::class)
fun <T : Serializable> BuildConfigField<T>.emit(name: String, writer: ClassWriter) {
  val pfsOpcodes = Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_STATIC
  when (type) {
    "boolean" -> writer.visitField(pfsOpcodes, name, Type.getDescriptor(Boolean::class.java), null, value).visitEnd()
    "int" -> writer.visitField(pfsOpcodes, name, Type.getDescriptor(Int::class.java), null, value).visitEnd()
    "long" -> writer.visitField(pfsOpcodes, name, Type.getDescriptor(Long::class.java), null, value).visitEnd()
    "String" ->
      writer
        .visitField(
          pfsOpcodes,
          name,
          Type.getDescriptor(String::class.java),
          null,
          value.toString().removeSurrounding("\""),
        )
        .visitEnd()
    else ->
      throw IllegalArgumentException(
        """BuildConfigField name: $name type: $type and value type: ${value.javaClass
                .name} cannot be emitted."""
          .trimMargin()
      )
  }
}

@Throws(IOException::class)
fun <T : Serializable> BuildConfigField<T>.emit(
  name: String,
  writer: Writer,
  namespace: String,
) {
  comment?.lineSequence()?.forEach { line -> writer.write("  // $line\n") }
  val valueAsString = value.toString()
  // Hack (see IDEA-100046): We want to avoid reporting "condition is always
  // true" from the data flow inspection, so use a non-constant value.
  // However, that defeats the purpose of this flag (when not in debug mode,
  // if (BuildConfig.DEBUG && ...) will be completely removed by
  // the compiler), so as a hack we do it only for the case where debug is
  // true, which is the most likely scenario while the user is looking
  // at source code. map.put(PH_DEBUG, Boolean.toString(mDebug));
  val emitValue =
    if (name == "DEBUG") {
      if (valueAsString == "true") "Boolean.parseBoolean(\"true\")" else "false"
    } else {
      when (type) {
        "long" -> if (valueAsString.toLongOrNull() == null) valueAsString else valueAsString.ensureSuffix('L')
        "float" -> if (valueAsString.toFloatOrNull() == null) valueAsString else valueAsString.ensureSuffix('f')
        else -> valueAsString
      }
    }

  val compressedType = compressType(type, namespace)
  val separator = if (emitValue.startsWith("\n")) "" else " "
  val formattedValue = emitValue.replace("\n", "\n      ")
  writer.write("  public static final $compressedType $name =$separator$formattedValue;\n")
}

private val TYPE_PATTERN: Pattern = Pattern.compile("(?:[\\w$]+\\.)*([\\w.*$]+)")

/**
 * Strips redundant `java.lang.` and same-package (`<namespace>.`) prefixes from qualified class names inside [type] while preserving all
 * surrounding syntax (`<...>`, `?`, `[]`, whitespace) verbatim.
 *
 * Examples (when `namespace == "my.app.pkg"`):
 * - `"java.lang.Long[]"` -> `"Long[]"`
 * - `"java.util.List<java.lang.String>"` -> `"java.util.List<String>"`
 * - `"my.app.pkg.Outer<String>.Inner"` -> `"Outer<String>.Inner"`
 * - `"java.lang.reflect.Method"` -> `"java.lang.reflect.Method"` (subpackage, not `java.lang` itself)
 */
private fun compressType(type: String, namespace: String): String {
  val packagePrefix = "$namespace."
  val sb = StringBuilder()
  val matcher = TYPE_PATTERN.matcher(type)
  var pos = 0
  while (true) {
    val found = matcher.find(pos)
    val typeStart = if (found) matcher.start() else type.length
    sb.append(type, pos, typeStart)
    if (!found) break
    val name = matcher.group(0)
    when {
      isClassInPackage(name, packagePrefix) -> sb.append(name.substring(packagePrefix.length))
      isClassInPackage(name, "java.lang.") -> sb.append(name.substring("java.lang.".length))
      else -> sb.append(name)
    }
    pos = matcher.end()
  }
  return sb.toString()
}

/**
 * Returns true if [name] refers to a class directly in [packagePrefix] (including nested classes like `java.lang.Thread.State`, identified
 * by an uppercase outer class name) rather than a subpackage (like `java.lang.reflect.Method`).
 */
private fun isClassInPackage(name: String, packagePrefix: String): Boolean {
  if (!name.startsWith(packagePrefix) || name.length <= packagePrefix.length) {
    return false
  }
  return name.indexOf('.', packagePrefix.length) == -1 || Character.isUpperCase(name[packagePrefix.length])
}

private fun String.ensureSuffix(suffix: Char): String {
  return if (endsWith(suffix)) this else "$this$suffix"
}
