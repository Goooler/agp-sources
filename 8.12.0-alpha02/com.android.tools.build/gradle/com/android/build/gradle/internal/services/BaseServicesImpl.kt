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

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.utils.GradleEnvironmentProvider
import com.android.build.gradle.internal.utils.GradleEnvironmentProviderImpl
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.IssueReporter
import org.gradle.api.services.BuildServiceRegistry
import java.io.File

/**
 * Impl for BaseScope over a [ProjectServices]
 */
open class BaseServicesImpl(protected val projectServices: ProjectServices):
    BaseServices {

    final override fun <T> newInstance(type: Class<T>, vararg args: Any?): T = projectServices.objectFactory.newInstance(type, *args)

    final override fun file(file: Any): File = projectServices.fileResolver.invoke(file)

    final override val issueReporter: IssueReporter
        get() = projectServices.issueReporter
    final override val deprecationReporter: DeprecationReporter
        get() = projectServices.deprecationReporter
    final override val projectOptions: ProjectOptions
        get() = projectServices.projectOptions
    final override val buildServiceRegistry: BuildServiceRegistry
        get() = projectServices.buildServiceRegistry

    final override val gradleEnvironmentProvider: GradleEnvironmentProvider =
        GradleEnvironmentProviderImpl(projectServices.providerFactory)

    final override val projectInfo: ProjectInfo
        get() = projectServices.projectInfo

    final override val builtInKotlinServices: BuiltInKotlinServices
        get() = projectServices.builtInKotlinServices
}
