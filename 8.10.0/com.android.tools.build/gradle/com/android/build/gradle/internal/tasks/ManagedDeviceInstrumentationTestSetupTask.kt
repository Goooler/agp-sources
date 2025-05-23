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

package com.android.build.gradle.internal.tasks

import com.android.build.api.dsl.TestOptions
import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.SdkComponentsBuildService.VersionedSdkLoader
import com.android.build.gradle.internal.computeAbiFromArchitecture
import com.android.build.gradle.internal.computeAvdName
import com.android.build.gradle.internal.computeManagedDeviceEmulatorMode
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.testing.utp.ManagedDeviceImageSuggestionGenerator
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.repository.Revision
import com.android.testing.utils.computeSystemImageHashFromDsl
import com.android.testing.utils.isTvOrAutoDevice
import com.android.testing.utils.isTvOrAutoSource
import com.android.testing.utils.getPageAlignmentSuffix
import com.android.utils.osArchitecture
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault

/**
 * Task to create an AVD from a managed device definition in the DSL.
 *
 * Expands the dsl from a [ManagedVirtualDevice] definition in [TestOptions.devices] to a functional
 * Android Virtual Device to be used with the emulator. This includes the downloading of required
 * system images, configuring the AVD, and creating an AVD snapshot.
 *
 * This task is required as a dependency for all Unified Testing Platform Tasks that require this
 * device.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class ManagedDeviceInstrumentationTestSetupTask: NonIncrementalGlobalTask() {

    @get: Internal
    abstract val sdkService: Property<SdkComponentsBuildService>

    @get: Internal
    abstract val avdService: Property<AvdComponentsBuildService>

    @get: Input
    abstract val compileSdkVersion: Property<String>

    @get: Input
    abstract val buildToolsRevision: Property<Revision>

    @get: Input
    abstract val abi: Property<String>

    @get: Input
    abstract val sdkVersion: Property<Int>

    @get: Input
    abstract val systemImageVendor: Property<String>

    @get: Input
    @get: Optional
    abstract val sdkExtensionVersion: Property<Int>

    @get: Input
    abstract val pageAlignmentSuffix: Property<String>

    @get: Input
    abstract val hardwareProfile: Property<String>

    @get: Input
    abstract val emulatorGpuFlag: Property<String>

    // Used in error messaging
    @get: Internal
    abstract val managedDeviceName: Property<String>

    // Used in error messaging.
    @get: Internal
    abstract val require64Bit: Property<Boolean>

    override fun doTaskAction() {
        assertSourceDoesNotIncludePageAlignment()
        assertNoTvOrAuto()

        workerExecutor.noIsolation().submit(ManagedDeviceSetupRunnable::class.java) {
            it.initializeWith(projectPath, path, analyticsService)
            it.sdkService.set(sdkService)
            it.compileSdkVersion.set(compileSdkVersion)
            it.buildToolsRevision.set(buildToolsRevision)
            it.avdService.set(avdService)
            it.deviceName.set(
                computeAvdName(
                    sdkVersion.get(),
                    sdkExtensionVersion.orNull,
                    systemImageVendor.get(),
                    pageAlignmentSuffix.get(),
                    abi.get(),
                    hardwareProfile.get()))
            it.hardwareProfile.set(hardwareProfile)
            it.emulatorGpuFlag.set(emulatorGpuFlag)
            it.managedDeviceName.set(managedDeviceName)
            it.systemImageVendor.set(systemImageVendor)
            it.pageAlignmentSuffix.set(pageAlignmentSuffix)
            it.sdkVersion.set(sdkVersion)
            it.sdkExtensionVersion.set(sdkExtensionVersion)
            it.require64Bit.set(require64Bit)
            it.abi.set(abi)
        }
    }

    private fun assertNoTvOrAuto() {
        // Since we presently don't support tv or auto devices, we need to check
        // if the developer is trying to use an image from those sources.
        if (isTvOrAutoSource(systemImageVendor.get())) {
            error(
                """
                    ${managedDeviceName.get()} has a systemImageSource of ${systemImageVendor.get()}.
                    TV and Auto devices are presently not supported with Gradle Managed Devices.
                """.trimIndent()
            )
        }

        // Or is attempting to use a tv or automotive device profile.
        if (isTvOrAutoDevice(hardwareProfile.get())) {
            error(
                """
                    ${managedDeviceName.get()} has a device profile of ${hardwareProfile.get()}.
                    TV and Auto devices are presently not supported with Gradle Managed Devices.
                """.trimIndent()
            )
        }
    }

    private fun assertSourceDoesNotIncludePageAlignment() {
        // Developers should not explicitly specify page alignment in the system image source.
        // This can cause problems with determining the system image, as well as image
        // recommendations
        val sourcePageAlignment = getPageAlignmentSuffix(systemImageVendor.get())
        if (sourcePageAlignment != null) {
            error(
                """
                    ${managedDeviceName.get()} has a systemImageSource = ${systemImageVendor.get()},
                    The system image source should not include page alignment information
                    ($sourcePageAlignment). Use the ManagedVirtualDevice.pageAlignment instead.
                """.trimIndent()
            )
        }
    }

    abstract class ManagedDeviceSetupRunnable : ProfileAwareWorkAction<ManagedDeviceSetupParams>() {
        override fun run() {
            val versionedSdkLoader = parameters.sdkService.get().sdkLoader(
                compileSdkVersion = parameters.compileSdkVersion,
                buildToolsRevision = parameters.buildToolsRevision
            )

            val imageHash = computeImageHash()
            val sdkImageProvider = versionedSdkLoader.sdkImageDirectoryProvider(imageHash)
            if (!sdkImageProvider.isPresent) {
                error(generateSystemImageErrorMessage(
                    parameters.managedDeviceName.get(),
                    parameters.sdkVersion.get(),
                    parameters.sdkExtensionVersion.orNull,
                    parameters.systemImageVendor.get(),
                    parameters.pageAlignmentSuffix.get(),
                    parameters.require64Bit.get(),
                    versionedSdkLoader))
            }

            // Need to ensure that the emulator is downloaded before the avd is created. This can
            // cause issues when the system image does not mark the emulator as a dependency.
            versionedSdkLoader.emulatorDirectoryProvider.get()

            parameters.avdService.get().avdProvider(
                sdkImageProvider,
                imageHash,
                parameters.deviceName.get(),
                parameters.hardwareProfile.get()).get()

            parameters.avdService.get().ensureLoadableSnapshot(
                parameters.deviceName.get(),
                parameters.emulatorGpuFlag.get())
        }

        private fun computeImageHash(): String =
            computeSystemImageHashFromDsl(
                parameters.sdkVersion.get(),
                parameters.sdkExtensionVersion.orNull,
                parameters.systemImageVendor.get(),
                parameters.pageAlignmentSuffix.get(),
                parameters.abi.get())
    }

    abstract class ManagedDeviceSetupParams : ProfileAwareWorkAction.Parameters() {
        abstract val sdkService: Property<SdkComponentsBuildService>
        abstract val compileSdkVersion: Property<String>
        abstract val buildToolsRevision: Property<Revision>
        abstract val avdService: Property<AvdComponentsBuildService>
        abstract val deviceName: Property<String>
        abstract val hardwareProfile: Property<String>
        abstract val emulatorGpuFlag: Property<String>
        abstract val managedDeviceName: Property<String>
        abstract val systemImageVendor: Property<String>
        abstract val pageAlignmentSuffix: Property<String>
        abstract val sdkVersion: Property<Int>
        abstract val sdkExtensionVersion: Property<Int>
        abstract val require64Bit: Property<Boolean>
        abstract val abi: Property<String>
    }

    class CreationAction(
        override val name: String,
        private val systemImageSource: String,
        private val pageAlignmentSuffix: String,
        private val sdkVersion: Int,
        private val sdkExtensionVersion: Int?,
        private val abi: String,
        private val hardwareProfile: String,
        private val managedDeviceName: String,
        private val require64Bit: Boolean,
        private val creationConfig: GlobalTaskCreationConfig
    ) : GlobalTaskCreationAction<ManagedDeviceInstrumentationTestSetupTask>() {

        constructor(
            name: String,
            managedDevice: ManagedVirtualDevice,
            creationConfig: GlobalTaskCreationConfig
        ): this(
            name,
            managedDevice.systemImageSource,
            managedDevice.pageAlignmentSuffix,
            managedDevice.sdkVersion,
            managedDevice.sdkExtensionVersion,
            computeAbiFromArchitecture(managedDevice),
            managedDevice.device,
            managedDevice.name,
            managedDevice.require64Bit,
            creationConfig)

        override val type: Class<ManagedDeviceInstrumentationTestSetupTask>
            get() = ManagedDeviceInstrumentationTestSetupTask::class.java

        override fun configure(task: ManagedDeviceInstrumentationTestSetupTask) {
            super.configure(task)
            task.sdkService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
            task.compileSdkVersion.setDisallowChanges(creationConfig.compileSdkHashString)
            task.buildToolsRevision.setDisallowChanges(creationConfig.buildToolsRevision)
            task.avdService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )

            task.systemImageVendor.setDisallowChanges(systemImageSource)
            task.pageAlignmentSuffix.setDisallowChanges(pageAlignmentSuffix)
            task.sdkVersion.setDisallowChanges(sdkVersion)
            task.sdkExtensionVersion.setDisallowChanges(sdkExtensionVersion)
            task.abi.setDisallowChanges(abi)
            task.hardwareProfile.setDisallowChanges(hardwareProfile)

            task.emulatorGpuFlag.setDisallowChanges(
                computeManagedDeviceEmulatorMode(creationConfig.services.projectOptions)
            )

            task.managedDeviceName.setDisallowChanges(managedDeviceName)
            task.require64Bit.setDisallowChanges(require64Bit)
        }
    }

    companion object {
        @VisibleForTesting
        fun generateSystemImageErrorMessage(
            deviceName: String,
            sdkVersion: Int,
            extensionVersion: Int?,
            systemImageSource: String,
            pageAlignmentSuffix: String,
            require64Bit: Boolean,
            versionedSdkLoader: VersionedSdkLoader
        ) : String {
            // If the system image wasn't available. Check to see if we are offline.
            if (versionedSdkLoader.offlineMode) {
                return """
                    The system image for $deviceName is not available and Gradle is in offline mode.
                    Could not download the image or find other compatible images.
                """.trimIndent()
            }

            val allImages = versionedSdkLoader.allSystemImageHashes() ?: listOf()

            return ManagedDeviceImageSuggestionGenerator(
                osArchitecture,
                deviceName,
                sdkVersion,
                extensionVersion,
                systemImageSource,
                pageAlignmentSuffix,
                require64Bit,
                allImages
            ).message
        }
    }
}
