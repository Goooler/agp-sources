/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.publishing;

import static com.android.SdkConstants.FN_RESOURCE_STATIC_LIBRARY;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.REVERSE_METADATA_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.component.ApplicationCreationConfig;
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig;

import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;

/**
 * Helper for publishing android artifacts, both for internal (inter-project) and external
 * (to repositories).
 */
public class AndroidArtifacts {
    public static final Attribute<String> ARTIFACT_TYPE =
            Attribute.of("artifactType", String.class);
    public static final Attribute<String> MODULE_PATH = Attribute.of("modulePath", String.class);

    // types for main artifacts
    private static final String TYPE_AAR = "aar";
    private static final String TYPE_PROCESSED_AAR = "processed-aar";
    private static final String TYPE_APK = "apk";
    private static final String TYPE_JAR = ArtifactTypeDefinition.JAR_TYPE;
    private static final String TYPE_BUNDLE = "aab";
    // The apks produced from the android app bundle
    private static final String TYPE_APKS_FROM_BUNDLE = "bundle-apks";
    // zip of apks for publishing single or multi-apks to a repo.
    private static final String TYPE_APK_ZIP = "zip";

    // type for processed jars (the jars may need to be processed, e.g. jetified to AndroidX, before
    // they can be used)
    private static final String TYPE_PROCESSED_JAR = "processed-jar";

    private static final String TYPE_INSTRUMENTED_CLASSES = "jacoco-instrumented-classes";
    private static final String TYPE_JACOCO_CLASSES_JAR = "jacoco-classes-jar";
    private static final String TYPE_JACOCO_ASM_INSTRUMENTED_JARS = "jacoco-asm-instrumented-jars";

    private static final String TYPE_MAYBE_NOT_NAMESPACED_AAR = "non-namespaced-aar";
    private static final String TYPE_PREPROCESSED_AAR_FOR_AUTO_NAMESPACE =
            "preprocessed-aar-for-auto-namespace";
    private static final String TYPE_CLASSES = "android-classes";
    private static final String TYPE_PACKAGES_FOR_R8 = "android-packages-for-r8";

    // type for enumerated classes
    private static final String TYPE_ENUMERATED_RUNTIME_CLASSES = "enumerated-runtime-classes";

    // types published by an Android library
    private static final String TYPE_CLASSES_JAR = "android-classes-jar"; // In AAR
    private static final String TYPE_CLASSES_DIR = "android-classes-directory"; // Not in AAR
    private static final String TYPE_SHARED_CLASSES = "android-shared-classes";
    private static final String TYPE_CLASSES_FIXED_FRAMES_JAR = "android-classes-fixed-frames-jar";
    private static final String TYPE_DEX = "android-dex";
    private static final String TYPE_D8_OUTPUTS = "android-d8-outputs";
    private static final String TYPE_KEEP_RULES = "android-keep-rules";
    private static final String TYPE_GLOBAL_SYNTHETICS = "android-global-synthetics";
    private static final String TYPE_ASM_INSTRUMENTED_JARS = "android-asm-instrumented-jars";
    private static final String TYPE_JAVA_RES = "android-java-res";
    private static final String TYPE_SHARED_JAVA_RES = "android-shared-java-res";
    private static final String TYPE_MANIFEST = "android-manifest";
    private static final String TYPE_MANIFEST_METADATA = "android-manifest-metadata";
    private static final String TYPE_ANDROID_RES = "android-res";
    private static final String TYPE_ANDROID_RES_STATIC_LIBRARY = "android-res-static-library";
    private static final String TYPE_ANDROID_RES_SHARED_STATIC_LIBRARY =
            "android-res-shared-static-library";
    private static final String TYPE_ANDROID_RES_SHARED_OEM_LIBRARY =
            "android-res-shared-oem-library";
    private static final String TYPE_ANDROID_RES_BUNDLE = "android-res-for-bundle";
    private static final String TYPE_ASSETS = "android-assets";
    private static final String TYPE_SHARED_ASSETS = "android-shared-assets";
    private static final String TYPE_JNI = "android-jni";
    private static final String TYPE_SHARED_JNI = "android-shared-jni";
    private static final String TYPE_AIDL = "android-aidl";
    private static final String TYPE_RENDERSCRIPT = "android-renderscript";
    private static final String TYPE_LINT_JAR = "android-lint";
    private static final String TYPE_LINT_MODEL = "android-lint-variant-dependencies-model";
    private static final String TYPE_BASE_MODULE_LINT_MODEL =
            "android-base-module-lint-variant-dependencies-model";
    private static final String TYPE_LINT_VITAL_LINT_MODEL =
            "android-lint-vital-lint-variant-dependencies-model";
    private static final String TYPE_UNIT_TEST_LINT_MODEL = "android-unit-test-lint-model";
    private static final String TYPE_ANDROID_TEST_LINT_MODEL =
            "android-instrumentation-test-lint-model";
    private static final String TYPE_TEST_FIXTURES_LINT_MODEL = "android-test-fixtures-lint-model";
    private static final String TYPE_LINT_PARTIAL_RESULTS =
            "android-lint-variant-dependencies-partial-results";
    private static final String TYPE_LINT_VITAL_PARTIAL_RESULTS =
            "android-lint-vital-variant-dependencies-partial-results";
    private static final String TYPE_UNIT_TEST_LINT_PARTIAL_RESULTS =
            "android-unit-test-lint-partial-results";
    private static final String TYPE_ANDROID_TEST_LINT_PARTIAL_RESULTS =
            "android-instrumentation-test-lint-partial-results";
    private static final String TYPE_TEST_FIXTURES_LINT_PARTIAL_RESULTS =
            "android-test-fixtures-lint-partial-results";
    private static final String TYPE_LOCAL_AAR_FOR_LINT = "android-lint-local-aar";
    private static final String TYPE_LOCAL_EXPLODED_AAR_FOR_LINT = "android-lint-exploded-aar";
    private static final String TYPE_LINT_MODEL_METADATA = "android-lint-model-metadata";
    private static final String TYPE_EXT_ANNOTATIONS = "android-ext-annot";
    private static final String TYPE_PUBLIC_RES = "android-public-res";
    private static final String TYPE_SYMBOL = "android-symbol";
    private static final String TYPE_SYMBOL_WITH_PACKAGE_NAME = "android-symbol-with-package-name";

