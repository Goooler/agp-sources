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
package com.android.ide.common.repository

import com.android.support.AndroidxNameUtils

/**
 * Enumeration of known artifacts used in Android Studio
 */
enum class GoogleMavenArtifactId(val mavenGroupId: String, val mavenArtifactId: String): WellKnownMavenArtifactId {
  // Support libraries (mostly in support/androidx pairs)
  SUPPORT_ANIMATED_VECTOR_DRAWABLE("com.android.support", "animated-vector-drawable"),
  ANDROIDX_VECTORDRAWABLE_ANIMATED("androidx.vectordrawable", "vectordrawable-animated"),
  SUPPORT_APPCOMPAT_V7("com.android.support", "appcompat-v7"),
  ANDROIDX_APPCOMPAT("androidx.appcompat", "appcompat"),
  SUPPORT_CAR("com.android.support", "car"),
  ANDROIDX_CAR("androidx.car", "car"),
  SUPPORT_CARDVIEW_V7("com.android.support", "cardview-v7"),
  ANDROIDX_CARDVIEW("androidx.cardview", "cardview"),
  SUPPORT_COLLECTIONS("com.android.support", "collections"),
  ANDROIDX_COLLECTION("androidx.collection", "collection"),
  SUPPORT_CUSTOMTABS("com.android.support", "customtabs"),
  ANDROIDX_BROWSER("androidx.browser", "browser"),
  SUPPORT_DESIGN("com.android.support", "design"),
  MATERIAL("com.google.android.material", "material"),
  SUPPORT_EXIFINTERFACE("com.android.support", "exifinterface"),
  ANDROIDX_EXIFINTERFACE("androidx.exifinterface", "exifinterface"),
  SUPPORT_GRIDLAYOUT_V7("com.android.support", "gridlayout-v7"),
  ANDROIDX_GRIDLAYOUT("androidx.gridlayout", "gridlayout"),
  SUPPORT_HEIFWRITER("com.android.support", "heifwriter"),
  ANDROIDX_HEIFWRITER("androidx.heifwriter", "heifwriter"),
  SUPPORT_LEANBACK_V17("com.android.support", "leanback-v17"),
  ANDROIDX_LEANBACK("androidx.leanback", "leanback"),
  SUPPORT_MEDIAROUTER_V7("com.android.support", "mediarouter-v7"),
  ANDROIDX_MEDIAROUTER("androidx.mediarouter", "mediarouter"),
  SUPPORT_MULTIDEX("com.android.support", "multidex"),
  ANDROIDX_MULTIDEX("androidx.multidex", "multidex"),
  SUPPORT_MULTIDEX_INSTRUMENTATION("com.android.support", "multidex-instrumentation"),
  ANDROIDX_MULTIDEX_INSTRUMENTATION("androidx.multidex", "multidex-instrumentation"),
  SUPPORT_PALETTE_V7("com.android.support", "palette-v7"),
  ANDROIDX_PALETTE("androidx.palette", "palette"),
  SUPPORT_PERCENT("com.android.support", "percent"),
  ANDROIDX_PERCENTLAYOUT("androidx.percentlayout", "percentlayout"),
  SUPPORT_PREFERENCE_LEANBACK_V17("com.android.support", "preference-leanback-v17"),
  ANDROIDX_LEANBACK_PREFERENCE("androidx.leanback", "leanback-preference"),
  SUPPORT_PREFERENCE_V14("com.android.support", "preference-v14"),
  ANDROIDX_LEGACY_PREFERENCE_V14("androidx.legacy", "legacy-preference-v14"),
  SUPPORT_PREFERENCE_V7("com.android.support", "preference-v7"),
  ANDROIDX_PREFERENCE("androidx.preference", "preference"),
  SUPPORT_RECOMMENDATION("com.android.support", "recommendation"),
  ANDROIDX_RECOMMENDATION("androidx.recommendation", "recommendation"),
  SUPPORT_RECYCLERVIEW_SELECTION("com.android.support", "recyclerview-selection"),
  ANDROIDX_RECYCLERVIEW_SELECTION("androidx.recyclerview", "recyclerview-selection"),
  SUPPORT_RECYCLERVIEW_V7("com.android.support", "recyclerview-v7"),
  ANDROIDX_RECYCLERVIEW("androidx.recyclerview", "recyclerview"),
  SUPPORT_ANNOTATIONS("com.android.support", "support-annotations"),
  ANDROIDX_ANNOTATION("androidx.annotation", "annotation"),
  SUPPORT_COMPAT("com.android.support", "support-compat"),
  ANDROIDX_CORE("androidx.core", "core"),
  SUPPORT_CONTENT("com.android.support", "support-content"),
  ANDROIDX_CONTENTPAGER("androidx.contentpager", "contentpager"),
  SUPPORT_CORE_UI("com.android.support", "support-core-ui"),
  ANDROIDX_LEGACY_SUPPORT_CORE_UI("androidx.legacy", "legacy-support-core-ui"),
  SUPPORT_CORE_UTILS("com.android.support", "support-core-utils"),
  ANDROIDX_LEGACY_SUPPORT_CORE_UTILS("androidx.legacy", "legacy-support-core-utils"),
  SUPPORT_DYNAMIC_ANIMATION("com.android.support", "support-dynamic-animation"),
  ANDROIDX_DYNAMICANIMATION("androidx.dynamicanimation", "dynamicanimation"),
  SUPPORT_EMOJI("com.android.support", "support-emoji"),
  ANDROIDX_EMOJI("androidx.emoji", "emoji"),
  SUPPORT_EMOJI_APPCOMPAT("com.android.support", "support-emoji-appcompat"),
  ANDROIDX_EMOJI_APPCOMPAT("androidx.emoji", "emoji-appcompat"),
  SUPPORT_EMOJI_BUNDLED("com.android.support", "support-emoji-bundled"),
  ANDROIDX_EMOJI_BUNDLED("androidx.emoji", "emoji-bundled"),
  SUPPORT_FRAGMENT("com.android.support", "support-fragment"),
  ANDROIDX_FRAGMENT("androidx.fragment", "fragment"),
  SUPPORT_MEDIA_COMPAT("com.android.support", "support-media-compat"),
  ANDROIDX_MEDIA("androidx.media", "media"),
  SUPPORT_TV_PROVIDER("com.android.support", "support-tv-provider"),
  ANDROIDX_TVPROVIDER("androidx.tvprovider", "tvprovider"),
  SUPPORT_V13("com.android.support", "support-v13"),
  ANDROIDX_LEGACY_SUPPORT_V13("androidx.legacy", "legacy-support-v13"),
  SUPPORT_V4("com.android.support", "support-v4"),
  ANDROIDX_LEGACY_SUPPORT_V4("androidx.legacy", "legacy-support-v4"),
  SUPPORT_VECTOR_DRAWABLE("com.android.support", "support-vector-drawable"),
  ANDROIDX_VECTORDRAWABLE("androidx.vectordrawable", "vectordrawable"),
  SUPPORT_TRANSITION("com.android.support", "transition"),
  ANDROIDX_TRANSITION("androidx.transition", "transition"),
  SUPPORT_WEAR("com.android.support", "wear"),
  ANDROIDX_WEAR("androidx.wear", "wear"),
  SUPPORT_ASYNCLAYOUTINFLATER("com.android.support", "asynclayoutinflater"),
  ANDROIDX_ASYNCLAYOUTINFLATER("androidx.asynclayoutinflater", "asynclayoutinflater"),
  SUPPORT_COORDINATORLAYOUT("com.android.support", "coordinatorlayout"),
  ANDROIDX_COORDINATORLAYOUT("androidx.coordinatorlayout", "coordinatorlayout"),
  SUPPORT_CURSORADAPTER("com.android.support", "cursoradapter"),
  ANDROIDX_CURSORADAPTER("androidx.cursoradapter", "cursoradapter"),
  SUPPORT_CUSTOMVIEW("com.android.support", "customview"),
  ANDROIDX_CUSTOMVIEW("androidx.customview", "customview"),
  SUPPORT_DOCUMENTFILE("com.android.support", "documentfile"),
  ANDROIDX_DOCUMENTFILE("androidx.documentfile", "documentfile"),
  SUPPORT_DRAWERLAYOUT("com.android.support", "drawerlayout"),
  ANDROIDX_DRAWERLAYOUT("androidx.drawerlayout", "drawerlayout"),
  SUPPORT_INTERPOLATOR("com.android.support", "interpolator"),
  ANDROIDX_INTERPOLATOR("androidx.interpolator", "interpolator"),
  SUPPORT_LOADER("com.android.support", "loader"),
  ANDROIDX_LOADER("androidx.loader", "loader"),
  SUPPORT_LOCALBROADCASTMANAGER("com.android.support", "localbroadcastmanager"),
  ANDROIDX_LOCALBROADCASTMANAGER("androidx.localbroadcastmanager", "localbroadcastmanager"),
  SUPPORT_PRINT("com.android.support", "print"),
  ANDROIDX_PRINT("androidx.print", "print"),
  SUPPORT_SLIDINGPANELLAYOUT("com.android.support", "slidingpanelayout"),
  ANDROIDX_SLIDINGPANELLAYOUT("androidx.slidingpanelayout", "slidingpanelayout"),
  SUPPORT_SWIPEREFRESHLAYOUT("com.android.support", "swiperefreshlayout"),
  ANDROIDX_SWIPERFRESHLAYOUT("androidx.swiperefreshlayout", "swiperefreshlayout"),
  SUPPORT_VERSIONEDPARCELABLE("com.android.support", "versionedparcelable"),
  ANDROIDX_VERSIONEDPARCELABLE("androidx.versionedparcelable", "versionedparcelable"),
  SUPPORT_VIEWPAGER("com.android.support", "viewpager"),
  ANDROIDX_VIEWPAGER("androidx.viewpager", "viewpager"),
  SUPPORT_SLICES_CORE("com.android.support", "slices-core"),
  ANDROIDX_SLICE_CORE("androidx.slice", "slice-core"),
  SUPPORT_SLICES_BUILDERS("com.android.support", "slices-builders"),
  ANDROIDX_SLICE_BUILDERS("androidx.slice", "slice-builders"),
  SUPPORT_SLICES_VIEW("com.android.support", "slices-view"),
  ANDROIDX_SLICE_VIEW("androidx.slice", "slice-view"),
  SUPPORT_WEBKIT("com.android.support", "webkit"),
  ANDROIDX_WEBKIT("androidx.webkit", "webkit"),
  DATABINDING_ADAPTERS("com.android.databinding", "adapters"),
  ANDROIDX_DATABINDING_ADAPTERS("androidx.databinding", "databinding-adapters"),
  DATABINDING_BASE_LIBRARY("com.android.databinding", "baseLibrary"),
  ANDROIDX_DATABINDING_COMMON("androidx.databinding", "databinding-common"),
  DATABINDING_COMPILER("com.android.databinding", "compiler"),
  ANDROIDX_DATABINDING_COMPILER("androidx.databinding", "databinding-compiler"),
  DATABDINDING_COMPILER_COMMON("com.android.databinding", "compilerCommon"),
  ANDROIDX_DATABINDING_COMPILER_COMMON("androidx.databinding", "databinding-compiler-common"),
  DATABINDING_LIBRARY("com.android.databinding", "library"),
  ANDROIDX_DATABINDING_RUNTIME("androidx.databinding", "databinding-runtime"),
  WORK_RUNTIME("android.arch.work", "work-runtime"),
  ANDROIDX_WORK_RUNTIME("androidx.work", "work-runtime"),
  WORK_RUNTIME_KTX("android.arch.work", "work-runtime-ktx"),
  ANDROIDX_WORK_RUNTIME_KTX("androidx.work", "work-runtime-ktx"),
  WORK_TESTING("android.arch.work", "work-testing"),
  ANDROIDX_WORK_TESTING("androidx.work", "work-testing"),
  WORK_RXJAVA2("android.arch.work", "work-rxjava2"),
  ANDROIDX_WORK_RXJAVA2("androidx.work", "work-rxjava2"),
  NAVIGATION_COMMON("android.arch.navigation", "navigation-common"),
  ANDROIDX_NAVIGATION_COMMON("androidx.navigation", "navigation-common"),
  NAVIGATION_COMMON_KTX("android.arch.navigation", "navigation-common-ktx"),
  ANDROIDX_NAVIGATION_COMMON_KTX("androidx.navigation", "navigation-common-ktx"),
  NAVIGATION_FRAGMENT("android.arch.navigation", "navigation-fragment"),
  ANDROIDX_NAVIGATION_FRAGMENT("androidx.navigation", "navigation-fragment"),
  NAVIGATION_FRAGMENT_KTX("android.arch.navigation", "navigation-fragment-ktx"),
  ANDROIDX_NAVIGATION_FRAGMENT_KTX("androidx.navigation", "navigation-fragment-ktx"),
  NAVIGATION_RUNTIME("android.arch.navigation", "navigation-runtime"),
  ANDROIDX_NAVIGATION_RUNTIME("androidx.navigation", "navigation-runtime"),
  NAVIGATION_RUNTIME_KTX("android.arch.navigation", "navigation-runtime-ktx"),
  ANDROIDX_NAVIGATION_RUNTIME_KTX("androidx.navigation", "navigation-runtime-ktx"),
  NAVIGATION_SAFE_ARGS_GENERATOR("android.arch.navigation", "navigation-safe-args-generator"),
  ANDROIDX_NAVIGATION_SAFE_ARGS_GENERATOR("androidx.navigation", "navigation-safe-args-generator"),
  NAVIGATION_SAFE_ARGS_GRADLE_PLUGIN("android.arch.navigation", "navigation-safe-args-gradle-plugin"),
  ANDROIDX_NAVIGATION_SAFE_ARGS_GRADLE_PLUGIN("androidx.navigation", "navigation-safe-args-gradle-plugin"),
  NAVIGATION_UI("android.arch.navigation", "navigation-ui"),
  ANDROIDX_NAVIGATION_UI("androidx.navigation", "navigation-ui"),
  NAVIGATION_UI_KTX("android.arch.navigation", "navigation-ui-ktx"),
  ANDROIDX_NAVIGATION_UI_KTX("androidx.navigation", "navigation-ui-ktx"),
  CORE_COMMON("android.arch.core", "common"),
  ANDROIDX_CORE_COMMON("androidx.arch.core", "core-common"),
  CORE_CORE("android.arch.core", "core"),
  ANDROIDX_CORE_RUNTIME("androidx.arch.core", "core-runtime"),
  CORE_TESTING("android.arch.core", "core-testing"),
  ANDROIDX_CORE_TESTING("androidx.arch.core", "core-testing"),
  CORE_RUNTIME("android.arch.core", "runtime"),
  // ANDROIDX_CORE_RUNTIME
  LIFECYCLE_COMMON("android.arch.lifecycle", "common"),
  ANDROIDX_LIFECYCLE_COMMON("androidx.lifecycle", "lifecycle-common"),
  LIFECYCLE_COMMON_JAVA8("android.arch.lifecycle", "common-java8"),
  ANDROIDX_LIFECYCLE_COMMON_JAVA8("androidx.lifecycle", "lifecycle-common-java8"),
  LIFECYCLE_COMPILER("android.arch.lifecycle", "compiler"),
  ANDROIDX_LIFECYCLE_COMPILER("androidx.lifecycle", "lifecycle-compiler"),
  LIFECYCLE_EXTENSIONS("android.arch.lifecycle", "extensions"),
  ANDROIDX_LIFECYCLE_EXTENSIONS("androidx.lifecycle", "lifecycle-extensions"),
  LIFECYCLE_REACTIVESTREAMS("android.arch.lifecycle", "reactivestreams"),
  ANDROIDX_LIFECYCLE_REACTIVESTREAMS("androidx.lifecycle", "lifecycle-reactivestreams"),
  LIFECYCLE_RUNTIME("android.arch.lifecycle", "runtime"),
  ANDROIDX_LIFECYCLE_RUNTIME("androidx.lifecycle", "lifecycle-runtime"),
  LIFECYCLE_VIEWMODEL("android.arch.lifecycle", "viewmodel"),
  ANDROIDX_LIFECYCLE_VIEWMODEL("androidx.lifecycle", "lifecycle-viewmodel"),
  LIFECYCLE_LIVEDATA("android.arch.lifecycle", "livedata"),
  ANDROIDX_LIFECYCLE_LIVEDATA("androidx.lifecycle", "lifecycle-livedata"),
  LIFECYCLE_LIVEDATA_CORE("android.arch.lifecycle", "livedata-core"),
  ANDROIDX_LIFECYCLE_LIVEDATA_CORE("androidx.lifecycle", "lifecycle-livedata-core"),
  PAGING_COMMON("android.arch.paging", "common"),
  ANDROIDX_PAGING_COMMON("androidx.paging", "paging-common"),
  PAGING_RUNTIME("android.arch.paging", "runtime"),
  ANDROIDX_PAGING_RUNTIME("androidx.paging", "paging-runtime"),
  PAGING_RXJAVA2("android.arch.paging", "rxjava2"),
  ANDROIDX_PAGING_RXJAVA2("androidx.paging", "paging-rxjava2"),
  PERSISTENCE_DB("android.arch.persistence", "db"),
  ANDROIDX_SQLITE("androidx.sqlite", "sqlite"),
  PERSISTENCE_DB_FRAMEWORK("android.arch.persistence", "db-framework"),
  ANDROIDX_SQLITE_FRAMEWORK("androidx.sqlite", "sqlite-framework"),
  ROOM_COMMON("android.arch.persistence.room", "common"),
  ANDROIDX_ROOM_COMMON("androidx.room", "room-common"),
  ROOM_COMPILER("android.arch.persistence.room", "compiler"),
  ANDROIDX_ROOM_COMPILER("androidx.room", "room-compiler"),
  ROOM_MIGRATION("android.arch.persistence.room", "migration"),
  ANDROIDX_ROOM_MIGRATION("androidx.room", "room-migration"),
  ROOM_RUNTIME("android.arch.persistence.room", "runtime"),
  ANDROIDX_ROOM_RUNTIME("androidx.room", "room-runtime"),
  ROOM_RXJAVA2("android.arch.persistence.room", "rxjava2"),
  ANDROIDX_ROOM_RXJAVA2("androidx.room", "room-rxjava2"),
  ROOM_TESTING("android.arch.persistence.room", "testing"),
  ANDROIDX_ROOM_TESTING("androidx.room", "room-testing"),
  ROOM_GUAVA("android.arch.persistence.room", "guava"),
  ANDROIDX_ROOM_GUAVA("androidx.room", "room-guava"),
  CONSTRAINT_LAYOUT("com.android.support.constraint", "constraint-layout"),
  ANDROIDX_CONSTRAINTLAYOUT("androidx.constraintlayout", "constraintlayout"),
  CONSTRAINT_LAYOUT_SOLVER("com.android.support.constraint", "constraint-layout-solver"),
  ANDROIDX_CONSTRAINTLAYOUT_SOLVER("androidx.constraintlayout", "constraintlayout-solver"),
  TEST_MONITOR("com.android.support.test", "monitor"),
  ANDROIDX_TEST_MONITOR("androidx.test", "monitor"),
  TEST_ORCHESTRATOR("com.android.support.test", "orchestrator"),
  ANDROIDX_TEST_ORCHESTRATOR("androidx.test", "orchestrator"),
  TEST_RULES("com.android.support.test", "rules"),
  ANDROIDX_TEST_RULES("androidx.test", "rules"),
  TEST_RUNNER("com.android.support.test", "runner"),
  ANDROIDX_JUNIT("androidx.test.ext", "junit"),
  ESPRESSO_ACCESSIBILITY("com.android.support.test.espresso", "espresso-accessibility"),
  ANDROIDX_ESPRESSO_ACCESSIBILITY("androidx.test.espresso", "espresso-accessibility"),
  ESPRESSO_CONTRIB("com.android.support.test.espresso", "espresso-contrib"),
  ANDROIDX_ESPRESSO_CONTRIB("androidx.test.espresso", "espresso-contrib"),
  ESPRESSO_CORE("com.android.support.test.espresso", "espresso-core"),
  ANDROIDX_ESPRESSO_CORE("androidx.test.espresso", "espresso-core"),
  ESPRESSO_IDLING_RESOURCE("com.android.support.test.espresso", "espresso-idling-resource"),
  ANDROIDX_ESPRESSO_IDLING_RESOURCE("androidx.test.espresso", "espresso-idling-resource"),
  ESPRESSO_INTENTS("com.android.support.test.espresso", "espresso-intents"),
  ANDROIDX_ESPRESSO_INTENTS("androidx.test.espresso", "espresso-intents"),
  ESPRESSO_REMOTE("com.android.support.test.espresso", "espresso-remote"),
  ANDROIDX_ESPRESSO_REMOTE("androidx.test.espresso", "espresso-remote"),
  ESPRESSO_WEB("com.android.support.test.espresso", "espresso-web"),
  ANDROIDX_ESPRESSO_WEB("androidx.test.espresso", "espresso-web"),
  IDLING_CONCURRENT("com.android.support.test.espresso.idling", "idling-concurrent"),
  ANDROIDX_IDLING_CONCURRENT("androidx.test.espresso.idling", "idling-concurrent"),
  IDLING_NET("com.android.support.test.espresso.idling", "idling-net"),
  ANDROIDX_IDLING_NET("androidx.test.espresso.idling", "idling-net"),
  JANKTESTHELPER_V23("com.android.support.test.janktesthelper", "janktesthelper-v23"),
  ANDROIDX_JANKTESTHELPER("androidx.test.janktesthelper", "janktesthelper"),
  TEST_SERVICES("com.android.support.test.services", "test-services"),
  ANDROIDX_TEST_SERVICES("androidx.test.services", "test-services"),
  UIAUTOMATOR_V18("com.android.support.test.uiautomator", "uiautomator-v18"),
  ANDROIDX_UIAUTOMATOR("androidx.test.uiautomator", "uiautomator"),
  SUPPORT_MEDIA2("com.android.support", "media2"),
  ANDROIDX_MEDIA2("androidx.media2", "media2"),
  SUPPORT_MEDIA2_EXOPLAYER("com.google.android.exoplayer", "extension-media2"),
  ANDROIDX_MEDIA2_EXOPLAYER("androidx.media2", "media2-exoplayer"),

