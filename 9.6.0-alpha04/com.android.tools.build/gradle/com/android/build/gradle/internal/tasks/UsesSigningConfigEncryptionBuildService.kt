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

import com.android.build.gradle.internal.signing.SigningConfigEncryptionBuildService
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Optional

/**
 * Common interface for tasks that encrypt or decrypt [com.android.build.gradle.internal.signing.SigningConfigData] using the build-scoped
 * [SigningConfigEncryptionBuildService].
 */
interface UsesSigningConfigEncryptionBuildService {

  @Suppress("UnstableApiUsage")
  @get:ServiceReference
  @get:Optional
  val signingConfigEncryptionService: Property<SigningConfigEncryptionBuildService>

  object ConfigureAction {

    fun <TaskT> configure(task: TaskT) where TaskT : Task, TaskT : UsesSigningConfigEncryptionBuildService {
      val service = SigningConfigEncryptionBuildService.RegistrationAction(task.project).execute()
      task.signingConfigEncryptionService.setDisallowChanges(service)
    }
  }
}
