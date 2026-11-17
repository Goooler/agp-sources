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

import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.google.common.annotations.VisibleForTesting
import com.google.common.hash.Hashing
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Callable
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry

/** Encrypts and decrypts signing config passwords for intermediate file storage. */
interface SigningConfigPasswordCipher {
  fun encryptPassword(
    fieldName: String,
    configName: String,
    storeType: String?,
    storeFilePath: String?,
    keyAlias: String?,
    plaintext: String,
  ): String

  fun decryptPassword(
    fieldName: String,
    configName: String,
    storeType: String?,
    storeFilePath: String?,
    keyAlias: String?,
    ciphertext: String,
  ): String
}

/**
 * Global [BuildService] registered under its fixed class name on `Gradle.sharedServices` so all projects and AGP classloaders within a
 * build share the same ephemeral 256-bit AES key without capturing any [Project] reference in Configuration Cache state.
 */
abstract class GlobalSigningConfigKeyService : BuildService<GlobalSigningConfigKeyService.Params> {

  interface Params : BuildServiceParameters {
    val encodedKey: Property<String>
  }

  fun getEncodedKey(): String = parameters.encodedKey.get()
}

/**
 * Build-scoped [BuildService] that encrypts and decrypts [SigningConfigData] passwords using authenticated encryption (AES-256-GCM) so
 * plaintext passwords are never written to intermediate files on disk.
 *
 * The 256-bit key is generated once per build via [GlobalSigningConfigKeyService] and referenced through [Params.keyService] so
 * Configuration Cache hits can decrypt existing intermediate files across daemon restarts while Configuration Cache misses or non-cached
 * builds rotate to a fresh key (causing [com.android.build.gradle.internal.tasks.SigningConfigWriterTask] to re-run via [getKeyId]).
 */
abstract class SigningConfigEncryptionBuildService : BuildService<SigningConfigEncryptionBuildService.Params>, SigningConfigPasswordCipher {

  interface Params : BuildServiceParameters {
    val keyService: Property<Any>
  }

  private val delegate: AesGcmSigningConfigPasswordCipher by lazy {
    val service = parameters.keyService.get()
    val encodedKey =
      (service as? GlobalSigningConfigKeyService)?.getEncodedKey() ?: service.javaClass.getMethod("getEncodedKey").invoke(service) as String
    AesGcmSigningConfigPasswordCipher(encodedKey)
  }

  /**
   * Returns a non-sensitive 16-character hex fingerprint of the ephemeral encryption key used as a Gradle task input so
   * [com.android.build.gradle.internal.tasks.SigningConfigWriterTask] stays `UP-TO-DATE` when the key is reused (e.g., Configuration Cache
   * hit) and re-executes when a fresh key is generated.
   */
  fun getKeyId(): String = delegate.keyFingerprint

  override fun encryptPassword(
    fieldName: String,
    configName: String,
    storeType: String?,
    storeFilePath: String?,
    keyAlias: String?,
    plaintext: String,
  ): String = delegate.encryptPassword(fieldName, configName, storeType, storeFilePath, keyAlias, plaintext)

  override fun decryptPassword(
    fieldName: String,
    configName: String,
    storeType: String?,
    storeFilePath: String?,
    keyAlias: String?,
    ciphertext: String,
  ): String = delegate.decryptPassword(fieldName, configName, storeType, storeFilePath, keyAlias, ciphertext)

  class RegistrationAction
  @VisibleForTesting
  internal constructor(
    project: Project,
    private val keyGenerator: Callable<String>,
  ) :
    ServiceRegistrationAction<SigningConfigEncryptionBuildService, Params>(
      project,
      SigningConfigEncryptionBuildService::class.java,
    ) {

    constructor(project: Project) : this(project, Callable { generateEncodedKey() })

    override fun configure(parameters: Params) {
      parameters.keyService.set(registerGlobalKeyService(project.gradle.sharedServices, project.providers, keyGenerator))
      parameters.keyService.disallowChanges()
    }

    companion object {
      private fun registerGlobalKeyService(
        sharedServices: BuildServiceRegistry,
        providerFactory: ProviderFactory,
        keyGenerator: Callable<String>,
      ): Provider<GlobalSigningConfigKeyService> {
        return sharedServices.registerIfAbsent(
          GlobalSigningConfigKeyService::class.java.name,
          GlobalSigningConfigKeyService::class.java,
        ) { spec ->
          spec.parameters.encodedKey.setDisallowChanges(providerFactory.provider(keyGenerator))
        }
      }
    }
  }