    private static final String TYPE_APP_SYMBOL_FOR_DATA_BINDING =
            "android-app-symbol-for-data-binding";
    private static final String TYPE_UNFILTERED_PROGUARD_RULES = "android-consumer-proguard-rules";
    private static final String TYPE_FILTERED_PROGUARD_RULES = "android-filtered-proguard-rules";
    private static final String TYPE_AAPT_PROGUARD_RULES = "android-aapt-proguard-rules";
    private static final String TYPE_DATA_BINDING_ARTIFACT = "android-databinding";
    private static final String TYPE_DATA_BINDING_BASE_CLASS_LOG_ARTIFACT =
            "android-databinding-class-log";
    private static final String TYPE_EXPLODED_AAR = "android-exploded-aar";
    private static final String TYPE_AAR_OR_JAR = "android-aar-or-jar";
    private static final String TYPE_EXPLODED_AAR_OR_ASAR_INTERFACE_DESCRIPTOR =
            "android-exploded-aar-or-asar-interface-descriptor";
    private static final String TYPE_AAR_ClASS_LIST_AND_RES_SYMBOLS =
            "aar-class-list-and-res-symbols";
    private static final String TYPE_JAR_ClASS_LIST = "jar-class-list";
    private static final String TYPE_COMPILED_DEPENDENCIES_RESOURCES =
            "android-compiled-dependencies-resources";
    private static final String TYPE_MODULE_BUNDLE = "android-module-bundle";
    private static final String TYPE_LIB_DEPENDENCIES = "android-lib-dependencies";
    private static final String TYPE_AAR_METADATA = "android-aar-metadata";

    // types for additional artifacts to go with APK
    private static final String TYPE_MAPPING = "android-mapping";
    private static final String TYPE_METADATA = "android-metadata";

    // types for APK to APK support (base/feature/tests)
    private static final String TYPE_PACKAGED_DEPENDENCIES = "android-packaged-dependencies";

    // types for feature-split content.
    private static final String TYPE_FEATURE_SET_METADATA = "android-feature-all-metadata";
    private static final String TYPE_BASE_MODULE_METADATA = "android-base-module-metadata";
    private static final String TYPE_FEATURE_RESOURCE_PKG = "android-feature-res-ap_";
    private static final String TYPE_FEATURE_DEX = "android-feature-dex";
    private static final String TYPE_FEATURE_SHRUNK_JAVA_RES = "android-feature-shrunk-java-res";
    private static final String TYPE_FEATURE_SHRUNK_RESOURCES_PROTO_FORMAT =
            "android-feature-shrunk-resources-proto-format";
    private static final String TYPE_FEATURE_SIGNING_CONFIG_DATA =
            "android-feature-signing-config-data";
    private static final String TYPE_FEATURE_SIGNING_CONFIG_VERSIONS =
            "android-feature-signing-config-versions";
    private static final String TYPE_FEATURE_NAME = "android-feature-name";

