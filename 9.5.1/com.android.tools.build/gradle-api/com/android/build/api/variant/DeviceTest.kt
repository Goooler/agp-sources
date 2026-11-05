/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.api.variant

import java.io.Serializable
import org.gradle.api.Incubating
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test

/**
 * Model for Device Test components that contains build-time properties
 *
 * This object is accessible on subtypes of [Variant] that implement [HasDeviceTests], via [HasDeviceTests.deviceTests]. It is also part of
 * [Variant.nestedComponents].
 *
 * The presence of this component in a variant is controlled by [HasDeviceTestsBuilder.deviceTests] and [DeviceTestBuilder.enable] which is
 * accessible on subtypes of [VariantBuilder] that implement [HasDeviceTestsBuilder]
 */
interface DeviceTest : GeneratesTestApk, HasAndroidResources, TestComponent {

  /** Variant's application ID as present in the final manifest file of the APK. */
  @get:Incubating override val applicationId: Property<String>

  /**
   * Variant's signingConfig, initialized by the corresponding DSL element.
   *
   * @return Variant's config or null if the variant is not configured for signing.
   */
  @get:Incubating val signingConfig: SigningConfig?

  /** Variant's [BuildConfigField] which will be generated in the BuildConfig class. */
  @get:Incubating val buildConfigFields: MapProperty<String, BuildConfigField<out Serializable>>?

  /**
   * List of proguard configuration files for this variant. The list is initialized from the corresponding DSL element, and cannot be
   * queried at configuration time. At configuration time, you can only add new elements to the list.
   */
  @get:Incubating val proguardFiles: ListProperty<RegularFile>

  /**
   * Whether test coverage is enabled for this device test.
   *
   * If enabled, this uses Jacoco to capture coverage and creates a report in the build directory.
   *
   * You cannot change the value any longer, to change it, please use [DeviceTestBuilder.enableCodeCoverage] in the
   * [AndroidComponentsExtension.beforeVariants] callback.
   */
  @get:Incubating val codeCoverageEnabled: Boolean

  /**
   * Runs some action to configure the Variant's device test task.
   *
   * The action will only run if the task is configured. In particular the
   * [HasDeviceTestsBuilder.deviceTests].\[[DeviceTestBuilder.ANDROID_TEST_TYPE]\]?.enable] must be set to true (it is true by default).
   *
   * Example :
   * ```(kotlin)
   *  androidComponents {
   *      onVariants { variant ->
   *          variant.deviceTests[DeviceTestBuilder.ANDROID_TEST_TYPE]?.configureTestTask { testTask ->
   *              testTask.beforeTest { descriptor ->
   *                  println("Running test: " + descriptor)
   *              }
   *          }
   *      }
   *  }
   * ```
   *
   * @param action to configure the test task. At this point, you should not assume the passed test Task is of [Test] type although this may
   *   change in the future.
   */
  @Incubating fun configureTestTask(action: (Task) -> Unit)

  /**
   * Runs some action on the Variant's device test task's [TaskProvider].
   *
   * The action will only run if the [DeviceTest] is enabled. In particular the
   * [HasDeviceTestsBuilder.deviceTests].\[[DeviceTestBuilder.ANDROID_TEST_TYPE]\]?.enable] must be set to true (it is true by default).
   *
   * This is particularly useful to set manual tasks dependencies. However, you should avoid calling [TaskProvider.get] as it will
   * automatically configure the task even if it is not scheduled to run, instead use [configureTestTask]
   *
   * Example :
   * ```(kotlin)
   *  androidComponents {
   *      onVariants { variant ->
   *          variant.deviceTests[DeviceTestBuilder.ANDROID_TEST_TYPE]?.withTestTaskProvider { testTaskProvider ->
   *              someAnchorTask.dependsOn(testTaskProvider)
   *          }
   *      }
   *  }
   * ```
   *
   * @param action on the test task [TaskProvider].
   */
  @Incubating fun withTestTaskProvider(action: (TaskProvider<out Task>) -> Unit)
}
