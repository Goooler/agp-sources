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
import com.android.build.gradle.internal.errors.SyncIssueReporter
import com.android.build.gradle.internal.lint.LintFromMaven
import com.android.build.gradle.internal.res.Aapt2FromMaven
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.ProjectOptions
import org.gradle.api.Task
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.plugins.PluginManager
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import java.io.File

/**
 * Service object for the project, containing a bunch of project-provided items that can be exposed
 * to different stages of the plugin work.
 *
 * This is not meant to be exposed directly to most classes. It's meant to be a convenient storage for
 * all these objects so that they don't have to be recreated or passed to methods/constructors
 * all the time.
 *
 * Stage-specific services should expose only part of what these objects expose, based on the need
 * of the context.
 */
class ProjectServices constructor(
    val issueReporter: SyncIssueReporter,
    val deprecationReporter: DeprecationReporter,
    val objectFactory: ObjectFactory,
    val logger: Logger,
    val providerFactory: ProviderFactory,
    val projectLayout: ProjectLayout,
    val projectOptions: ProjectOptions,
    val buildServiceRegistry: BuildServiceRegistry,
    val lintFromMaven: LintFromMaven,
    private val aapt2FromMaven: Aapt2FromMaven? = null,
    private val maxWorkerCount: Int,
    val projectInfo: ProjectInfo,
    val fileResolver: (Any) -> File,
    val configurationContainer: ConfigurationContainer,
    val dependencyHandler: DependencyHandler,
    val extraProperties: ExtraPropertiesExtension,
    val emptyTaskCreator: (String) -> TaskProvider<*>,
    val plugins: PluginManager,
) {
    fun initializeAapt2Input(
        aapt2Input: Aapt2Input,
        task: Task? // null iff the caller is a transform
    ) {
        if (task != null) {
            aapt2Input.initializeAapt2DaemonBuildService(task)
            aapt2Input.initializeAapt2ThreadPoolBuildService(task)
        } else {
            aapt2Input.aapt2DaemonBuildService.setDisallowChanges(getBuildService(buildServiceRegistry))
            aapt2Input.aapt2ThreadPoolBuildService.setDisallowChanges(getBuildService(buildServiceRegistry))
        }
        aapt2Input.binaryDirectory.from(aapt2FromMaven?.aapt2Directory)
        aapt2Input.binaryDirectory.disallowChanges()
        aapt2Input.version.setDisallowChanges(aapt2FromMaven?.version)
        aapt2Input.maxWorkerCount.setDisallowChanges(maxWorkerCount)
        aapt2Input.maxAapt2Daemons.setDisallowChanges(computeMaxAapt2Daemons(projectOptions))
    }

    val builtInKotlinServices: BuiltInKotlinServices by lazy {
        BuiltInKotlinServices.createFromPlugin(
            kotlinBaseApiPlugin = projectInfo.getPlugin(KotlinBaseApiPlugin::class.java),
            kotlinAndroidProjectExtension = projectInfo.getExtension(KotlinAndroidProjectExtension::class.java),
            projectName = projectInfo.name
        )
    }
}
