/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.android.build.gradle

import com.android.annotations.NonNull
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.ComposeOptions
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import com.android.build.api.transform.Transform
import com.android.build.api.variant.VariantFilter
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.api.ViewBindingOptions
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.SourceSetSourceProviderWrapper
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.dsl.AdbOptions
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DataBindingOptions
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.DexOptions
import com.android.build.gradle.internal.dsl.ExternalNativeBuild
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.dsl.Lockable
import com.android.build.gradle.internal.dsl.PackagingOptions
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.dsl.Splits
import com.android.build.gradle.internal.dsl.TestOptions
import com.android.build.gradle.internal.dsl.ViewBindingOptionsImpl
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig
import com.android.builder.core.LibraryRequest
import com.android.builder.errors.IssueReporter
import com.android.builder.model.SourceProvider
import com.android.builder.testing.api.DeviceProvider
import com.android.builder.testing.api.TestServer
import com.android.repository.Revision
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.SourceSet
import org.gradle.declarative.dsl.model.annotations.Restricted
import java.io.File

/**
 * Base extension for all Android plugins.
 *
 * You don't use this extension directly. Instead, use one of the following:
 *
 * * [ApplicationExtension]: `android` extension for the `com.android.application` plugin
 *         used to create an Android app.
 * * [LibraryExtension]: `android` extension for the `com.android.library` plugin used to
 *         [create an Android library](https://developer.android.com/studio/projects/android-library.html)
 * * [TestExtension]: `android` extension for the `com.android.test` plugin used to create
 *         a separate android test project.
 * * [DynamicFeatureExtension]: `android` extension for the `com.android.feature` plugin
 *         used to create dynamic features.
 *
 * The following applies the Android plugin to an app project `build.gradle` file:
 *
 * ```
 * // Applies the application plugin and makes the 'android' block available to specify
 * // Android-specific build options.
 * apply plugin: 'com.android.application'
 * ```
 *
 * To learn more about creating and organizing Android projects, read
 * [Projects Overview](https://developer.android.com/studio/projects/index.html)
 */