    // types for reverse metadata content.
    private static final String TYPE_REVERSE_METADATA_FEATURE_DECLARATION =
            "android-reverse-metadata-feature-decl";
    private static final String TYPE_REVERSE_METADATA_FEATURE_MANIFEST =
            "android-reverse-metadata-feature-manifest";
    private static final String TYPE_REVERSE_METADATA_CLASSES = "android-reverse-metadata-classes";
    private static final String TYPE_REVERSE_METADATA_JAVA_RES =
            "android-reverse-metadata-java-res";
    private static final String TYPE_REVERSE_METADATA_LINKED_RESOURCES_PROTO_FORMAT =
            "android-reverse-metadata-linked-resources-proto-format";
    private static final String TYPE_REVERSE_METADATA_NATIVE_DEBUG_METADATA =
            "android-reverse-metadata-native-debug-metadata";
    private static final String TYPE_REVERSE_METADATA_NATIVE_SYMBOL_TABLES =
            "android-reverse-metadata-native-symbol-tables";

    public static final String TYPE_MOCKABLE_JAR = "android-mockable-jar";
    public static final Attribute<Boolean> MOCKABLE_JAR_RETURN_DEFAULT_VALUES =
            Attribute.of("returnDefaultValues", Boolean.class);

    // jetpack compose related types
    public static final String TYPE_ART_PROFILE = "android-art-profile";

    // attr info extracted from the platform android.jar
    public static final String TYPE_PLATFORM_ATTR = "android-platform-attr";

    private static final String TYPE_NAVIGATION_JSON = "android-navigation-json";

    private static final String TYPE_PREFAB_PACKAGE = "android-prefab";
    private static final String TYPE_PREFAB_PACKAGE_CONFIGURATION = "android-prefab-configuration";

    private static final String TYPE_DESUGAR_LIB_MERGED_KEEP_RULES =
            "android-desugar-lib-merged-keep-rules";

    private static final String TYPE_ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE = "asar";
    private static final String TYPE_ANDROID_PRIVACY_SANDBOX_SDK_COMPAT_SPLIT_APKS =
            "android-privacy-sandbox-sdk-compat-apks";
    private static final String TYPE_ANDROID_PRIVACY_SANDBOX_EXTRACTED_SDK_APKS =
            "android-privacy-sandbox-extracted-sdk-apks";
    private static final String TYPE_USES_SDK_LIBRARY_SPLIT_FOR_LOCAL_DEPLOYMENT =
            "uses-sdk-library-split-for-local-deployment";

    private static final String TYPE_ANDROID_PRIVACY_SANDBOX_SDK_EXTRACTED_METADATA_PROTO =
            "android-privacy-sandbox-sdk-extracted-metadata";
    private static final String TYPE_ANDROID_PRIVACY_SANDBOX_SDK_INTERFACE_DESCRIPTOR =
            "android-privacy-sandbox-sdk-interface-descriptor";
    private static final String TYPE_ANDROID_PRIVACY_SANDBOX_USES_SDK_LIBRARY_MANIFEST_SNIPPET =
            "android-privacy-sandbox-sdk-uses-sdk-library-manifest-snippet";

    private static final String TYPE_FEATURE_PUBLISHED_DEX = "android-feature-published-dex";

    private static final String TYPE_FEATURE_PUBLISHED_GLOBAL_SYNTHETICS =
            "android-feature-published-global-synthetics";

    private static final String TYPE_SOURCES_JAR = "android-sources-jar";
    private static final String TYPE_JAVA_DOC_JAR = "android-java-doc-jar";

    private static final String TYPE_SUPPORTED_LOCALE_LIST = "supported-locale-list";
    private static final String TYPE_R_CLASS_JAR = "r-class-jar";
    private static final String TYPE_MERGED_TEST_ONLY_NATIVE_LIBS = "merged-test-only-native-libs";

    public static final String PATH_SHARED_LIBRARY_RESOURCES_APK =
            "shared/" + FN_RESOURCE_STATIC_LIBRARY;

    public enum ConsumedConfigType {
        COMPILE_CLASSPATH("compileClasspath", API_ELEMENTS, true),
        RUNTIME_CLASSPATH("runtimeClasspath", RUNTIME_ELEMENTS, true),
        /**
         * A 'true' runtime classpath for consuming the PACKAGED_DEPENDENCIES artifact in separate
         * test projects and dynamic feature projects.
         *
         * <p>In the separate test project the tested project is added as compileOnly, so this is
         * the only way to access this runtime classpath.
         */
        PROVIDED_CLASSPATH("packagedDependenciesClasspath", RUNTIME_ELEMENTS, false),
        ANNOTATION_PROCESSOR("annotationProcessorClasspath", RUNTIME_ELEMENTS, false),
        REVERSE_METADATA_VALUES("reverseMetadata", REVERSE_METADATA_ELEMENTS, false);

        @NonNull private final String name;
        @NonNull private final PublishedConfigType publishedTo;
        private final boolean needsTestedComponents;

        ConsumedConfigType(
                @NonNull String name,
                @NonNull PublishedConfigType publishedTo,
                boolean needsTestedComponents) {
            this.name = name;
            this.publishedTo = publishedTo;
            this.needsTestedComponents = needsTestedComponents;
        }

