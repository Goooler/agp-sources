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

package com.android.build.gradle.tasks

import com.android.build.api.testsuites.TestEngineInputProperty
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.testing.LegacyTestReportingRedirectionWatcher
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal

/**
 * A subclass of [TestSuiteTestTask] that redirects test result events from a streaming file to stdout.
 *
 * This is a legacy workaround for Android Studio to consume test results in real-time. In the future, this will be replaced by a more
 * robust solution using the Gradle Tooling API.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class LegacyReportingTestSuiteTestTask : TestSuiteTestTask() {

  companion object {
    /** This Gradle property key is hard coded in Android Studio. */
    const val ENABLE_UTP_REPORTING_PROPERTY = "com.android.tools.utp.GradleAndroidProjectResolverExtension.enable"
  }

  /**
   * Whether to redirect test result events from a streaming file to stdout.
   *
   * This is a legacy workaround for Android Studio to consume test results in real-time. In the future, this will be replaced by a more
   * robust solution using the Gradle Tooling API.
   */
  @get:Internal abstract val legacyTestReportingRedirectionEnabled: Property<Boolean>

  override fun doExecuteTests(engineInputParameters: List<TestEngineInputProperty>) {
    if (legacyTestReportingRedirectionEnabled.get()) {
      LegacyTestReportingRedirectionWatcher(streamingOutputFile.get().asFile).use { watcher ->
        watcher.start()
        super.doExecuteTests(engineInputParameters)
      }
    } else {
      super.doExecuteTests(engineInputParameters)
    }
  }
}
