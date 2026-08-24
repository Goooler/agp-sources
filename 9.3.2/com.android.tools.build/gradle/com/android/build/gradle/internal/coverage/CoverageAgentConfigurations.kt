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

package com.android.build.gradle.internal.coverage

import com.android.build.gradle.internal.services.BaseServices

/** Configurations for the on-the-fly coverage agent. */
object CoverageAgentConfigurations {
  private const val AGENT_DEPENDENCY_GROUP = "com.android.tools.test"
  private const val AGENT_DEPENDENCY_NAME = "coverage-agent"
  private const val DEFAULT_AGENT_VERSION = "1.0.0"

  @JvmStatic
  fun getAgentRuntimeDependency(services: BaseServices): String {
    return "$AGENT_DEPENDENCY_GROUP:$AGENT_DEPENDENCY_NAME:$DEFAULT_AGENT_VERSION"
  }
}