        @NonNull
        public String getName() {
            return name;
        }

        @NonNull
        public PublishedConfigType getPublishedTo() {
            return publishedTo;
        }

        public boolean needsTestedComponents() {
            return needsTestedComponents;
        }
    }

    public enum PublishedConfigType {
        API_ELEMENTS, // inter-project publishing (API)
        RUNTIME_ELEMENTS, // inter-project publishing (RUNTIME)
        REVERSE_METADATA_ELEMENTS, // inter-project publishing (REVERSE META-DATA)

        // Maven/SoftwareComponent AAR publishing (API)
        API_PUBLICATION(true),
        // Maven/SoftwareComponent AAR publishing (RUNTIME)
        RUNTIME_PUBLICATION(true),

        APK_PUBLICATION(true), // Maven/SoftwareComponent APK publishing
        AAB_PUBLICATION(true), // Maven/SoftwareComponent AAB publishing

        SOURCE_PUBLICATION(true),
        JAVA_DOC_PUBLICATION(true);

        private boolean isPublicationConfig;

        PublishedConfigType(boolean isPublicationConfig) {
            this.isPublicationConfig = isPublicationConfig;
        }

        PublishedConfigType() {
            this(false);
        }

        public boolean isPublicationConfig() {
            return isPublicationConfig;
        }
    }

    /** The provenance of artifacts to include. */
    public enum ArtifactScope {
        /** Include all artifacts */
        ALL,
        /** Include all 'external' artifacts, i.e. everything but PROJECT, i.e. FILE + MODULE */
        EXTERNAL,
        /** Include all artifacts built by subprojects */
        PROJECT,
        /** Include all file dependencies */
        FILE,
        /** Include all module (i.e. from a repository) dependencies */
        REPOSITORY_MODULE,
    }

    /** Artifact published by modules for consumption by other modules. */
    public enum ArtifactType {

        /**
         * A jar or directory containing classes.
         *
         * <p>If it is a directory, it must contain class files only and not jars.
         */
        CLASSES(TYPE_CLASSES),

        /** A jar containing classes. */
        CLASSES_JAR(TYPE_CLASSES_JAR),

        /** A jar containing source files. */
        SOURCES_JAR(TYPE_SOURCES_JAR),

        /** A jar containing java doc files. */
        JAVA_DOC_JAR(TYPE_JAVA_DOC_JAR),

        /**
         * A directory containing classes.
         *
         * <p>IMPORTANT: The directory may contain either class files only (preferred) or a single
         * jar only, see {@link ClassesDirFormat}. Because of this, DO NOT CONSUME this artifact
         * type directly, use {@link #CLASSES} or {@link #CLASSES_JAR} instead. (We have {@link
         * com.android.build.gradle.internal.dependency.ClassesDirToClassesTransform} from {@link
         * #CLASSES_DIR} to {@link #CLASSES} to normalize the format.)
         */
        CLASSES_DIR(TYPE_CLASSES_DIR),
        SHARED_CLASSES(TYPE_SHARED_CLASSES),
        /** A jar containing classes with recalculated stack frames */
        CLASSES_FIXED_FRAMES_JAR(TYPE_CLASSES_FIXED_FRAMES_JAR),

        /**
         * Original (unprocessed) jar.
         *
         * <p>JAR vs. {@link #PROCESSED_JAR}: Jars usually need to be processed (e.g., jetified,
         * namespaced) before they can be used.
         *
         * <p>In a few cases, consumers may want to use unprocessed jars (be sure to document the
         * reason in those cases). Common reasons are:
         *
         * <ul>
         *   <li>Correctness: Some tasks want to work with unprocessed jars.
         *   <li>Performance: Some jars don't need to be processed (e.g., android.jar, lint.jar).
         * </ul>
         *
         * Only use when specifically the unprocessed artifact is needed, otherwise use {@link
         * GlobalTaskCreationConfig#getAarOrJarTypeToConsume() }
         */
        JAR(TYPE_JAR),

        /** Jacoco instrumented versions of CLASSES and CLASSES_JAR produced by JacocoTransform. */
        JACOCO_CLASSES(TYPE_INSTRUMENTED_CLASSES),
        JACOCO_CLASSES_JAR(TYPE_JACOCO_CLASSES_JAR),
        JACOCO_ASM_INSTRUMENTED_JARS(TYPE_JACOCO_ASM_INSTRUMENTED_JARS),

        /**
         * A processed jar.
         *
         * <p>See {@link #JAR} for context on processed/unprocessed artifacts.
         *
         * <p>Don't use directly, use {@link GlobalTaskCreationConfig#getAarOrJarTypeToConsume() }
         * instead.
         */
        PROCESSED_JAR(TYPE_PROCESSED_JAR),

