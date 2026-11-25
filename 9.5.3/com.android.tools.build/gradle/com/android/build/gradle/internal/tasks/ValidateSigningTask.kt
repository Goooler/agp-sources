/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.SigningConfig
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.packaging.createDefaultDebugStore
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.signing.SigningConfigDataProvider
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.BuilderConstants
import com.android.builder.signing.DefaultSigningConfig
import com.android.builder.utils.SynchronizedFile
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions.checkState
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.ExecutionException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * A Gradle Task to check that the keystore file is present for this variant's signing config.
 *
 * If the keystore is the default debug keystore, it will be created if it is missing.
 *
 * This task has the signing config metadata (excluding passwords) as nested inputs, and a dummy output directory to allow it to be
 * up-to-date.
 *
 * As enabling caching for a verification task that only checks file presence serves no useful purpose, it is disabled by default.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.VERIFICATION)
abstract class ValidateSigningTask : NonIncrementalTask() {

  /**
   * Output directory to allow this task to be up-to-date, despite the the signing config file not being modelled directly as an input or an
   * output.
   */
  @get:OutputDirectory abstract val dummyOutputDirectory: DirectoryProperty

  @get:Internal abstract val signingConfig: Property<SigningConfigDataProvider>

  /**
   * Exposing keyAlias as a flat input forces Gradle to resolve the custom signing config provider (if any) before this task runs, ensuring
   * any configuration errors (e.g., reading missing files in a custom ValueSource) are caught early.
   *
   * We keep [signingConfig] as [Internal] to avoid input/output overlap issues since this task writes to the default debug keystore which
   * would otherwise be tracked as a nested input.
   */
  @get:Input
  @get:Optional
  val keyAlias: Provider<String>
    get() = signingConfig.flatMap { it.keyAlias }

  @get:Internal abstract val defaultDebugKeystoreLocation: Property<File>

  override fun doTaskAction() {
    val provider = signingConfig.get()
    val storeFile = provider.storeFile.orNull
    when {
      storeFile == null -> throw InvalidUserDataException("""Keystore file not set for signing config ${provider.name.getOrElse("")}""")
      isSigningConfigUsingTheDefaultDebugKeystore(provider) ->
        /* Check if the debug keystore is being used rather than directly checking if it
        already exists. A "fast path" of returning true if the store file is present would
        allow one task to return while another validate task has only partially written the
        default debug keystore file, which could lead to confusing transient build errors. */
        createDefaultDebugKeystoreIfNeeded()
      storeFile.isFile -> {
        /* Keystore file is present, allow the build to continue. */
      }
      else ->
        throw InvalidUserDataException(
          """Keystore file '${storeFile.absolutePath}' """ + """not found for signing config '${provider.name.getOrElse("")}'."""
        )
    }
  }

  @Throws(ExecutionException::class, IOException::class)
  private fun createDefaultDebugKeystoreIfNeeded() {
    checkState(defaultDebugKeystoreLocation.isPresent, "Debug keystore location is not specified.")
    // Synchronized file with multi process locking requires that the parent directory of the
    // default debug keystore is present.
    val location = defaultDebugKeystoreLocation.get()
    FileUtils.mkdirs(location.parentFile)

    if (!location.parentFile.canWrite()) {
      throw IOException("""Unable to create debug keystore in """ + """${location.parentFile.absolutePath} because it is not writable.""")
    }

    /* Creating the debug keystore is done with the multi process file locking,
    to avoid one validate signing task from exiting early while the keystore is in the
    process of being written.
    The keystore is not locked in the task input presence check or where it is used at
    application packaging.

    This is generally safe as the keystore is only automatically created,
    never automatically deleted.  */
    SynchronizedFile.getInstanceWithMultiProcessLocking(location).createIfAbsent { createDefaultDebugStore(it, this.logger) }
  }

  private fun isSigningConfigUsingTheDefaultDebugKeystore(provider: SigningConfigDataProvider): Boolean {
    val storeFileValue = provider.storeFile.orNull ?: return false
    val defaultDebugLocation = defaultDebugKeystoreLocation.orNull ?: return false
    if (!storeFileValue.isSameFile(defaultDebugLocation)) {
      return false
    }
    return provider.name.getOrElse("") == BuilderConstants.DEBUG &&
      provider.keyAlias.orNull == DefaultSigningConfig.DEFAULT_ALIAS &&
      provider.keyPassword.orNull == DefaultSigningConfig.DEFAULT_PASSWORD &&
      provider.storePassword.orNull == DefaultSigningConfig.DEFAULT_PASSWORD &&
      provider.storeType.orNull == KeyStore.getDefaultType()
  }

  private fun File?.isSameFile(other: File?) = this != null && other != null && FileUtils.isSameFile(this, other)

  /**
   * Always re-run if the store file is not present to prevent the task being UP-TO-DATE if the keystore is deleted after the first run.
   * (See [CreationAction.execute]) Other changes, such as the first time it is run, or if the project is cleaned, or if the plugin
   * classpath is changed will also cause this task to be re-run.
   */
  @VisibleForTesting
  fun forceRerun(): Boolean {
    val storeFile: File? = signingConfig.get().storeFile.orNull
    return storeFile == null || !storeFile.isFile
  }

  class CreationForAssetPackBundleAction(private val artifacts: ArtifactsImpl, private val signingConfig: SigningConfig) :
    TaskCreationAction<ValidateSigningTask>() {

    override val type = ValidateSigningTask::class.java
    override val name = "validateSigning"

    override fun handleProvider(taskProvider: TaskProvider<ValidateSigningTask>) {
      super.handleProvider(taskProvider)
      artifacts.setInitialProvider(taskProvider, ValidateSigningTask::dummyOutputDirectory).on(InternalArtifactType.VALIDATE_SIGNING_CONFIG)
    }

    override fun configure(task: ValidateSigningTask) {
      UsesAnalytics.ConfigureAction.configure(task)
      task.signingConfig.set(SigningConfigDataProvider.create(task.project.objects, signingConfig))
      task.outputs.upToDateWhen { !task.forceRerun() }
    }
  }

  class CreationAction(creationConfig: ApkCreationConfig, private val defaultDebugKeystoreLocation: File) :
    VariantTaskCreationAction<ValidateSigningTask, ApkCreationConfig>(creationConfig) {

    override val name: String
      get() = computeTaskName("validateSigning")

    override val type: Class<ValidateSigningTask>
      get() = ValidateSigningTask::class.java

    override fun handleProvider(taskProvider: TaskProvider<ValidateSigningTask>) {
      super.handleProvider(taskProvider)
      creationConfig.artifacts
        .setInitialProvider(taskProvider, ValidateSigningTask::dummyOutputDirectory)
        .on(InternalArtifactType.VALIDATE_SIGNING_CONFIG)
    }

    override fun configure(task: ValidateSigningTask) {
      super.configure(task)

      val signingConfig =
        creationConfig.signingConfig ?: throw IllegalStateException("No signing config configured for variant " + creationConfig.name)
      val provider = creationConfig.services.newInstance(SigningConfigDataProvider::class.java)
      provider.name.set(creationConfig.services.provider { signingConfig.name })
      provider.storeFile.set(signingConfig.storeFile)
      provider.storeType.set(signingConfig.storeType)
      provider.keyAlias.set(signingConfig.keyAlias)
      provider.storePassword.set(signingConfig.storePassword)
      provider.keyPassword.set(signingConfig.keyPassword)
      task.signingConfig.set(provider)
      task.defaultDebugKeystoreLocation.set(defaultDebugKeystoreLocation)
      task.outputs.upToDateWhen { !task.forceRerun() }
    }
  }
}
