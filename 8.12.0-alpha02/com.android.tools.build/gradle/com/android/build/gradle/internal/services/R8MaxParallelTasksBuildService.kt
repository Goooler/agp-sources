/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.ProjectOptions
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Build service to manage R8 parallelism. The number of concurrent R8 tasks can be controlled by the
 * [com.android.build.gradle.options.IntegerOption.R8_MAX_WORKERS] integer option.
 */
abstract class R8MaxParallelTasksBuildService : BuildService<BuildServiceParameters.None>, AutoCloseable {

    class RegistrationAction(project: Project, projectOptions: ProjectOptions) :
        ServiceRegistrationAction<R8MaxParallelTasksBuildService, BuildServiceParameters.None>(
            project,
            R8MaxParallelTasksBuildService::class.java,
            // This `IntegerOption` has default value so get() should return not-null
            projectOptions.get(IntegerOption.R8_MAX_WORKERS)!!
        ) {

        override fun configure(parameters: BuildServiceParameters.None) {
           // Do nothing
        }
    }
}
