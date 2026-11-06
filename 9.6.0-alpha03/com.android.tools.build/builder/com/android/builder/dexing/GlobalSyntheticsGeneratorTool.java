/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.builder.dexing;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.GlobalSyntheticsGenerator;
import com.android.tools.r8.GlobalSyntheticsGeneratorCommand;
import com.android.tools.r8.OutputMode;
import com.android.utils.FileUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Bridge to invoke R8's {@code GlobalSyntheticsGenerator} and {@code D8} compiler.
 *
 * <p>Architectural Constraint: This class resides in {@code builder-r8} because {@code gradle-core}
 * cannot access shadowed R8 classes. It acts as the execution facade for {@code
 * GlobalSyntheticsGeneratorTask}.
 *
 * <p>Generates a {@code .globals} file and immediately compiles it to DEX.
 *
 * <p><b>Note on Pollution Prevention:</b> The {@code GlobalSyntheticsGenerator} is invoked
 * <i>without</i> library inputs to ensure it generates only generic global synthetics (like {@code
 * LambdaMethod}) and not platform-specific bridges (which would duplicate the Android SDK in the
 * APK). D8 is then invoked in strict debug mode to compile these synthetics using the bootclasspath
 * purely for type resolution.
 */
public class GlobalSyntheticsGeneratorTool {

    public static void generate(Collection<Path> bootClasspath, int minSdk, Path outputDir)
            throws Exception {
        Path tempDir = Files.createTempDirectory("global_synthetics_intermediate");
        Path intermediateGlobals = tempDir.resolve("all.globals");

        // We intentionally provide only the bootclasspath to avoid polluting the global
        // synthetics with application classes. Because the classpath is incomplete, R8
        // will naturally generate benign "Missing class" warnings (e.g., java.lang.Record).
        // We must suppress these expected warnings so they do not pollute the Gradle console
        // and confuse users. We intentionally do not override error() so fatal issues propagate.
        DiagnosticsHandler silentWarningHandler =
                new DiagnosticsHandler() {
                    @Override
                    public void warning(Diagnostic diagnostic) {
                        // Suppress benign missing class warnings
                    }

                    @Override
                    public void info(Diagnostic diagnostic) {
                        // Suppress info messages
                    }
                };

        try {
            GlobalSyntheticsGeneratorCommand genCommand =
                    GlobalSyntheticsGeneratorCommand.builder(silentWarningHandler)
                            .addLibraryFiles(bootClasspath)
                            .setMinApiLevel(minSdk)
                            .setGlobalSyntheticsOutput(intermediateGlobals)
                            .build();

            GlobalSyntheticsGenerator.run(genCommand);
            D8Command d8Command =
                    D8Command.builder(silentWarningHandler)
                            .addLibraryFiles(bootClasspath)
                            .setMinApiLevel(minSdk)
                            .setMode(CompilationMode.DEBUG)
                            .setDisableDesugaring(true)
                            .addGlobalSyntheticsFiles(intermediateGlobals)
                            .setOutput(outputDir, OutputMode.DexIndexed)
                            .build();

            D8.run(d8Command);

        } finally {
            FileUtils.deleteRecursivelyIfExists(tempDir.toFile());
        }
    }
}
