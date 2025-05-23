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

package com.android.build.gradle.internal.utils

import com.android.build.api.variant.ApkInstallGroup
import com.android.build.api.variant.DeviceSpec
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.test.BuiltArtifactsSplitOutputMatcher.computeBestOutput
import com.android.builder.internal.InstallUtils
import com.android.sdklib.AndroidVersion
import com.google.common.collect.Lists
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.TaskInputs
import java.io.File

class DefaultDeviceApkOutput(

    private val apkSources: ApkSources,
    private val supportedAbis: Set<String>?,
    private val minSdkVersion: AndroidVersion,
    private val variantName: String,
    private val projectPath: String,
    private val iLogger: LoggerWrapper = LoggerWrapper.getLogger(DefaultDeviceApkOutput::class.java)
): DeviceApkOutput {

    override fun getApks(deviceSpec: DeviceSpec): List<ApkInstallGroup>{
        val apkInstallGroups = mutableListOf<ApkInstallGroup>()
        val apkFiles: MutableList<File> = Lists.newLinkedList()
        if (InstallUtils.checkDeviceApiLevel(deviceSpec.name, deviceSpec.apiLevel, deviceSpec.codeName,
                minSdkVersion, iLogger, projectPath, variantName)
        ) {
            val mainApks = getMainApks(apkSources.mainApkArtifact.get(), supportedAbis, deviceSpec)
            if (mainApks.isNotEmpty()) {
                apkFiles.addAll(mainApks)
            }
            val privacySandboxSdksPresent =
                    !apkSources.privacySandboxSdksApksFiles.isEmpty
                            || (apkSources.additionalSupportedSdkApkSplits?.isPresent ?: false)
            if (privacySandboxSdksPresent && deviceSpec.supportsPrivacySandbox) {
                apkSources.privacySandboxSdksApksFiles.files
                    .mapNotNull { BuiltArtifactsLoaderImpl().load { it } }
                    .map { artifacts -> artifacts.applicationId to artifacts.elements.map { RegularFile { File(it.outputFile) }  } }
                    .forEach { (applicationId, sdkApkFiles) -> apkInstallGroups.add(DefaultSdkApkInstallGroup(applicationId, sdkApkFiles)) }

                apkSources.additionalSupportedSdkApkSplits?.let { apkFiles.addAll(getFiles(it)) }
            } else {
                apkSources.privacySandboxSdkSplitApksForLegacy?.let { apkFiles.addAll(getFiles(it)) }
            }

            addDexMetadataFiles(
                apkSources.dexMetadataDirectory,
                apkSources.mainApkArtifact.get(),
                deviceSpec.apiLevel,
                apkFiles,
                iLogger
            )
        }
        apkInstallGroups.add(DefaultApkInstallGroup(apkFiles.map { RegularFile { it } }, "Main Apk Group" ))
        return apkInstallGroups
    }

    override fun setInputs(inputs: TaskInputs, deviceSpec: DeviceSpec) {
        inputs.files(*getApkInputs(apkSources, deviceSpec).toTypedArray()).withNormalizer(ClasspathNormalizer::class.java)
    }

    data class DefaultApkInstallGroup(override val apks: List<RegularFile>,
        override val description: String) : ApkInstallGroup

    data class DefaultSdkApkInstallGroup(
        override val sourceSdk: String,
        override val apks: List<RegularFile>) : SdkApkInstallGroup

    companion object {
        fun getApkInputs(apkSources: ApkSources, deviceSpec: DeviceSpec): Set<Any> {
            val taskInputs = mutableSetOf<Any>(apkSources.mainApkArtifact)
            apkSources.dexMetadataDirectory?.let { taskInputs.add(it) }

            if (deviceSpec.supportsPrivacySandbox) {
                // If the device supports privacy sandbox, we depend on privacy sandbox sdk apks and support sdk apk splits.
                taskInputs.add(apkSources.privacySandboxSdksApksFiles)
                apkSources.additionalSupportedSdkApkSplits?.let { taskInputs.add(it) }
            } else {
                // If the device does not support privacy sandbox, we only depend on legacy sdk split apks.
                apkSources.privacySandboxSdkSplitApksForLegacy?.let { taskInputs.add(it) }
            }
            return taskInputs
        }
        fun getMainApks(mainApkDirectory: Directory, supportedAbis: Set<String>?, deviceSpec: DeviceSpec): List<File> {
            val builtArtifactsLoader = BuiltArtifactsLoaderImpl()
            val builtArtifacts: BuiltArtifactsImpl? = builtArtifactsLoader.load(mainApkDirectory)
            if (builtArtifacts != null) {
                return computeBestOutput(deviceSpec.abis, builtArtifacts, supportedAbis ?: setOf())
            }
            return listOf()
        }
    }
}

data class ApkSources(
    val mainApkArtifact: Provider<Directory>,
    val privacySandboxSdksApksFiles: FileCollection,
    val additionalSupportedSdkApkSplits: Provider<Directory>?,
    val privacySandboxSdkSplitApksForLegacy: Provider<Directory>?,
    val dexMetadataDirectory: Provider<Directory>? = null)

data class PrivacySandboxApkSources(
    val privacySandboxSdksApksFiles: FileCollection,
    val additionalSupportedSdkApkSplits: Provider<Directory>?,
    val privacySandboxSdkSplitApksForLegacy: Provider<Directory>?)