  companion object {
    private const val AES_ALGORITHM = "AES"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_SIZE_BYTES = 32
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val FORMAT_VERSION_V1: Byte = 1
    private const val KEY_ID_HEX_LENGTH = 16

    private val secureRandom = SecureRandom()

    @JvmStatic
    fun generateEncodedKey(): String {
      val keyBytes = ByteArray(KEY_SIZE_BYTES)
      secureRandom.nextBytes(keyBytes)
      return Base64.getEncoder().encodeToString(keyBytes)
    }

    @VisibleForTesting
    fun createForTesting(encodedKey: String = generateEncodedKey()): SigningConfigPasswordCipher {
      return AesGcmSigningConfigPasswordCipher(encodedKey)
    }

    private class AesGcmSigningConfigPasswordCipher(encodedKey: String) : SigningConfigPasswordCipher {
      private val secretKeySpec: SecretKeySpec =
        Base64.getDecoder().decode(encodedKey).let { keyBytes ->
          require(keyBytes.size == KEY_SIZE_BYTES) {
            "Invalid signing config encryption key length: expected $KEY_SIZE_BYTES bytes."
          }
          SecretKeySpec(keyBytes, AES_ALGORITHM)
        }

      val keyFingerprint: String = Hashing.sha256().hashBytes(secretKeySpec.encoded).toString().substring(0, KEY_ID_HEX_LENGTH)

      override fun encryptPassword(
        fieldName: String,
        configName: String,
        storeType: String?,
        storeFilePath: String?,
        keyAlias: String?,
        plaintext: String,
      ): String {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(buildAssociatedData(fieldName, configName, storeType, storeFilePath, keyAlias))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

        val payload = ByteArray(1 + iv.size + ciphertext.size)
        payload[0] = FORMAT_VERSION_V1
        System.arraycopy(iv, 0, payload, 1, iv.size)
        System.arraycopy(ciphertext, 0, payload, 1 + iv.size, ciphertext.size)
        return Base64.getEncoder().encodeToString(payload)
      }

      override fun decryptPassword(
        fieldName: String,
        configName: String,
        storeType: String?,
        storeFilePath: String?,
        keyAlias: String?,
        ciphertext: String,
      ): String {
        val payload =
          try {
            Base64.getDecoder().decode(ciphertext)
          } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Malformed encrypted signing config $fieldName.", e)
          }
        val minLength = 1 + GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / Byte.SIZE_BITS)
        if (payload.size < minLength || payload[0] != FORMAT_VERSION_V1) {
          throw IllegalStateException("Unsupported or corrupted encrypted signing config $fieldName.")
        }

        val iv = payload.copyOfRange(1, 1 + GCM_IV_LENGTH_BYTES)
        val encryptedBytes = payload.copyOfRange(1 + GCM_IV_LENGTH_BYTES, payload.size)

        return try {
          val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
          cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
          cipher.updateAAD(buildAssociatedData(fieldName, configName, storeType, storeFilePath, keyAlias))
          String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8)
        } catch (e: GeneralSecurityException) {
          throw IllegalStateException(
            "Failed to decrypt signing config $fieldName; the encryption key or signing config data has changed.",
            e,
          )
        }
      }
    }

    private fun buildAssociatedData(
      fieldName: String,
      configName: String,
      storeType: String?,
      storeFilePath: String?,
      keyAlias: String?,
    ): ByteArray {
      val output = ByteArrayOutputStream()
      DataOutputStream(output).use { data ->
        writeString(data, fieldName)
        writeString(data, configName)
        writeNullableString(data, storeType)
        writeNullableString(data, storeFilePath)
        writeNullableString(data, keyAlias)
      }
      return output.toByteArray()
    }

    private fun writeString(data: DataOutputStream, value: String) {
      val bytes = value.toByteArray(StandardCharsets.UTF_8)
      data.writeInt(bytes.size)
      data.write(bytes)
    }

    private fun writeNullableString(data: DataOutputStream, value: String?) {
      if (value == null) {
        data.writeByte(0)
      } else {
        data.writeByte(1)
        writeString(data, value)
      }
    }
  }
}
