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

package com.android.build.gradle.internal.signing

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * Common interface for tasks that consume the signing keystore file.
 *
 * This is used to track the keystore file content changes (as an [InputFiles] property) without making it an input to
 * [com.android.build.gradle.internal.tasks.ValidateSigningTask].
 *
 * We cannot declare the keystore file as an input (e.g., `@InputFile`) inside [SigningConfigDataProvider] because
 * [com.android.build.gradle.internal.tasks.ValidateSigningTask] references [SigningConfigDataProvider] and might write to the keystore file
 * (creating the default debug keystore), which would cause a Gradle input/output overlap error.
 */
interface SigningInputs {

  /** Internal property used solely for input tracking. The actual keystore file is resolved lazily at execution time. */
  @get:Optional @get:PathSensitive(PathSensitivity.NONE) @get:InputFiles val signingKeystoreFile: ConfigurableFileCollection
}
