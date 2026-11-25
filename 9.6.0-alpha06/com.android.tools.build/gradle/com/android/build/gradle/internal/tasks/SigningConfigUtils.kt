/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.signing.SigningConfigData
import com.android.build.gradle.internal.signing.SigningConfigPasswordCipher
import com.android.build.gradle.internal.signing.SigningConfigVersions
import com.android.build.gradle.options.SigningOptions
import com.google.common.hash.Hashing
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.EnumSet
import org.apache.commons.io.FileUtils

/** Utility class to save/load the signing config information to/from a json file. */
class SigningConfigUtils {

  companion object {
    private const val FIELD_STORE_PASSWORD = "storePassword"
    private const val FIELD_KEY_PASSWORD = "keyPassword"
    private const val FINGERPRINT_DOMAIN = "signing-config-fingerprint-v1"

    /** Saves the [SigningConfigData] information to the outputFile with passwords encrypted using [encryptionService]. */
    fun saveSigningConfigData(
      outputFile: File,
      signingConfigData: SigningConfigData?,
      encryptionService: SigningConfigPasswordCipher? = null,
    ) {
      saveSigningConfigData(outputFile, signingConfigData) { encryptionService }
    }

    /**
     * Saves the [SigningConfigData] information to the outputFile, lazily obtaining a [SigningConfigPasswordCipher] from
     * [encryptionServiceProvider] only when passwords are present.
     */
    fun saveSigningConfigData(
      outputFile: File,
      signingConfigData: SigningConfigData?,
      encryptionServiceProvider: () -> SigningConfigPasswordCipher?,
    ) {
      val serializedData = signingConfigData?.let { data ->
        val cipher =
          if (data.hasPasswords()) {
            encryptionServiceProvider()
              ?: throw IllegalStateException("SigningConfigEncryptionBuildService is required to save signing config passwords.")
          } else {
            null
          }
        SerializedSigningConfigData(
          name = data.name,
          storeType = data.storeType,
          storeFile = data.storeFile,
          encryptedStorePassword =
            data.storePassword?.let {
              cipher!!.encryptPassword(
                fieldName = FIELD_STORE_PASSWORD,
                configName = data.name,
                storeType = data.storeType,
                storeFilePath = data.storeFile?.path,
                keyAlias = data.keyAlias,
                plaintext = it,
              )
            },
          keyAlias = data.keyAlias,
          encryptedKeyPassword =
            data.keyPassword?.let {
              cipher!!.encryptPassword(
                fieldName = FIELD_KEY_PASSWORD,
                configName = data.name,
                storeType = data.storeType,
                storeFilePath = data.storeFile?.path,
                keyAlias = data.keyAlias,
                plaintext = it,
              )
            },
        )
      }

      writeRestrictedBytes(outputFile.toPath(), gson.toJson(serializedData).toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Writes a deterministic, secret-free SHA-256 fingerprint of [signingConfigData] to [outputFile] so downstream tasks can track logical
     * signing configuration changes without re-executing when ephemeral encryption keys rotate.
     */
    fun saveSigningConfigFingerprint(outputFile: File, signingConfigData: SigningConfigData?) {
      val fingerprint = computeSigningConfigFingerprint(signingConfigData)
      val outputPath = outputFile.toPath()
      outputPath.parent?.let { Files.createDirectories(it) }
      Files.deleteIfExists(outputPath)
      Files.writeString(outputPath, fingerprint, StandardCharsets.UTF_8)
    }

    internal fun computeSigningConfigFingerprint(signingConfigData: SigningConfigData?): String {
      val baos = ByteArrayOutputStream()
      DataOutputStream(baos).use { out ->
        writeLengthPrefixedString(out, FINGERPRINT_DOMAIN)
        if (signingConfigData == null) {
          out.writeBoolean(false)
        } else {
          out.writeBoolean(true)
          writeLengthPrefixedString(out, signingConfigData.name)
          writeOptionalLengthPrefixedString(out, signingConfigData.storeType)
          writeOptionalLengthPrefixedString(out, signingConfigData.storeFile?.path)
          writeOptionalKeystoreFileHash(out, signingConfigData.storeFile)
          writeOptionalLengthPrefixedString(out, signingConfigData.storePassword)
          writeOptionalLengthPrefixedString(out, signingConfigData.keyAlias)
          writeOptionalLengthPrefixedString(out, signingConfigData.keyPassword)
        }
      }
      return Hashing.sha256().hashBytes(baos.toByteArray()).toString()
    }

    private fun writeOptionalKeystoreFileHash(out: DataOutputStream, storeFile: File?) {
      val path = storeFile?.toPath()
      if (path == null) {
        out.writeByte(0)
      } else if (Files.isRegularFile(path)) {
        out.writeByte(1)
        val hasher = Hashing.sha256().newHasher()
        val buffer = ByteArray(8192)
        Files.newInputStream(path).use { input ->
          while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            hasher.putBytes(buffer, 0, read)
          }
        }
        val digest = hasher.hash().asBytes()
        out.writeInt(digest.size)
        out.write(digest)
      } else {
        out.writeByte(2)
      }
    }

    private fun writeLengthPrefixedString(out: DataOutputStream, value: String) {
      val bytes = value.toByteArray(StandardCharsets.UTF_8)
      out.writeInt(bytes.size)
      out.write(bytes)
    }

    private fun writeOptionalLengthPrefixedString(out: DataOutputStream, value: String?) {
      if (value == null) {
        out.writeBoolean(false)
      } else {
        out.writeBoolean(true)
        writeLengthPrefixedString(out, value)
      }
    }

    /** Saves the [SigningConfigVersions] information to the outputFile. */
    fun saveSigningConfigVersions(outputFile: File, signingConfigVersions: SigningConfigVersions) {
      FileUtils.write(outputFile, gson.toJson(signingConfigVersions), StandardCharsets.UTF_8)
    }

    /** Loads the [SigningConfigData] information from a json file and decrypts any stored passwords using [encryptionService]. */
    fun loadSigningConfigData(
      input: File,
      encryptionService: SigningConfigPasswordCipher? = null,
    ): SigningConfigData? {
      return loadSigningConfigData(input) { encryptionService }
    }

    /**
     * Loads the [SigningConfigData] information from a json file, lazily obtaining a [SigningConfigPasswordCipher] from
     * [encryptionServiceProvider] only when encrypted passwords are present.
     */
    fun loadSigningConfigData(
      input: File,
      encryptionServiceProvider: () -> SigningConfigPasswordCipher?,
    ): SigningConfigData? {
      val serializedData =
        input.bufferedReader(StandardCharsets.UTF_8).use { reader ->
          gson.fromJson(reader, SerializedSigningConfigData::class.java)
        } ?: return null

      val hasEncryptedPasswords = serializedData.encryptedStorePassword != null || serializedData.encryptedKeyPassword != null
      val cipher =
        if (hasEncryptedPasswords) {
          encryptionServiceProvider()
            ?: throw IllegalStateException("SigningConfigEncryptionBuildService is required to load encrypted signing config passwords.")
        } else {
          null
        }

      val configName = serializedData.name
      return SigningConfigData(
        name = configName,
        storeType = serializedData.storeType,
        storeFile = serializedData.storeFile,
        storePassword =
          serializedData.encryptedStorePassword?.let {
            cipher!!.decryptPassword(
              fieldName = FIELD_STORE_PASSWORD,
              configName = configName,
              storeType = serializedData.storeType,
              storeFilePath = serializedData.storeFile?.path,
              keyAlias = serializedData.keyAlias,
              ciphertext = it,
            )
          },
        keyAlias = serializedData.keyAlias,
        keyPassword =
          serializedData.encryptedKeyPassword?.let {
            cipher!!.decryptPassword(
              fieldName = FIELD_KEY_PASSWORD,
              configName = configName,
              storeType = serializedData.storeType,
              storeFilePath = serializedData.storeFile?.path,
              keyAlias = serializedData.keyAlias,
              ciphertext = it,
            )
          },
      )
    }

    private fun writeRestrictedBytes(outputPath: Path, bytes: ByteArray) {
      outputPath.parent?.let { Files.createDirectories(it) }
      // Delete any pre-existing file or symlink (without following symlinks) so CREATE_NEW atomically
      // creates a fresh file owned by the current user with restricted permissions.
      Files.deleteIfExists(outputPath)
      if (SdkConstants.CURRENT_PLATFORM != SdkConstants.PLATFORM_WINDOWS) {
        val perms = PosixFilePermissions.asFileAttribute(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        Files.newByteChannel(
            outputPath,
            EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
            perms,
          )
          .use { channel ->
            var offset = 0
            while (offset < bytes.size) {
              offset += channel.write(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
            }
          }
      } else {
        // on windows, atomically create the file first and restrict its ACL to owner read/write before writing bytes.
        Files.createFile(outputPath)
        val view = Files.getFileAttributeView(outputPath, AclFileAttributeView::class.java)
        val entry =
          AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(view.owner)
            .setPermissions(
              AclEntryPermission.READ_ACL,
              AclEntryPermission.READ_NAMED_ATTRS,
              AclEntryPermission.READ_DATA,
              AclEntryPermission.READ_ATTRIBUTES,
              AclEntryPermission.WRITE_ACL,
              AclEntryPermission.WRITE_DATA,
              AclEntryPermission.APPEND_DATA,
              AclEntryPermission.WRITE_NAMED_ATTRS,
              AclEntryPermission.WRITE_ATTRIBUTES,
              AclEntryPermission.WRITE_OWNER,
              AclEntryPermission.SYNCHRONIZE,
              AclEntryPermission.DELETE,
            )
            .build()
        view.acl = listOf(entry)
        Files.newByteChannel(outputPath, EnumSet.of(StandardOpenOption.WRITE)).use { channel ->
          var offset = 0
          while (offset < bytes.size) {
            offset += channel.write(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
          }
        }
      }
    }

    private data class SerializedSigningConfigData(
      val name: String,
      val storeType: String?,
      val storeFile: File?,
      val encryptedStorePassword: String?,
      val keyAlias: String?,
      val encryptedKeyPassword: String?,
    )

    /** Loads the [SigningConfigVersions] information from a json file. */
    @JvmStatic
    fun loadSigningConfigVersions(input: File): SigningConfigVersions {
      return input.bufferedReader(StandardCharsets.UTF_8).use { reader -> gson.fromJson(reader, SigningConfigVersions::class.java) }
    }

    private val gson: Gson by lazy {
      val gsonBuilder = GsonBuilder()
      gsonBuilder.registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
      gsonBuilder.create()
    }

    fun createSigningOverride(dslServices: DslServices): SigningConfig? {
      SigningOptions.readSigningOptions(dslServices.projectOptions)?.let { signingOptions ->
        val signingConfigDsl = dslServices.newDecoratedInstance(SigningConfig::class.java, SigningOptions.SIGNING_CONFIG_NAME, dslServices)
        signingConfigDsl.storeFile(File(signingOptions.storeFile))
        signingConfigDsl.storePassword(signingOptions.storePassword)
        signingConfigDsl.keyAlias(signingOptions.keyAlias)
        signingConfigDsl.keyPassword(signingOptions.keyPassword)
        signingOptions.storeType?.let { signingConfigDsl.storeType(it) }
        signingOptions.v1Enabled?.let { signingConfigDsl.enableV1Signing = it }
        signingOptions.v2Enabled?.let { signingConfigDsl.enableV2Signing = it }
        return signingConfigDsl
      }
      return null
    }
  }
}
