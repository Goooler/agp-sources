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

package com.android.build.api.variant

import java.io.File
import java.io.Serializable
import java.util.Objects

/**
 * Configuration information required to sign a build artifact.
 *
 * @property storeFile The keystore file.
 * @property storePassword The keystore password.
 * @property keyAlias The key alias.
 * @property keyPassword The key password.
 * @property storeType The keystore type. Default: pkcs12
 */
class SigningConfigInfo(
  val storeFile: File,
  val storePassword: String,
  val keyAlias: String,
  val keyPassword: String,
  val storeType: String,
) : Serializable {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SigningConfigInfo) return false

    if (storeFile != other.storeFile) return false
    if (storePassword != other.storePassword) return false
    if (keyAlias != other.keyAlias) return false
    if (keyPassword != other.keyPassword) return false
    if (storeType != other.storeType) return false

    return true
  }

  override fun hashCode(): Int = Objects.hash(storeFile, storePassword, keyAlias, keyPassword, storeType)

  override fun toString(): String = "SigningConfigInfo(storeFile=${storeFile.absolutePath}, keyAlias=$keyAlias, storeType=$storeType)"
}
