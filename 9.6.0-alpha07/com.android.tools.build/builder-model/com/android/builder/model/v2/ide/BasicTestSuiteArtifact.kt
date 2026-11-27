/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.builder.model.v2.ide

import com.android.builder.model.v2.AndroidModel

// TODO(b/575647801): remove once MINIMUM_MODEL_CONSUMER excludes Studio versions that read this.
/**
 * Basic information about a test suite that is applied to a variant.
 *
 * Only returned by the deprecated [BasicVariant.testSuiteArtifacts], which is no longer populated.
 */
@Deprecated("Only returned by the deprecated BasicVariant.testSuiteArtifacts; use BasicVariant.testSuiteVariantTargets")
interface BasicTestSuiteArtifact : AndroidModel {

  val testSuiteName: String
}