        // published dex folder for bundle
        DEX(TYPE_DEX),
        // d8 outputs including dex and global synthetics
        D8_OUTPUTS(TYPE_D8_OUTPUTS),
        // global synthetics generated by d8 which need to be merged into dex for de-duplication
        GLOBAL_SYNTHETICS(TYPE_GLOBAL_SYNTHETICS),

        // Dependencies jars that are instrumented by the registered asm class visitors
        ASM_INSTRUMENTED_JARS(TYPE_ASM_INSTRUMENTED_JARS),

        // A list of enumerated runtime classes by module,
        // used to reduce IO in checking for duplicates
        ENUMERATED_RUNTIME_CLASSES(TYPE_ENUMERATED_RUNTIME_CLASSES),

        // manifest is published to both to compare and detect provided-only library dependencies.
        MANIFEST(TYPE_MANIFEST),

        MANIFEST_METADATA(TYPE_MANIFEST_METADATA),

        // Resources static library are API (where only explicit dependencies are included) and
        // runtime
        RES_STATIC_LIBRARY(TYPE_ANDROID_RES_STATIC_LIBRARY),
        RES_SHARED_STATIC_LIBRARY(TYPE_ANDROID_RES_SHARED_STATIC_LIBRARY),
        RES_BUNDLE(TYPE_ANDROID_RES_BUNDLE),
        RES_SHARED_OEM_TOKEN_LIBRARY(TYPE_ANDROID_RES_SHARED_OEM_LIBRARY),

        // API only elements.
        AIDL(TYPE_AIDL),
        RENDERSCRIPT(TYPE_RENDERSCRIPT),
        DATA_BINDING_ARTIFACT(TYPE_DATA_BINDING_ARTIFACT),
        DATA_BINDING_BASE_CLASS_LOG_ARTIFACT(TYPE_DATA_BINDING_BASE_CLASS_LOG_ARTIFACT),
        // The AAR metadata file, specifying consumer constraints
        AAR_METADATA(TYPE_AAR_METADATA),

        // runtime and/or bundle elements
        JAVA_RES(TYPE_JAVA_RES),
        SHARED_JAVA_RES(TYPE_SHARED_JAVA_RES),
        ANDROID_RES(TYPE_ANDROID_RES),
        ASSETS(TYPE_ASSETS),
        SHARED_ASSETS(TYPE_SHARED_ASSETS),
        COMPILE_SYMBOL_LIST(TYPE_SYMBOL),
        COMPILED_DEPENDENCIES_RESOURCES(TYPE_COMPILED_DEPENDENCIES_RESOURCES),
        /**
         * The symbol list with the package name as the first line. As the r.txt format in the AAR
         * cannot be changed, this is created by prepending the package name from the
         * AndroidManifest.xml to the existing r.txt file.
         */
        SYMBOL_LIST_WITH_PACKAGE_NAME(TYPE_SYMBOL_WITH_PACKAGE_NAME),

        /**
         * Same as [SYMBOL_LIST_WITH_PACKAGE_NAME] but for apps, this is published only for data
         * binding classes generation tasks in test projects and dynamic features.
         */
        APP_SYMBOL_LIST_FOR_DATA_BINDING(TYPE_APP_SYMBOL_FOR_DATA_BINDING),

        /** Intermediate format of the preprocessed AAR for auto-namespacing */
        MAYBE_NON_NAMESPACED_PROCESSED_AAR(TYPE_MAYBE_NOT_NAMESPACED_AAR),
        PREPROCESSED_AAR_FOR_AUTO_NAMESPACE(TYPE_PREPROCESSED_AAR_FOR_AUTO_NAMESPACE),
        JNI(TYPE_JNI),
        SHARED_JNI(TYPE_SHARED_JNI),

        /**
         * A directory containing a Prefab package.json file and associated modules.
         *
         * <p>Processed by Prefab to generate inputs for ExternalNativeBuild modules to consume
         * dependencies from AARs. https://google.github.io/prefab/
         */
        PREFAB_PACKAGE(TYPE_PREFAB_PACKAGE),
        PREFAB_PACKAGE_CONFIGURATION(TYPE_PREFAB_PACKAGE_CONFIGURATION),
        ANNOTATIONS(TYPE_EXT_ANNOTATIONS),
        PUBLIC_RES(TYPE_PUBLIC_RES),
        UNFILTERED_PROGUARD_RULES(TYPE_UNFILTERED_PROGUARD_RULES),
        FILTERED_PROGUARD_RULES(TYPE_FILTERED_PROGUARD_RULES),
        AAPT_PROGUARD_RULES(TYPE_AAPT_PROGUARD_RULES),

