/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.gradle.external.cmake.CmakeUtils
import com.android.build.gradle.external.cmake.server.ComputeResult
import com.android.build.gradle.external.cmake.server.ConfigureCommandResult
import com.android.build.gradle.external.cmake.server.HandshakeRequest
import com.android.build.gradle.external.cmake.server.ProtocolVersion
import com.android.build.gradle.external.cmake.server.Server
import com.android.build.gradle.external.cmake.server.ServerFactory
import com.android.build.gradle.external.cmake.server.ServerUtils
import com.android.build.gradle.external.cmake.server.Target
import com.android.build.gradle.external.cmake.server.findRuntimeFiles
import com.android.build.gradle.external.cmake.server.receiver.InteractiveMessage
import com.android.build.gradle.external.cmake.server.receiver.ServerReceiver
import com.android.build.gradle.internal.cxx.build.CxxRegularBuilder.Companion.BUILD_TARGETS_PLACEHOLDER
import com.android.build.gradle.internal.cxx.cmake.makeCmakeMessagePathsAbsolute
import com.android.build.gradle.internal.cxx.configure.convertCMakeToCompileCommandsBin
import com.android.build.gradle.internal.cxx.configure.getCmakeBinaryOutputPath
import com.android.build.gradle.internal.cxx.configure.getCmakeGenerator
import com.android.build.gradle.internal.cxx.configure.onlyKeepCmakeServerArguments
import com.android.build.gradle.internal.cxx.configure.toCmakeArguments
import com.android.build.gradle.internal.cxx.configure.toStringList
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue
import com.android.build.gradle.internal.cxx.json.NativeLibraryValue
import com.android.build.gradle.internal.cxx.logging.PassThroughPrintWriterLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.additionalProjectFilesIndexFile
import com.android.build.gradle.internal.cxx.model.cmakeServerLogFile
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonBinFile
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonFile
import com.android.build.gradle.internal.cxx.model.createNinjaCommand
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.name
import com.android.build.gradle.internal.cxx.process.ExecuteProcessCommand
import com.android.build.gradle.internal.cxx.process.createExecuteProcessCommand
import com.android.ide.common.process.ProcessException
import com.android.utils.cxx.CxxDiagnosticCode.CMAKE_SERVER_INTERACTIVE_ERROR
import com.android.utils.cxx.CxxDiagnosticCode.CMAKE_SERVER_SOURCE_DIRECTORY_MISSING
import com.google.common.base.Preconditions
import com.google.common.collect.Maps
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.CMAKE
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * This strategy uses the Vanilla-CMake that supports Cmake server version 1.0 to configure the project and generate the android build JSON.
 */