// All the public methods are meant to be exposed in the DSL. We can't use lambdas in this class
// (yet), because the DSL reference generator doesn't understand them.
abstract class BaseExtension protected constructor(
    protected val dslServices: DslServices,
    protected val bootClasspathConfig: BootClasspathConfig,
    /** All build outputs for all variants, can be used by users to customize a build output. */
    override val buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    private val sourceSetManager: SourceSetManager,
    private val isBaseModule: Boolean,
    private val stats: GradleBuildProject.Builder?
) : AndroidConfig, Lockable {

    private var hasOldVariantApiUsage: Boolean = false

    private val _dexOptions = dslServices.newInstance(DexOptions::class.java)

    @Deprecated("Using dexOptions is obsolete.")
    override val dexOptions: DexOptions
        get() {
            dslServices.deprecationReporter.reportObsoleteUsage(
                "dexOptions",
                DeprecationReporter.DeprecationTarget.DEX_OPTIONS
            )
            return _dexOptions
        }

    private val deviceProviderList: MutableList<DeviceProvider> = Lists.newArrayList()
    private val testServerList: MutableList<TestServer> = Lists.newArrayList()

    @get:Incubating
    abstract val composeOptions: ComposeOptions

    abstract override val dataBinding: DataBindingOptions
    abstract val viewBinding: ViewBindingOptionsImpl

    override var defaultPublishConfig: String = "release"
        set(_) {
            dslServices.deprecationReporter.reportObsoleteUsage(
                    "defaultPublishConfig",
                    DeprecationReporter.DeprecationTarget.DEFAULT_PUBLISH_CONFIG
            )
        }

    override var variantFilter: Action<VariantFilter>? = null

    protected val logger: Logger = Logging.getLogger(this::class.java)

    private var isWritable = true

    /**
     * Disallow further modification on the extension.
     */
    fun disableWrite() {
        isWritable = false
        lock()
    }

    protected fun recordOldVariantApiUsage() {
        stats?.oldVariantApiInUse = true
        hasOldVariantApiUsage = true
    }

    fun hasOldVariantApiUsage() : Boolean {
        return hasOldVariantApiUsage
    }

    protected fun checkWritability() {
        if (!isWritable) {
            throw GradleException(
                "Android tasks have already been created.\n" +
                        "This happens when calling android.applicationVariants,\n" +
                        "android.libraryVariants or android.testVariants.\n" +
                        "Once these methods are called, it is not possible to\n" +
                        "continue configuring the model."
            )
        }
    }

    /** For groovy only (so `compileSdkVersion=2` works) */
    fun setCompileSdkVersion(apiLevel: Int) {
        compileSdkVersion(apiLevel)
    }

    open fun buildToolsVersion(version: String) {
        buildToolsVersion = version
    }

    open fun flavorDimensions(vararg dimensions: String) {
        checkWritability()
        flavorDimensionList.clear()
        flavorDimensionList.addAll(dimensions)
    }

    abstract fun sourceSets(action: Action<NamedDomainObjectContainer<AndroidSourceSet>>)

    abstract fun aaptOptions(action: Action<AaptOptions>)

    /**
     * Specifies options for the DEX tool, such as enabling library pre-dexing.
     *
     * For more information about the properties you can configure in this block, see [DexOptions].
     */
    @Deprecated("Setting dexOptions is obsolete.")
    fun dexOptions(action: Action<DexOptions>) {
        checkWritability()
        action.execute(dexOptions)
    }

    abstract fun lintOptions(action: Action<LintOptions>)

    abstract fun externalNativeBuild(action: Action<ExternalNativeBuild>)

    /**
     * Specifies options for how the Android plugin should run local and instrumented tests.
     *
     * For more information about the properties you can configure in this block, see [TestOptions].
     */
    abstract fun testOptions(action: Action<TestOptions>)

    abstract fun compileOptions(action: Action<CompileOptions>)

    abstract fun packagingOptions(action: Action<PackagingOptions>)

    abstract fun jacoco(action: Action<JacocoOptions>)

    /**
     * Specifies options for the
     * [Android Debug Bridge (ADB)](https://developer.android.com/studio/command-line/adb.html),
     * such as APK installation options.
     *
     * For more information about the properties you can configure in this block, see [AdbOptions].
     */
    abstract fun adbOptions(action: Action<AdbOptions>)

    abstract fun splits(action: Action<Splits>)

    /**
     * Specifies options for the
     * [Data Binding Library](https://developer.android.com/topic/libraries/data-binding/index.html).
     *
     * For more information about the properties you can configure in this block, see [DataBindingOptions]
     */
    abstract fun dataBinding(action: Action<DataBindingOptions>)

    /**
     * Specifies options for the View Binding lLibrary.
     *
     * For more information about the properties you can configure in this block, see [ViewBindingOptions].
     */
    abstract fun viewBinding(action: Action<ViewBindingOptionsImpl>)

    fun deviceProvider(deviceProvider: DeviceProvider) {
        checkWritability()
        deviceProviderList.add(deviceProvider)
    }

    override val deviceProviders: List<DeviceProvider>
        get() = deviceProviderList

    fun testServer(testServer: TestServer) {
        checkWritability()
        testServerList.add(testServer)
    }

    override val testServers: List<TestServer>
        get() = testServerList

    /**
     * The [Transform] API is planned to be removed in Android Gradle plugin 8.0.
     *
     * There is no single replacement. For more information about how to migrate, see
     * [https://developer.android.com/studio/releases/gradle-plugin-roadmap]
     */
    @Deprecated(
        "The transform API support has been removed in Android Gradle plugin 8.0."
    )
    fun registerTransform(transform: Transform, vararg dependencies: Any) {
        dslServices.deprecationReporter.reportRemovedApi(
            oldApiElement = "android.registerTransform",
            url = "https://developer.android.com/studio/releases/gradle-plugin-api-updates#transform-api",
            deprecationTarget = DeprecationReporter.DeprecationTarget.TRANSFORM_API
        )
    }

    override val transforms: List<Transform>
        get() = ImmutableList.of()

    override val transformsDependencies: List<List<Any>>
        get() = ImmutableList.of()

    open fun defaultPublishConfig(value: String) {
        defaultPublishConfig = value
    }

    fun setPublishNonDefault(publishNonDefault: Boolean) {
        logger.warn("publishNonDefault is deprecated and has no effect anymore. All variants are now published.")
    }

    @Deprecated("Use AndroidComponentsExtension.beforeVariants API to disable specific variants")
    open fun variantFilter(variantFilter: Action<VariantFilter>) {
        this.variantFilter = variantFilter
    }

    open fun resourcePrefix(prefix: String) {
        resourcePrefix = prefix
    }

    abstract fun addVariant(variant: BaseVariant)

    fun registerArtifactType(name: String, isTest: Boolean, artifactType: Int) {
    }

    fun registerBuildTypeSourceProvider(
        name: String,
        buildType: BuildType,
        sourceProvider: SourceProvider
    ) {
    }

    fun registerProductFlavorSourceProvider(
        name: String,
        productFlavor: ProductFlavor,
        sourceProvider: SourceProvider
    ) {
    }

    fun registerJavaArtifact(
        name: String,
        variant: BaseVariant,
        assembleTaskName: String,
        javaCompileTaskName: String,
        generatedSourceFolders: MutableCollection<File>,
        ideSetupTaskNames: Iterable<String>,
        configuration: Configuration,
        classesFolder: File,
        javaResourceFolder: File,
        sourceProvider: SourceProvider
    ) {

    }

    fun registerMultiFlavorSourceProvider(
        name: String,
        flavorName: String,
        sourceProvider: SourceProvider
    ) {
    }

    @NonNull
    fun wrapJavaSourceSet(sourceSet: SourceSet): SourceProvider {
        return SourceSetSourceProviderWrapper(sourceSet)
    }

    /**
     * The path to the Android SDK that Gradle uses for this project.
     *
     * To learn more about downloading and installing the Android SDK, read
     * [Update Your Tools with the SDK Manager](https://developer.android.com/studio/intro/update.html#sdk-manager)
     */
    val sdkDirectory: File
        get() {
            return dslServices.sdkComponents.flatMap { it.sdkDirectoryProvider }.get().asFile
        }

    /**
     * The path to the [Android NDK](https://developer.android.com/ndk/index.html) that Gradle uses for this project.
     *
     * You can install the Android NDK by either
     * [using the SDK manager](https://developer.android.com/studio/intro/update.html#sdk-manager)
     * or downloading
     * [the standalone NDK package](https://developer.android.com/ndk/downloads/index.html).
     */
    val ndkDirectory: File
        get() {
        // do not call this method from within the plugin code as it forces part of SDK initialization.
            return dslServices.sdkComponents.map {
                it.versionedNdkHandler(
                    ndkVersion,
                    ndkPath
                ).ndkPlatform.getOrThrow().ndkDirectory
            }.get()
    }

    // do not call this method from within the plugin code as it forces SDK initialization.
    // once this method is removed, remember to protect the bootClasspathConfig.bootClasspath against
    // unsafe read.
    override val bootClasspath: List<File>
        get() = try {
            bootClasspathConfig.bootClasspath.get().map { it.asFile }
        } catch (e: IllegalStateException) {
            listOf()
        }

    /**
     * The path to the
     * [Android Debug Bridge (ADB)](https://developer.android.com/studio/command-line/adb.html)
     * executable from the Android SDK.
     */
    @Suppress("DEPRECATION")
    val adbExecutable: File
        get() {
            return dslServices.versionedSdkLoaderService.versionedSdkLoader.flatMap {
                it.adbExecutableProvider }.get().asFile
        }

    /** This property is deprecated. Instead, use [adbExecutable]. */
    @Deprecated("This property is deprecated", ReplaceWith("adbExecutable"))
    val adbExe: File
        get() {
            return adbExecutable
        }

    open fun getDefaultProguardFile(name: String): File {
        if (!ProguardFiles.KNOWN_FILE_NAMES.contains(name)) {
            dslServices
                .issueReporter
                .reportError(
                    IssueReporter.Type.GENERIC, ProguardFiles.UNKNOWN_FILENAME_MESSAGE
                )
        }
        return ProguardFiles.getDefaultProguardFile(name, dslServices.buildDirectory)
    }

    // ---------------
    // TEMP for compatibility

    /** {@inheritDoc} */
    override var generatePureSplits: Boolean
        get() = false
        set(_) = logger.warn(
            "generatePureSplits is deprecated and has no effect anymore. Use bundletool to generate configuration splits."
        )

    @get:Suppress("WrongTerminology")
    @Deprecated("Use aidlPackagedList instead", ReplaceWith("aidlPackagedList"))
    override val aidlPackageWhiteList: MutableCollection<String>?
        get() = aidlPackagedList

    override val aidlPackagedList: MutableCollection<String>?
        get() = throw GradleException("aidlPackagedList is not supported.")

    // For compatibility with FeatureExtension.
    override val baseFeature: Boolean
        get() = isBaseModule

    @Incubating
    abstract fun composeOptions(action: Action<ComposeOptions>)

    abstract fun compileSdkVersion(version: String)

    abstract fun compileSdkVersion(apiLevel: Int)

    // Kept for binary and source compatibility until the old DSL interfaces can go away.
    abstract override val flavorDimensionList: MutableList<String>

    abstract override var resourcePrefix: String?

    abstract override var ndkVersion: String

    abstract var ndkPath: String?

    abstract override var buildToolsVersion: String

    abstract override val buildToolsRevision: Revision

    abstract override val libraryRequests: MutableCollection<LibraryRequest>

    abstract fun useLibrary(name: String)
    abstract fun useLibrary(name: String, required: Boolean)

    abstract override val aaptOptions: AaptOptions

    abstract override val adbOptions: AdbOptions

    abstract override val buildTypes: NamedDomainObjectContainer<out BuildType>
    abstract fun buildTypes(action: Action<in NamedDomainObjectContainer<BuildType>>)

    abstract override val compileOptions: CompileOptions

    abstract override var compileSdkVersion: String?

    abstract override val defaultConfig: DefaultConfig
    abstract fun defaultConfig(action: Action<DefaultConfig>)

    abstract override val externalNativeBuild: ExternalNativeBuild

    abstract override val jacoco: JacocoOptions

    abstract override val lintOptions: LintOptions

    abstract override val packagingOptions: PackagingOptions

    abstract override val productFlavors: NamedDomainObjectContainer<out ProductFlavor>
    abstract fun productFlavors(action: Action<NamedDomainObjectContainer<ProductFlavor>>)

    abstract override val signingConfigs: NamedDomainObjectContainer<SigningConfig>
    abstract fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfig>>)

    abstract override val sourceSets: NamedDomainObjectContainer<AndroidSourceSet>

    abstract override val splits: Splits

    abstract override val testOptions: TestOptions

    // these are indirectly implemented by extensions when they implement the new public
    // extension interfaces via delegates.
    abstract val buildFeatures: BuildFeatures

    @get:Restricted
    abstract var namespace: String?
}