        LINT(TYPE_LINT_JAR),
        LINT_MODEL(AndroidArtifacts.TYPE_LINT_MODEL, ArtifactCategory.VERIFICATION),
        // The lint model published by the base module for consumption by dynamic features.
        BASE_MODULE_LINT_MODEL(
                AndroidArtifacts.TYPE_BASE_MODULE_LINT_MODEL, ArtifactCategory.VERIFICATION),
        // The lint model with partial results set to the location of LINT_VITAL_PARTIAL_RESULTS.
        LINT_VITAL_LINT_MODEL(
                AndroidArtifacts.TYPE_LINT_VITAL_LINT_MODEL, ArtifactCategory.VERIFICATION),
        // The unit test lint model
        UNIT_TEST_LINT_MODEL(TYPE_UNIT_TEST_LINT_MODEL, ArtifactCategory.VERIFICATION),
        // The android test lint model
        ANDROID_TEST_LINT_MODEL(TYPE_ANDROID_TEST_LINT_MODEL, ArtifactCategory.VERIFICATION),
        // The test fixtures lint model
        TEST_FIXTURES_LINT_MODEL(TYPE_TEST_FIXTURES_LINT_MODEL, ArtifactCategory.VERIFICATION),
        // The partial results produced by running lint with --analyze-only
        LINT_PARTIAL_RESULTS(
                AndroidArtifacts.TYPE_LINT_PARTIAL_RESULTS, ArtifactCategory.VERIFICATION),
        // The partial results produced by running lint with --analyze-only and --fatalOnly
        LINT_VITAL_PARTIAL_RESULTS(TYPE_LINT_VITAL_PARTIAL_RESULTS, ArtifactCategory.VERIFICATION),
        // The partial results produced by running lint with --analyze-only on the unit test
        // component
        UNIT_TEST_LINT_PARTIAL_RESULTS(
                TYPE_UNIT_TEST_LINT_PARTIAL_RESULTS, ArtifactCategory.VERIFICATION),
        // The partial results produced by running lint with --analyze-only on the android test
        // component
        ANDROID_TEST_LINT_PARTIAL_RESULTS(
                TYPE_ANDROID_TEST_LINT_PARTIAL_RESULTS, ArtifactCategory.VERIFICATION),
        // The partial results produced by running lint with --analyze-only on the test fixtures
        // component
        TEST_FIXTURES_LINT_PARTIAL_RESULTS(
                TYPE_TEST_FIXTURES_LINT_PARTIAL_RESULTS, ArtifactCategory.VERIFICATION),
        // An AAR built from a library project for lint to consume.
        LOCAL_AAR_FOR_LINT(TYPE_LOCAL_AAR_FOR_LINT),
        // Exploded AARs from library projects for lint to consume when not run with check
        // dependencies.
        LOCAL_EXPLODED_AAR_FOR_LINT(TYPE_LOCAL_EXPLODED_AAR_FOR_LINT),
        // The lint model metadata file, containing maven groupId e.g.
        LINT_MODEL_METADATA(
                AndroidArtifacts.TYPE_LINT_MODEL_METADATA, ArtifactCategory.VERIFICATION),

        APK_MAPPING(TYPE_MAPPING),
        APK_METADATA(TYPE_METADATA),
        APK(TYPE_APK),
        // zip of apks for publishing single or multi-apks to a repo.
        APK_ZIP(TYPE_APK_ZIP),

        // intermediate bundle that only contains one module. This is to be input into bundle-tool
        MODULE_BUNDLE(TYPE_MODULE_BUNDLE),
        // final bundle generate by bundle-tool
        BUNDLE(TYPE_BUNDLE),
        // apks produced from the bundle, for consumption by tests.
        APKS_FROM_BUNDLE(TYPE_APKS_FROM_BUNDLE),
        // intermediate library dependencies on a per module basis for eventual packaging in the
        // bundle.
        LIB_DEPENDENCIES(TYPE_LIB_DEPENDENCIES),

        // Dynamic Feature related artifacts.

        // file containing the metadata for the full feature set. This contains the feature names,
        // the res ID offset, both tied to the feature module path. Published by the base for the
        // other features to consume and find their own metadata.
        FEATURE_SET_METADATA(TYPE_FEATURE_SET_METADATA),
        // file containing the signing config data to be used by any features. Published by the base
        // for the features to consume.
        FEATURE_SIGNING_CONFIG_DATA(TYPE_FEATURE_SIGNING_CONFIG_DATA),
        // file containing the signing config versions to be used by any features. Published by the
        // base for the features to consume.
        FEATURE_SIGNING_CONFIG_VERSIONS(TYPE_FEATURE_SIGNING_CONFIG_VERSIONS),

        // file containing the base module info (appId, versionCode, debuggable, ...).
        // This is published by the base module and read by the dynamic feature modules
        BASE_MODULE_METADATA(TYPE_BASE_MODULE_METADATA),

        // ?
        FEATURE_RESOURCE_PKG(TYPE_FEATURE_RESOURCE_PKG),