  // Misc. layouts
  FLEXBOX_LAYOUT("com.google.android.flexbox", "flexbox"),
  ANDROIDX_VIEWPAGER2("androidx.viewpager2", "viewpager2"),

  // Navigation
  ANDROIDX_NAVIGATION_DYNAMIC_FEATURES_FRAGMENT(
      "androidx.navigation",
      "navigation-dynamic-features-fragment"
  ),

  // Google repo
  PLAY_SERVICES("com.google.android.gms", "play-services"),
  PLAY_SERVICES_ADS("com.google.android.gms", "play-services-ads"),
  PLAY_SERVICES_WEARABLE("com.google.android.gms", "play-services-wearable"),
  PLAY_SERVICES_MAPS("com.google.android.gms", "play-services-maps"),
  SUPPORT_WEARABLE("com.google.android.support", "wearable"),

  // Compose
  COMPOSE_RUNTIME("androidx.compose.runtime", "runtime"),
  COMPOSE_TOOLING("androidx.compose.ui", "ui-tooling"),
  COMPOSE_TOOLING_PREVIEW("androidx.compose.ui", "ui-tooling-preview"),
  COMPOSE_UI("androidx.compose.ui", "ui"),

  // Wear Tiles
  WEAR_TILES_TOOLING("androidx.wear.tiles", "tiles-tooling"),

