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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.ResourcesPackaging
import com.android.build.gradle.internal.dsl.decorator.annotation.WithLazyInitialization
import com.android.build.gradle.internal.packaging.defaultExcludes
import com.android.build.gradle.internal.packaging.defaultMerges

abstract class ResourcesPackagingImpl: ResourcesPackaging {

    @WithLazyInitialization
    @Suppress("unused") // the call is injected by DslDecorator
    protected fun lazyInit() {
        setExcludes(defaultExcludes)
        setMerges(defaultMerges)
    }

    // support excludes += 'foo' syntax in groovy
    abstract fun setExcludes(patterns: Set<String>)

    // support pickFirsts += 'foo' syntax in groovy
    abstract fun setPickFirsts(patterns: Set<String>)

    // support merges += 'foo' syntax in groovy
    abstract fun setMerges(patterns: Set<String>)
}