        // File containing the list of dependencies packaged in a given APK. This is consumed
        // by other APKs to avoid repackaging the same thing.
        PACKAGED_DEPENDENCIES(TYPE_PACKAGED_DEPENDENCIES),

        // The feature dex files output by R8 or DexSplitter from the base. The base produces and
        // publishes these files when the base has dynamic features and code shrinking occurs.
        FEATURE_DEX(TYPE_FEATURE_DEX),

        // The feature java resources output by R8 from the base. The base produces and publishes
        // these files when the base has dynamic features and R8 code shrinking occurs.
        FEATURE_SHRUNK_JAVA_RES(TYPE_FEATURE_SHRUNK_JAVA_RES),

        /**
         * Shrunk resources published from the base module to be consumed by dynamic feature modules
         * (see {@link ApplicationCreationConfig#getShrinkingWithDynamicFeatures}).
         */
        FEATURE_SHRUNK_RESOURCES_PROTO_FORMAT(TYPE_FEATURE_SHRUNK_RESOURCES_PROTO_FORMAT),

        // The name of an instant or dynamic feature module
        // This is published by {@link FeatureNameWriterTask} to be consumed by dependencies
        // of the feature that need to know the name of its feature split
        FEATURE_NAME(TYPE_FEATURE_NAME),

        // The feature dex files published from feature modules to the base for computing main
        // dex list for bundle.
        FEATURE_PUBLISHED_DEX(TYPE_FEATURE_PUBLISHED_DEX),

        // Reverse Metadata artifacts
        REVERSE_METADATA_FEATURE_DECLARATION(TYPE_REVERSE_METADATA_FEATURE_DECLARATION),
        REVERSE_METADATA_FEATURE_MANIFEST(TYPE_REVERSE_METADATA_FEATURE_MANIFEST),
        REVERSE_METADATA_CLASSES(TYPE_REVERSE_METADATA_CLASSES),
        REVERSE_METADATA_JAVA_RES(TYPE_REVERSE_METADATA_JAVA_RES),

        /**
         * Linked resources published from dynamic feature modules to be consumed by the base module
         * (see {@link ApplicationCreationConfig#getShrinkingWithDynamicFeatures}).
         */
        REVERSE_METADATA_LINKED_RESOURCES_PROTO_FORMAT(TYPE_REVERSE_METADATA_LINKED_RESOURCES_PROTO_FORMAT),

        // The .so.dbg files containing the debug metadata from the corresponding .so files
        REVERSE_METADATA_NATIVE_DEBUG_METADATA(TYPE_REVERSE_METADATA_NATIVE_DEBUG_METADATA),
        // The .so.sym files containing the symbol tables from the corresponding .so files
        REVERSE_METADATA_NATIVE_SYMBOL_TABLES(TYPE_REVERSE_METADATA_NATIVE_SYMBOL_TABLES),

        // art profile in human readable format
        ART_PROFILE(TYPE_ART_PROFILE),

        // types for querying only. Not publishable.

        /**
         * Original (unprocessed) aar.
         *
         * <p>See {@link #JAR} for context on processed/unprocessed artifacts.
         *
         * <p>Only use when specifically the unprocessed artifact is needed, otherwise use {@link
         * GlobalTaskCreationConfig#getAarOrJarTypeToConsume() }
         */
        AAR(TYPE_AAR),

        /**
         * A processed aar.
         *
         * <p>See {@link #JAR} for context on processed/unprocessed artifacts.
         *
         * <p>Don't use directly, use {@link GlobalTaskCreationConfig#getAarOrJarTypeToConsume() }
         * instead.
         */
        PROCESSED_AAR(TYPE_PROCESSED_AAR),

        /** Directory containing the extracted contents of a possibly processed AAR */
        EXPLODED_AAR(TYPE_EXPLODED_AAR),

        /**
         * Original (unprocessed) aar or jar (i.e., it is either an {@link #AAR} or a {@Link #JAR}).
         *
         * <p>See {@link #JAR} for context on processed/unprocessed artifacts.
         */
        AAR_OR_JAR(TYPE_AAR_OR_JAR), // See ArtifactUtils for how this is used.
        /** See ArtifactUtils for how this is used. */
        EXPLODED_AAR_OR_ASAR_INTERFACE_DESCRIPTOR(TYPE_EXPLODED_AAR_OR_ASAR_INTERFACE_DESCRIPTOR),

        // A file containing unique resource symbols from ANDROID_RES.
        ANDROID_RES_SYMBOLS(TYPE_AAR_ClASS_LIST_AND_RES_SYMBOLS),
        // A file containing classpaths from CLASSES_JAR.
        JAR_CLASS_LIST(TYPE_JAR_ClASS_LIST),

        NAVIGATION_JSON(TYPE_NAVIGATION_JSON),

