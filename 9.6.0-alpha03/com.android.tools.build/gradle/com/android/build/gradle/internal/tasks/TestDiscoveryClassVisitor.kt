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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.instrumentation.ASM_API_VERSION
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

/**
 * An ASM [ClassVisitor] designed to replicate the test discovery implementation from `AndroidJUnitRunner`.
 *
 * Similar to how test discovery works in `androidx.test` (e.g. `ClassPathScanner`, `TestRequestBuilder`, and builders like
 * `AndroidJUnit4Builder` or `AndroidJUnit3Builder`), this visitor flags a class as a test class if it subclasses `junit/framework/TestCase`
 * or `android/test/AndroidTestCase`, or if it is annotated with `@RunWith` or contains methods annotated with `@Test`.
 */
class TestDiscoveryClassVisitor(delegate: ClassVisitor? = null) : ClassVisitor(ASM_API_VERSION, delegate) {
  var isTestClass = false
    private set

  override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
    if (superName == "junit/framework/TestCase" || superName == "android/test/AndroidTestCase") {
      isTestClass = true
    }
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitAnnotation(descriptor: String?, visible: Boolean): org.objectweb.asm.AnnotationVisitor? {
    if (descriptor == "Lorg/junit/runner/RunWith;") {
      isTestClass = true
    }
    return super.visitAnnotation(descriptor, visible)
  }

  override fun visitMethod(
    access: Int,
    name: String?,
    descriptor: String?,
    signature: String?,
    exceptions: Array<out String>?,
  ): MethodVisitor? {
    val nextVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
    return object : MethodVisitor(ASM_API_VERSION, nextVisitor) {
      override fun visitAnnotation(desc: String?, visible: Boolean): org.objectweb.asm.AnnotationVisitor? {
        if (desc == "Lorg/junit/Test;") {
          isTestClass = true
        }
        return super.visitAnnotation(desc, visible)
      }
    }
  }
}