internal class CmakeServerExternalNativeJsonGenerator(abi: CxxAbiModel, variantBuilder: GradleBuildVariant.Builder?) :
  ExternalNativeJsonGenerator(abi, variantBuilder) {
  init {
    variantBuilder?.nativeBuildSystemType = CMAKE
    cmakeMakefileChecks(abi.variant)
  }

  private val cmake
    get() = abi.variant.module.cmake!!

  override fun executeProcess(providers: ProviderFactory, abi: CxxAbiModel) {
    providers
      .of(CmakeServerValueSource::class.java) { spec ->
        spec.parameters.cmakeServerLogFile.set(abi.cmakeServerLogFile.absoluteFile)
        spec.parameters.makeFile.set(abi.variant.module.makeFile)
        spec.parameters.cmakeExe.set(cmake.cmakeExe!!)
        spec.parameters.configurationArguments.set(abi.configurationArguments)
        spec.parameters.jsonFile.set(abi.jsonFile)
        spec.parameters.compileCommandsJsonFile.set(abi.compileCommandsJsonFile)
        spec.parameters.compileCommandsJsonBinFile.set(abi.compileCommandsJsonBinFile)
        spec.parameters.additionalProjectFilesIndexFile.set(abi.additionalProjectFilesIndexFile)
        spec.parameters.abiName.set(abi.name)
        spec.parameters.debuggableEnabled.set(abi.variant.isDebuggableEnabled)
        spec.parameters.baseNinjaCommand.set(abi.createNinjaCommand())
      }
      .get()
  }

  override fun getProcessBuilder(abi: CxxAbiModel): ExecuteProcessCommand {
    return createExecuteProcessCommand(cmake.cmakeExe!!).addArgs(abi.configurationArguments)
  }

  abstract class CmakeServerValueSource : ValueSource<Boolean, CmakeServerValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
      val cmakeServerLogFile: RegularFileProperty
      val makeFile: RegularFileProperty
      val cmakeExe: RegularFileProperty
      val configurationArguments: ListProperty<String>
      val jsonFile: RegularFileProperty
      val compileCommandsJsonFile: RegularFileProperty
      val compileCommandsJsonBinFile: RegularFileProperty
      val additionalProjectFilesIndexFile: RegularFileProperty
      val abiName: Property<String>
      val debuggableEnabled: Property<Boolean>
      val baseNinjaCommand: ListProperty<String>
    }

    override fun obtain(): Boolean {
      executeProcessAndGetOutput()
      return true
    }

    /** Executes the JSON generation process. */
    private fun executeProcessAndGetOutput() {
      // Once a Cmake server object is created
      // - connect to the server
      // - perform a handshake
      // - configure and compute.
      // Create the NativeBuildConfigValue and write the required JSON file.
      val cmakeServerLogFile = parameters.cmakeServerLogFile.get().asFile
      val makeFile = parameters.makeFile.get().asFile
      cmakeServerLogFile.parentFile.mkdirs()
      PassThroughPrintWriterLoggingEnvironment(PrintWriter(cmakeServerLogFile, "UTF-8"), CMAKE_SERVER_LOG_PREFIX).use {
        // Create a new cmake server for the given Cmake and configure the given project.
        val serverReceiver =
          ServerReceiver()
            .setMessageReceiver { message: InteractiveMessage -> logInteractiveMessage(message, makeFile.parentFile) }
            .setDiagnosticReceiver { message: String? -> infoln(message!!) }
        val cmakeBinFolder = parameters.cmakeExe.get().asFile.parentFile
        val cmakeServer = ServerFactory.create(cmakeBinFolder, serverReceiver)
        if (!cmakeServer.connect()) {
          throw RuntimeException("Unable to connect to Cmake server located at: " + cmakeBinFolder.absolutePath)
        }
        try {
          val arguments = parameters.configurationArguments.get().toCmakeArguments()
          val cacheArgumentsList = arguments.onlyKeepCmakeServerArguments().toStringList()
          val configureCommandResult: ConfigureCommandResult

          // Handshake
          doHandshake(
            arguments.getCmakeGenerator()!!,
            makeFile.parentFile,
            File(arguments.getCmakeBinaryOutputPath()!!),
            cmakeServer,
          )

          // Configure
          val argsArray = cacheArgumentsList.toTypedArray()
          configureCommandResult = cmakeServer.configure(*argsArray)
          if (!ServerUtils.isConfigureResultValid(configureCommandResult.configureResult)) {
            throw ProcessException(
              String.format("Error configuring CMake server (%s).\r\n%s", cmakeServer.cmakePath, configureCommandResult.interactiveMessages)
            )
          }
          val computeResult = doCompute(cmakeServer)
          if (!ServerUtils.isComputedResultValid(computeResult)) {
            throw ProcessException(
              """
                      Error computing CMake server result.
                      ${configureCommandResult.interactiveMessages}
                      """
                .trimIndent()
            )
          }
          generateAndroidGradleBuild(cmakeServer)
        } finally {
          cmakeServer.disconnect()
        }
      }
    }

    /**
     * Requests a handshake to a connected Cmake server.
     *
     * @throws IOException I/O failure. Note: The function throws RuntimeException if we receive an invalid/erroneous handshake result.
     */
    @Throws(IOException::class)
    private fun doHandshake(generator: String, sourceDirectory: File, buildDirectory: File, cmakeServer: Server) {
      val supportedProtocolVersions = cmakeServer.supportedVersion
      if (supportedProtocolVersions == null || supportedProtocolVersions.isEmpty()) {
        throw RuntimeException(String.format("Gradle does not support the Cmake server version. %s", getCmakeInfoString(cmakeServer)))
      }
      val handshakeResult =
        cmakeServer.handshake(getHandshakeRequest(generator, sourceDirectory, buildDirectory, supportedProtocolVersions[0]))
      if (!ServerUtils.isHandshakeResultValid(handshakeResult)) {
        throw RuntimeException(
          String.format(
            "Invalid handshake result from Cmake server: \n%s\n%s",
            CmakeUtils.getObjectToString(handshakeResult),
            getCmakeInfoString(cmakeServer),
          )
        )
      }
    }

    /**
     * Create a default handshake request for the given Cmake server-protocol version
     *
     * @return handshake request
     */
    private fun getHandshakeRequest(
      generator: String,
      sourceDirectory: File,
      buildDirectory: File,
      cmakeServerProtocolVersion: ProtocolVersion,
    ): HandshakeRequest {
      if (!sourceDirectory.isDirectory) {
        errorln(CMAKE_SERVER_SOURCE_DIRECTORY_MISSING, "Not a directory: %s", sourceDirectory)
      }
      val handshakeRequest = HandshakeRequest()
      handshakeRequest.cookie = "gradle-cmake-cookie"
      handshakeRequest.generator = generator
      handshakeRequest.protocolVersion = cmakeServerProtocolVersion
      handshakeRequest.buildDirectory = normalizeFilePath(buildDirectory)
      handshakeRequest.sourceDirectory = normalizeFilePath(sourceDirectory)
      return handshakeRequest
    }

    /**
     * Generates nativeBuildConfigValue by generating the code model from the cmake server and writes the android_gradle_build.json.
     *
     * @throws IOException I/O failure
     */
    @Throws(IOException::class)
    private fun generateAndroidGradleBuild(cmakeServer: Server) {
      val nativeBuildConfigValue = getNativeBuildConfigValue(cmakeServer)
      AndroidBuildGradleJsons.writeNativeBuildConfigValueToJsonFile(parameters.jsonFile.get().asFile, nativeBuildConfigValue)
      convertCMakeToCompileCommandsBin(
        parameters.compileCommandsJsonFile.get().asFile,
        parameters.compileCommandsJsonBinFile.get().asFile,
      )
    }

    /**
     * Returns NativeBuildConfigValue for the given abi from the given Cmake server.
     *
     * @return returns NativeBuildConfigValue
     * @throws IOException I/O failure
     */
    @Throws(IOException::class)
    private fun getNativeBuildConfigValue(cmakeServer: Server): NativeBuildConfigValue {
      val baseNinjaCommand = parameters.baseNinjaCommand.get()
      val abiName = parameters.abiName.get()
      val isDebuggableEnabled = parameters.debuggableEnabled.get()
      val additionalProjectFilesIndexFile = parameters.additionalProjectFilesIndexFile.get().asFile
      val nativeBuildConfigValue = createDefaultNativeBuildConfigValue()
      Preconditions.checkNotNull(nativeBuildConfigValue.buildFiles)
      nativeBuildConfigValue.buildFiles!!.addAll(getBuildFiles(cmakeServer))
      Preconditions.checkNotNull(nativeBuildConfigValue.cleanCommandsComponents)
      nativeBuildConfigValue.cleanCommandsComponents!!.add(baseNinjaCommand + "clean")
      nativeBuildConfigValue.buildTargetsCommandComponents = baseNinjaCommand + BUILD_TARGETS_PLACEHOLDER
      val codeModel = cmakeServer.codemodel()
      if (!ServerUtils.isCodeModelValid(codeModel)) {
        throw RuntimeException(
          String.format(
            "Invalid code model received from Cmake server: \n%s\n%s",
            CmakeUtils.getObjectToString(codeModel),
            getCmakeInfoString(cmakeServer),
          )
        )
      }
      Preconditions.checkNotNull(nativeBuildConfigValue.cFileExtensions)
      nativeBuildConfigValue.cFileExtensions!!.addAll(CmakeUtils.getCExtensionSet(codeModel))
      Preconditions.checkNotNull(nativeBuildConfigValue.cppFileExtensions)
      nativeBuildConfigValue.cppFileExtensions!!.addAll(CmakeUtils.getCppExtensionSet(codeModel))
      additionalProjectFilesIndexFile.parentFile.mkdirs()
      additionalProjectFilesIndexFile.bufferedWriter(StandardCharsets.UTF_8).use { additionalProjectFilesIndexWriter ->
        // Fill in the required fields in NativeBuildConfigValue from the code model obtained from
        // Cmake server.
        for (config in codeModel.configurations) {
          for (project in config.projects) {
            for (target in project.targets) {
              // Ignore targets that aren't valid.
              if (!canAddTargetToNativeLibrary(target)) {
                continue
              }
              val nativeLibraryValue =
                getNativeLibraryValue(abiName, isDebuggableEnabled, baseNinjaCommand, target, additionalProjectFilesIndexWriter)
              val libraryName = target.name + "-" + config.name + "-" + abiName
              Preconditions.checkNotNull(nativeBuildConfigValue.libraries)
              nativeBuildConfigValue.libraries!![libraryName] = nativeLibraryValue
            } // target
          } // project
        }
      }
      return nativeBuildConfigValue
    }

    /** Returns the list of build files used by CMake as part of the build system. Temporary files are currently ignored. */
    private fun getBuildFiles(cmakeServer: Server): List<File> {
      val makeFile = parameters.makeFile.get().asFile
      val cmakeInputsResult = cmakeServer.cmakeInputs()
      if (!ServerUtils.isCmakeInputsResultValid(cmakeInputsResult) || cmakeInputsResult.buildFiles == null) {
        // When CMake server doesn't return a result just use the CMakeLists.txt we know about.
        return listOf(makeFile.absoluteFile)
      }

      // The path to the build file source might be relative, so use the absolute path using
      // source directory information.
      val sourceDirectory = File(cmakeInputsResult.sourceDirectory ?: makeFile.absoluteFile.parent)

      val files =
        listOf(makeFile) +
          cmakeInputsResult.buildFiles
            // Combine multiple results from CMake server
            .flatMap { buildFiles -> buildFiles.sources?.filterNotNull() ?: listOf() }
            .map { File(it) }

      //  remove duplicates, filter down to only CMakeLists.txt
      return files
        // Throw away everything except CMakeLists.txt
        .filter { it.name.compareTo("CMakeLists.txt", ignoreCase = true) == 0 }
        // Make canonical
        .map { file -> sourceDirectory.resolve(file).canonicalFile }
        // Remove duplicates
        .distinct()
        // Sort by length of path to put more important ones higher for readability
        .sortedBy { it.path.length }
    }
  }

  companion object {
    private const val CMAKE_SERVER_LOG_PREFIX = "CMAKE SERVER: "

    private fun getCmakeInfoString(cmakeServer: Server): String {
      return "Cmake path: ${cmakeServer.cmakePath}"
    }

    /** Logs info/warning/error for the given interactive message. Throws a RunTimeException in case of an 'error' message type. */
    private fun logInteractiveMessage(message: InteractiveMessage, makeFileDirectory: File) {
      // CMake error/warning prefix strings. The CMake errors and warnings are part of the
      // message type "message" even though CMake is reporting errors/warnings (Note: They could
      // have a title that says if it's an error or warning, we check that first before checking
      // the prefix of the message string). Hence we would need to parse the output message to
      // figure out if we need to log them as error or warning.
      val cmakeErrorPrefix = "CMake Error"
      val cmakeWarningPrefix = "CMake Warning"

      // If the final message received is of type error, log and error and throw an exception.
      // Note: This is not the same as a message with type "message" with error information, that
      // case is handled below.
      if (message.type != null && message.type == "error") {
        errorln(CMAKE_SERVER_INTERACTIVE_ERROR, makeCmakeMessagePathsAbsolute(message.errorMessage, makeFileDirectory))
        return
      }
      val correctedMessage = makeCmakeMessagePathsAbsolute(message.message, makeFileDirectory)
      if (message.title != null && message.title == "Error" || message.message.startsWith(cmakeErrorPrefix)) {
        errorln(CMAKE_SERVER_INTERACTIVE_ERROR, correctedMessage)
        return
      }
      if (message.title != null && message.title == "Warning" || message.message.startsWith(cmakeWarningPrefix)) {
        warnln(correctedMessage)
        return
      }
      infoln(correctedMessage)
    }

    /**
     * Generate build system files in the build directly, or compute the given project and returns the computed result.
     *
     * @param cmakeServer Connected cmake server.
     * @throws IOException I/O failure. Note: The function throws RuntimeException if we receive an invalid/erroneous ComputeResult.
     */
    @Throws(IOException::class)
    private fun doCompute(cmakeServer: Server): ComputeResult {
      return cmakeServer.compute()
    }

    private fun getNativeLibraryValue(
      abiName: String,
      isDebuggableEnabled: Boolean,
      baseNinjaCommand: List<String>,
      target: Target,
      additionalProjectFilesIndexWriter: BufferedWriter,
    ): NativeLibraryValue {
      val nativeLibraryValue = NativeLibraryValue()
      nativeLibraryValue.abi = abiName
      nativeLibraryValue.buildCommandComponents = baseNinjaCommand + target.name
      nativeLibraryValue.artifactName = target.name
      nativeLibraryValue.buildType = if (isDebuggableEnabled) "debug" else "release"
      // If the target type is an OBJECT_LIBRARY, it does not build any output that we care.
      if (
        "OBJECT_LIBRARY" != target.type // artifacts of an object library could be null. See example in b/144938511
        && target.artifacts != null && target.artifacts.isNotEmpty()
      ) {
        // We'll have only one output, so get the first one.
        nativeLibraryValue.output = File(target.artifacts[0])
      }
      nativeLibraryValue.runtimeFiles = target.findRuntimeFiles()

      val sourceDirectory = File(target.sourceDirectory)
      for (fileGroup in target.fileGroups.orEmpty()) {
        if (fileGroup.language == "C" || fileGroup.language == "CXX") {
          // Skip C and CXX since these are contained in compile_commands.json already.
          continue
        }
        for (source in fileGroup.sources) {
          additionalProjectFilesIndexWriter.appendLine(sourceDirectory.resolve(source).absolutePath)
        }
      }
      return nativeLibraryValue
    }

    /** Helper function that returns true if the Target object is valid to be added to native library. */
    private fun canAddTargetToNativeLibrary(target: Target): Boolean {
      // If the target has artifact, then we need to build it.
      // If the target has files, then we need it for intellisense.
      return target.artifacts != null || target.fileGroups != null
    }

    /**
     * Creates a default NativeBuildConfigValue.
     *
     * @return a default NativeBuildConfigValue.
     */
    private fun createDefaultNativeBuildConfigValue(): NativeBuildConfigValue {
      val nativeBuildConfigValue = NativeBuildConfigValue()
      nativeBuildConfigValue.buildFiles = ArrayList()
      nativeBuildConfigValue.cleanCommandsComponents = ArrayList()
      nativeBuildConfigValue.libraries = HashMap()
      nativeBuildConfigValue.toolchains = HashMap()
      nativeBuildConfigValue.cFileExtensions = ArrayList()
      nativeBuildConfigValue.cppFileExtensions = ArrayList()
      nativeBuildConfigValue.stringTable = Maps.newHashMap()
      return nativeBuildConfigValue
    }

    /**
     * Returns the normalized path for the given file. The normalized path for Unix is the default string returned by getPath. For Microsoft
     * Windows, getPath returns a path with "\\" (example: "C:\\Android\\Sdk") while Vanilla-CMake prefers a forward slash (example
     * "C:/Android/Sdk"), without the forward slash, CMake would mix backward slash and forward slash causing compiler issues. This function
     * replaces the backward slashes with forward slashes for Microsoft Windows.
     */
    private fun normalizeFilePath(file: File): String {
      return if (isWindows) {
        file.path.replace("\\", "/")
      } else file.path
    }
  }
}
