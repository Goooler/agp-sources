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
package com.android.build.gradle.internal.cxx.process

import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.utils.cxx.os.quoteExecutablePath
import java.io.File
import javax.inject.Inject
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations

abstract class ExecuteProcessValueSource : ValueSource<Int, ExecuteProcessValueSource.Parameters> {
  interface Parameters : ValueSourceParameters {
    val useScript: Property<Boolean>
    val executable: Property<String>
    val args: ListProperty<String>
    val environment: MapProperty<String, String>
    val commandFile: Property<String>
    val stderrFile: RegularFileProperty
    val stdoutFile: RegularFileProperty
    val logStderr: Property<Boolean>
    val logStdout: Property<Boolean>
    val logFullStdout: Property<Boolean>
    val workingDirectory: Property<File>
  }

  @get:Inject abstract val execOperations: ExecOperations

  override fun obtain(): Int? {
    val process =
      if (parameters.useScript.get()) {
        ProcessInfoBuilder()
          .setExecutable(quoteExecutablePath(parameters.commandFile.get()))
          .addEnvironments(parameters.environment.get())
          .setDirectory(parameters.workingDirectory.get())
          .createProcess()
      } else {
        ProcessInfoBuilder()
          .setExecutable(quoteExecutablePath(parameters.executable.get()))
          .addArgs(parameters.args.get())
          .addEnvironments(parameters.environment.get())
          .setDirectory(parameters.workingDirectory.get())
          .createProcess()
      }

    val result =
      GradleProcessExecutor(execOperations::exec)
        .execute(
          process,
          DefaultProcessOutputHandler(
            stderrFile = parameters.stderrFile.get().asFile,
            stdoutFile = parameters.stdoutFile.get().asFile,
            logPrefix = "",
            logStderr = parameters.logStderr.get(),
            logStdout = parameters.logStdout.get(),
            logFullStdout = parameters.logFullStdout.get(),
          ),
        )

    return result.exitValue
  }
}
