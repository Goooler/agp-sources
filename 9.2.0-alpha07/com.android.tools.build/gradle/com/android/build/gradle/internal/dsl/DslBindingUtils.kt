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

package com.android.build.gradle.internal.dsl

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.dsl.DependencyCollector

/** Utility class to help with DSL binding operations. */
object DslBindingUtils {
  /**
   * Copies all property values from the [source] object to the [target] object.
   *
   * For each property in the [source], the [target] is expected to have either:
   * 1. A public setter method named `setPropertyName`.
   * 2. A private field named `_propertyName` (for simple properties).
   * 3. A public getter method, in which case the properties are copied recursively (for nested DSL objects).
   *
   * Special cases are handled for:
   * - [NamedDomainObjectContainer]: elements are matched by name and copied recursively.
   * - [DependencyCollector]: all dependencies and constraints are copied.
   * - [List] and [Set]: all elements are added to the target collection.
   */
  @JvmStatic
  fun copyProperties(source: Any, target: Any) {
    for (method in source.javaClass.methods) {
      val name = method.name
      if (method.parameterCount == 0 && (name.startsWith("get") || name.startsWith("is"))) {
        if (name == "getClass" || name == "getMetaClass") continue

        val baseName =
          when {
            name.startsWith("get") -> name.substring(3)
            name.startsWith("is") -> name.substring(2)
            else -> continue
          }
        if (baseName.isEmpty()) continue

        try {
          val value = method.invoke(source)
          val targetGetter = target.javaClass.methods.find { it.name == name && it.parameterCount == 0 }
          val targetValue = targetGetter?.invoke(target)
          val setterName = "set$baseName"
          val setter = target.javaClass.methods.find { it.name == setterName && it.parameterCount == 1 }
          if (setter != null) {
            if (targetGetter == null || value != targetValue) {
              setter.invoke(target, value)
            }
          } else {
            if (value != null && targetValue != null) {
              when {
                value is NamedDomainObjectContainer<*> && targetValue is NamedDomainObjectContainer<*> -> {
                  for (sourceElement in value) {
                    val sourceName = (sourceElement as Named).name
                    val targetElement = targetValue.maybeCreate(sourceName)
                    if (targetElement != null) {
                      copyProperties(sourceElement, targetElement)
                    }
                  }
                }
                value is DependencyCollector && targetValue is DependencyCollector -> {
                  value.dependencies.get().forEach { targetValue.add(it) }
                  value.dependencyConstraints.get().forEach { targetValue.addConstraint(it) }
                }
                (value is List<*> || value is Set<*>) && targetValue is MutableCollection<*> -> {
                  @Suppress("UNCHECKED_CAST") (targetValue as MutableCollection<Any>).addAll(value as Collection<Any>)
                }
                value.javaClass.name.startsWith("com.android.build.api.dsl.") -> {
                  copyProperties(value, targetValue)
                }
                value.javaClass.name == "org.gradle.api.NamedDomainObjectContainer" -> {
                  copyProperties(value, targetValue)
                }
                else -> {
                  if (value != targetValue) {
                    setField(target, baseName, value)
                  }
                }
              }
            } else {
              if (targetGetter == null || value != targetValue) {
                setField(target, baseName, value)
              }
            }
          }
        } catch (_: Exception) {}
      }
    }
  }

  private fun setField(target: Any, baseName: String, value: Any?) {
    val fieldName = "_" + baseName.replaceFirstChar { it.lowercase() }
    var clazz: Class<*>? = target.javaClass
    while (clazz != null) {
      try {
        val field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
        return
      } catch (_: NoSuchFieldException) {
        clazz = clazz.superclass
      }
    }
  }
}
