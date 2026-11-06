/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.api.dsl.SigningConfig
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.SigningConfigUtils
import com.google.common.hash.Hashing
import java.io.File
import java.io.Serializable
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * Encapsulates different ways to get the signing config information. It may be `null`.
 *
 * This class is designed to be used by tasks that are interested in the actual signing config information, not the ways to get that
 * information (i.e., *how* to get the info is internal to this class).
 *
 * Those tasks should then annotate this object with `@Nested`, so that if the signing config information has changed, the tasks will be
 * re-executed with the updated info.
 */
abstract class SigningConfigDataProvider {

  @get:Input abstract val name: Property<String>

  @get:Internal abstract val storeFile: Property<File>

  @get:Input @get:Optional abstract val storeType: Property<String>

  @get:Input @get:Optional abstract val keyAlias: Property<String>

  @get:Internal abstract val storePassword: Property<String>

  @get:Internal abstract val keyPassword: Property<String>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:Optional
  abstract val signingConfigFileCollection: ConfigurableFileCollection

  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) @get:Optional abstract val signingConfigValidationResultDir: DirectoryProperty

  @get:Input
  @get:Optional
  val storePasswordHash: Provider<String>
    get() = storePassword.map { Hashing.sha256().hashUnencodedChars(it).toString() }

  @get:Input
  @get:Optional
  val keyPasswordHash: Provider<String>
    get() = keyPassword.map { Hashing.sha256().hashUnencodedChars(it).toString() }

  /** Resolves this provider to get the signing config information. It may be `null`. */
  fun resolve(): SigningConfigData? {
    if (!signingConfigFileCollection.isEmpty) {
      return SigningConfigUtils.loadSigningConfigData(signingConfigFileCollection.singleFile)
    }
    val file = storeFile.orNull ?: return null
    return SigningConfigData(
      name = name.getOrElse(""),
      storeType = storeType.orNull,
      storeFile = file,
      storePassword = storePassword.orNull,
      keyAlias = keyAlias.orNull,
      keyPassword = keyPassword.orNull,
    )
  }

  /** Converts this provider to [SigningConfigProviderParams] to be used by Gradle workers. */
  fun convertToParams(): SigningConfigProviderParams {
    return SigningConfigProviderParams(resolve(), if (signingConfigFileCollection.isEmpty) null else signingConfigFileCollection.singleFile)
  }

  companion object {

    @JvmStatic
    fun create(objectFactory: ObjectFactory, signingConfig: SigningConfig): SigningConfigDataProvider {
      val provider = objectFactory.newInstance(SigningConfigDataProvider::class.java)
      provider.name.set("default")
      provider.storeFile.set(signingConfig.storeFile)
      provider.storeType.set(signingConfig.storeType)
      provider.keyAlias.set(signingConfig.keyAlias)
      provider.storePassword.set(signingConfig.storePassword)
      provider.keyPassword.set(signingConfig.keyPassword)
      return provider
    }

    @JvmStatic
    fun create(creationConfig: ApkCreationConfig): SigningConfigDataProvider {
      val isInDynamicFeature =
        creationConfig.componentType.isDynamicFeature ||
          (creationConfig is TestComponentCreationConfig && creationConfig.mainVariant.componentType.isDynamicFeature)

      val provider = creationConfig.services.newInstance(SigningConfigDataProvider::class.java)
      provider.name.set("")

      // We want to avoid writing the signing config information to disk to protect sensitive
      // data (see bug 137210434), so we'll attempt to get this information directly from
      // memory first.
      if (!isInDynamicFeature) {
        // Get it from the variant scope
        val signingConfig = creationConfig.signingConfig
        if (signingConfig != null) {
          provider.name.set(signingConfig.name ?: "")
          provider.storeFile.set(signingConfig.storeFile)
          provider.storeType.set(signingConfig.storeType)
          provider.keyAlias.set(signingConfig.keyAlias)
          provider.storePassword.set(signingConfig.storePassword)
          provider.keyPassword.set(signingConfig.keyPassword)
        }
        provider.signingConfigValidationResultDir.set(creationConfig.artifacts.get(InternalArtifactType.VALIDATE_SIGNING_CONFIG))
      } else {
        // Get it from the injected properties passed from the IDE
        val signingConfigData = SigningConfigData.fromProjectOptions(creationConfig.services.projectOptions)

        if (signingConfigData != null) {
          // Validation for this case is currently missing because the base module
          // doesn't publish its validation result so that we can use it here.
          // However, normally the users would build both the base module and the
          // dynamic feature module, therefore the signing config info for both
          // modules would be validated when the base module is built, so it may be
          // acceptable to not validate it here.
          provider.name.set(signingConfigData.name)
          provider.storeFile.set(signingConfigData.storeFile)
          provider.storeType.set(signingConfigData.storeType)
          provider.keyAlias.set(signingConfigData.keyAlias)
          provider.storePassword.set(signingConfigData.storePassword)
          provider.keyPassword.set(signingConfigData.keyPassword)
        } else {
          // Otherwise, get it from the published artifact
          // Validation is taken care of by the task in the base module that publishes
          // the signing config info (SigningConfigWriterTask).
          provider.signingConfigFileCollection.from(
            creationConfig.variantDependencies.getArtifactFileCollection(
              AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
              AndroidArtifacts.ArtifactScope.PROJECT,
              AndroidArtifacts.ArtifactType.FEATURE_SIGNING_CONFIG_DATA,
            )
          )
        }
      }
      return provider
    }
  }
}

/** Similar to [SigningConfigDataProvider], but uses a [File] instead of a [FileCollection] to be used by Gradle workers. */
class SigningConfigProviderParams(private val signingConfigData: SigningConfigData?, private val signingConfigFile: File?) : Serializable {

  /** Resolves this provider to get the signing config information. It may be `null`. */
  fun resolve(): SigningConfigData? {
    return signingConfigData ?: signingConfigFile?.let { SigningConfigUtils.loadSigningConfigData(it) }
  }

  companion object {
    private const val serialVersionUID = 1L
  }
}
