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

package com.android.build.gradle.internal.dsl.decorator

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import java.lang.invoke.MethodHandles
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import org.gradle.api.JavaVersion
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method

/**
 * A generator of a wrapper class for a DSL interface.
 *
 * Given an interface, calling [createSynthetic] will return a generated class which implements the interface and delegates all method calls
 * to a public 'delegate' field of the same interface type. 'delegate' field will be assigned once Gradle context/project is ready.
 */
class DeclarativeDslSyntheticFactory {

  private val cache: LoadingCache<Class<*>, Class<*>> =
    CacheBuilder.newBuilder()
      .build(
        object : CacheLoader<Class<*>, Class<*>>() {
          override fun load(dslClass: Class<*>): Class<*> {
            return decorateImpl(dslClass)
          }
        }
      )

  fun <T : Any> createSynthetic(dslClass: Class<T>): Class<out T> {
    @Suppress("UNCHECKED_CAST")
    return cache.get(dslClass) as Class<out T>
  }

  private fun <T> decorateImpl(dslClass: Class<T>): Class<out T> {
    val dslType = Type.getType(dslClass)
    // Use a suffix that won't collide with DslDecorator
    val generatedName = dslType.internalName + "\$AgpDeclarativeDecorated"

    try {
      @Suppress("UNCHECKED_CAST")
      return dslClass.classLoader.loadClass(generatedName.replace('/', '.')) as Class<out T>
    } catch (ignored: ClassNotFoundException) {
      // Define the class
    }

    val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)

    val isInterface = dslClass.isInterface
    val superType = if (isInterface) Type.getType(Any::class.java) else dslType
    val interfaces = if (isInterface) arrayOf(dslType.internalName) else null

    classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, generatedName, null, superType.internalName, interfaces)

    // Constructor: public <init>()
    val constructor = Method("<init>", Type.VOID_TYPE, arrayOf())
    val generator = GeneratorAdapter(Opcodes.ACC_PUBLIC, constructor, null, null, classWriter)
    generator.loadThis()
    generator.invokeConstructor(superType, constructor)
    generator.returnValue()
    generator.endMethod()

    // Implement methods
    val methods = dslClass.methods
    val generatedFields = mutableSetOf<String>()

    for (method in methods) {
      if (Modifier.isStatic(method.modifiers)) continue
      // If we are extending a class, only implement abstract methods
      if (!isInterface && !Modifier.isAbstract(method.modifiers)) continue

      generateStorageMethod(classWriter, Type.getObjectType(generatedName), method, generatedFields)
    }

    classWriter.visitEnd()
    return defineClass(dslClass, classWriter.toByteArray())
  }

  private fun generateStorageMethod(
    classWriter: ClassWriter,
    generatedType: Type,
    method: java.lang.reflect.Method,
    generatedFields: MutableSet<String>,
  ) {
    val asmMethod = Method.getMethod(method)
    val name = method.name

    val isGetter =
      (name.startsWith("get") && name.length > 3 && method.parameterCount == 0) ||
        (name.startsWith("is") && name.length > 2 && method.parameterCount == 0)

    if (isGetter) {
      val propertyName =
        if (name.startsWith("get")) {
          name.substring(3).replaceFirstChar { it.lowercase() }
        } else {
          name.substring(2).replaceFirstChar { it.lowercase() }
        }

      val fieldName = "_$propertyName"
      val fieldType = Type.getReturnType(method)

      if (generatedFields.add(fieldName)) {
        // Field: public _foo (public to allow direct access for copying)
        classWriter.visitField(Opcodes.ACC_PUBLIC, fieldName, fieldType.descriptor, null, null).visitEnd()
      }

      // Implement getter
      val getter = GeneratorAdapter(Opcodes.ACC_PUBLIC, asmMethod, null, null, classWriter)
      getter.loadThis()
      getter.getField(generatedType, fieldName, fieldType)
      getter.returnValue()
      getter.endMethod()
      return
    }

    // Handle block methods: foo(Action) or foo(Function1)
    if (method.parameterCount == 1) {
      val paramType = method.parameterTypes[0]
      if (paramType.name == "org.gradle.api.Action" || paramType.name == "kotlin.jvm.functions.Function1") {
        val getterName = "get${name.replaceFirstChar { it.uppercase() }}"
        val getter =
          try {
            method.declaringClass.getMethod(getterName)
          } catch (_: Exception) {
            null
          }

        if (getter != null) {
          val generator = GeneratorAdapter(Opcodes.ACC_PUBLIC, asmMethod, null, null, classWriter)
          generator.loadArg(0)
          generator.loadThis()
          generator.invokeVirtual(generatedType, Method.getMethod(getter))
          if (paramType.name == "org.gradle.api.Action") {
            generator.invokeInterface(Type.getType(paramType), Method("execute", Type.VOID_TYPE, arrayOf(Type.getType(Any::class.java))))
          } else {
            generator.invokeInterface(
              Type.getType(paramType),
              Method("invoke", Type.getType(Any::class.java), arrayOf(Type.getType(Any::class.java))),
            )
          }
          generator.returnValue()
          generator.endMethod()
          return
        }
      }
    }

    // Default implementation (e.g. for other methods)
    val generator = GeneratorAdapter(Opcodes.ACC_PUBLIC, asmMethod, null, null, classWriter)
    if (asmMethod.returnType != Type.VOID_TYPE) {
      when (asmMethod.returnType.sort) {
        Type.BOOLEAN,
        Type.CHAR,
        Type.BYTE,
        Type.SHORT,
        Type.INT -> generator.push(0)
        Type.LONG -> generator.push(0L)
        Type.FLOAT -> generator.push(0.0f)
        Type.DOUBLE -> generator.push(0.0)
        else -> generator.visitInsn(Opcodes.ACONST_NULL)
      }
    }
    generator.returnValue()
    generator.endMethod()
  }

  private fun <T> defineClass(originalClass: Class<T>, bytes: ByteArray): Class<out T> {
    return if (JavaVersion.current().isJava9Compatible) {
      lookupDefineClass(originalClass, bytes)
    } else {
      legacyDefineClass(originalClass, bytes)
    }
  }

  private fun <T> lookupDefineClass(originalClass: Class<T>, bytes: ByteArray): Class<out T> {
    val lookup = privateLookupInMethod.invoke(null, originalClass, MethodHandles.lookup()) as MethodHandles.Lookup
    try {
      @Suppress("UNCHECKED_CAST")
      return lookupDefineClassMethod.invoke(lookup, bytes) as Class<out T>
    } catch (e: InvocationTargetException) {
      throw RuntimeException(
        "Internal error happened generating implementation for " +
          originalClass +
          ".\n" +
          "This is usually caused by having different " +
          "classloaders for different AGP jars. If you have an api dependency " +
          "on `com.android.tools.build:gradle:gradle-api` in your buildSrc, try " +
          "changing the dependency to be compileOnly or adding a runtime " +
          "dependency on `com.android.tools.build:gradle:gradle` in your buildSrc.",
        e,
      )
    }
  }

  private fun <T> legacyDefineClass(originalClass: Class<T>, bytes: ByteArray): Class<out T> {
    @Suppress("UNCHECKED_CAST")
    return classLoaderDefineClass.invoke(originalClass.classLoader, null, bytes, 0, bytes.size) as Class<T>
  }

  companion object {
    private val privateLookupInMethod by
      lazy(LazyThreadSafetyMode.PUBLICATION) {
        MethodHandles::class.java.getDeclaredMethod("privateLookupIn", Class::class.java, MethodHandles.Lookup::class.java)
      }
    private val lookupDefineClassMethod by
      lazy(LazyThreadSafetyMode.PUBLICATION) { MethodHandles.Lookup::class.java.getDeclaredMethod("defineClass", ByteArray::class.java) }
    private val classLoaderDefineClass by
      lazy(LazyThreadSafetyMode.PUBLICATION) {
        ClassLoader::class
          .java
          .getDeclaredMethod(
            "defineClass",
            String::class.java,
            ByteArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
          )
          .also { it.isAccessible = true }
      }
  }
}