        // merged desugar lib keep rules from dynamic feature modules
        DESUGAR_LIB_MERGED_KEEP_RULES(TYPE_DESUGAR_LIB_MERGED_KEEP_RULES),

        GLOBAL_SYNTHETICS_MERGED(TYPE_FEATURE_PUBLISHED_GLOBAL_SYNTHETICS),

        // The 'ASAR' file for consuming privacy sandbox SDKs
        ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE(TYPE_ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE),

        // The APKs extracted from artifact-transform derived APKs from a privacy sandbox SDK
        ANDROID_PRIVACY_SANDBOX_EXTRACTED_SDK_APKS(TYPE_ANDROID_PRIVACY_SANDBOX_EXTRACTED_SDK_APKS),

        // The APKS containing split APK files for backward compatibility.
        ANDROID_PRIVACY_SANDBOX_SDK_COMPAT_SPLIT_APKS(
                TYPE_ANDROID_PRIVACY_SANDBOX_SDK_COMPAT_SPLIT_APKS),

        // use sdk library split Apk that allows us to have a single main APK that works for all
        // versions of Android.
        USES_SDK_LIBRARY_SPLIT_FOR_LOCAL_DEPLOYMENT(
                TYPE_USES_SDK_LIBRARY_SPLIT_FOR_LOCAL_DEPLOYMENT),

        // The metadata proto from the privacy sandbox SDK, used by the package numbering task
        ANDROID_PRIVACY_SANDBOX_SDK_METADATA_PROTO(
                TYPE_ANDROID_PRIVACY_SANDBOX_SDK_EXTRACTED_METADATA_PROTO),

        // The sdk-apis.jar containing client API stubs packaged in the ASB.
        ANDROID_PRIVACY_SANDBOX_SDK_INTERFACE_DESCRIPTOR(
                TYPE_ANDROID_PRIVACY_SANDBOX_SDK_INTERFACE_DESCRIPTOR),

        // The file describing the supported locales in the module
        SUPPORTED_LOCALE_LIST(TYPE_SUPPORTED_LOCALE_LIST),

        // The compilation only R class jar which is already part of the compile jar
        R_CLASS_JAR(TYPE_R_CLASS_JAR),

        // native libs to be packaged with test components instead of main components.
        MERGED_TEST_ONLY_NATIVE_LIBS(TYPE_MERGED_TEST_ONLY_NATIVE_LIBS),

        PACKAGES_FOR_R8(TYPE_PACKAGES_FOR_R8),
        ;

        @NonNull private final String type;
        @Nullable private final ArtifactCategory category;

        ArtifactType(@NonNull String type) {
            this(type, null);
        }

        ArtifactType(@NonNull String type, @Nullable ArtifactCategory category) {
            this.type = type;
            this.category = category;
        }

        @NonNull
        public String getType() {
            return type;
        }

        @Nullable
        public ArtifactCategory getCategory() {
            return category;
        }
    }

    /**
     * The format of the directory with artifact type {@link
     * AndroidArtifacts.ArtifactType#CLASSES_DIR}.
     *
     * <p>See {@link #CONTAINS_SINGLE_JAR} for why we need this format.
     */
    public enum ClassesDirFormat {

        /** The directory contains class files only. */
        CONTAINS_CLASS_FILES_ONLY,

        /**
         * The directory contains a single jar only.
         *
         * <p>The need for this format arises when we want to publish all classes to a directory,
         * but the input classes contain jars (e.g., R.jar or those provided by external
         * users/plugins via the AGP API). There are a few approaches, and only the last one works:
         *
         * <p>1. Unzip the jars into the directory: This operation may fail due to OS differences
         * (e.g., case sensitivity, char encoding). It is also inefficient to zip and unzip classes
         * multiple times.
         *
         * <p>2. Put the jars inside the directory: The directory is usually put on a classpath, and
         * the jars inside the directory would not be recognized as part of the classpath. Here are
         * 2 attempts to fix it: 2a) At the consumer's side, modify the classpath to include the
         * jars inside the directory. This is possible at the task/transform's execution but not
         * possible at task graph creation. Therefore, Gradle would not apply input normalization
         * correctly to the jars inside the directory. 2b) Add a transform to convert the directory
         * into a jar. This transform takes all the class files inside the jars and merge them into
         * a jar. Then, we can let consumers consume the jar instead of the directory. This is
         * possible but not as efficient as #3 below.
         *
         * <p>3. Merge all the class files inside the jars into a single jar inside the directory,
         * then add a transform to convert the directory into a jar where the transform simply
         * selects the jar inside the directory as its output jar (see {@link
         * com.android.build.gradle.internal.dependency.ClassesDirToClassesTransform}). This is
         * better than 2b because 2b includes copying files when publishing and zipping files when
         * transforming, whereas this includes zipping files when publishing and nearly a no-op when
         * transforming.
         */
        CONTAINS_SINGLE_JAR
    }
}
