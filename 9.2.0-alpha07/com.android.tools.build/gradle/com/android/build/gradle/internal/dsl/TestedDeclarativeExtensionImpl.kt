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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.dsl.TestFixtures
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.options.BooleanOption

abstract class TestedDeclarativeExtensionImpl<
  BuildTypeT : com.android.build.api.dsl.BuildType,
  DefaultConfigT : DefaultConfig,
  ProductFlavorT : com.android.build.api.dsl.ProductFlavor,
>(dslServices: DslServices) :
  CommonDeclarativeDefinitionImpl<BuildTypeT, DefaultConfigT, ProductFlavorT>(dslServices),
  com.android.build.api.dsl.TestedDeclarativeDefinition {

  override var testBuildType = "debug"
  override var testNamespace: String? = null

  override val testFixtures: TestFixtures =
    dslServices.newInstance(TestFixturesImpl::class.java, dslServices.projectOptions[BooleanOption.ENABLE_TEST_FIXTURES])
}