  // Lifecycle
  ANDROIDX_LIFECYCLE_VIEWMODEL_KTX("androidx.lifecycle", "lifecycle-viewmodel-ktx"),

  // Activity
  ACTIVITY_COMPOSE("androidx.activity", "activity-compose"),

  // Core-Ktx
  ANDROIDX_CORE_KTX("androidx.core", "core-ktx"),
  // Room-Ktx
  ANDROIDX_ROOM_KTX("androidx.room", "room-ktx"),
  ;

  override val groupId = mavenGroupId
  override val artifactId = mavenArtifactId

  override fun toString(): String = displayName

  companion object {
    private val ENTRIES_BY_MODULEID = entries.associateBy { it.toString() }
    private val ENTRIES_BY_GROUP_ARTIFACT_PAIR = entries.associateBy { it.mavenGroupId to it.artifactId }

    @JvmStatic fun find(moduleId: String): GoogleMavenArtifactId? =
        ENTRIES_BY_MODULEID[moduleId]

    @JvmStatic fun find(groupId: String, artifactId: String): GoogleMavenArtifactId? =
        ENTRIES_BY_GROUP_ARTIFACT_PAIR[groupId to artifactId]

    @JvmStatic fun androidxIdOf(id: GoogleMavenArtifactId): GoogleMavenArtifactId =
        find(AndroidxNameUtils.getCoordinateMapping(id.toString())) ?: id
  }
}
