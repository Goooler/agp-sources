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

package com.android.build.gradle.internal.ide.v2

import com.android.builder.model.v2.dsl.SigningConfig
import java.io.File
import java.io.Serializable

/**
 * Implementation of [SigningConfig] for serialization via the Tooling API. Intentionally NOT a `data class` so that the compiler-generated
 * toString() does not exist; passwords are never populated (see Converters.kt).
 */
class SigningConfigImpl(
  override val name: String,
  override val storeFile: File?,
  /** Always null. Retained for [SigningConfig] binary compatibility only. */
  override val storePassword: String?,
  override val keyAlias: String?,
  /** Always null. Retained for [SigningConfig] binary compatibility only. */
  override val keyPassword: String?,
  override val enableV1Signing: Boolean?,
  override val enableV2Signing: Boolean?,
  override val enableV3Signing: Boolean?,
  override val enableV4Signing: Boolean?,
  /** Stored (not computed) so that null passwords don't flip it to false. */
  override val isSigningReady: Boolean,
) : SigningConfig, Serializable {
  companion object {
    @JvmStatic private val serialVersionUID: Long = 1L
  }

  override fun toString(): String =
    "SigningConfigImpl(name=$name, storeFile=$storeFile, keyAlias=$keyAlias, " +
      "enableV1Signing=$enableV1Signing, enableV2Signing=$enableV2Signing, " +
      "enableV3Signing=$enableV3Signing, enableV4Signing=$enableV4Signing, " +
      "isSigningReady=$isSigningReady)"
}
