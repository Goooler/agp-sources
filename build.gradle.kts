@file:Suppress("UnstableApiUsage", "ConvertCallChainIntoSequence")

import java.io.Serializable

plugins {
  kotlin("jvm") version embeddedKotlinVersion
}

val agpGroupPrefix = "com.android.tools"

// Match all directories that look like version numbers, e.g. 8.11.1, 8.13.0-alpha02, and 8.13.0-rc02.
val versionDirPattern = """
  ^\d+\.\d+\.\d+(-(?:alpha|rc)\d+)?$
""".trimIndent().toRegex()

val compileOnly = configurations.compileOnly

rootDir.listFiles().orEmpty()
  .filter { it.isDirectory && versionDirPattern.matches(it.name) }
  .forEach { dir ->
    sourceSets.register(dir.name) {
      java.srcDir(dir)
      configurations.named(compileOnlyConfigurationName) {
        extendsFrom(compileOnly)
      }
    }
  }

dependencies {
  compileOnly(gradleApi())

  // Add all AGP dependencies but the AGP itself.
  configurations.detachedConfiguration(create(final.agp.get()))
    .resolvedConfiguration.resolvedArtifacts
    .map { it.moduleVersion.id }
    .filterNot { it.group.startsWith(agpGroupPrefix) }
    .map(ModuleVersionIdentifier::toString)
    .sorted()
    .forEach { notation ->
      logger.lifecycle("Compile only on: $notation")
      compileOnly(notation)
    }
}

// Anchor task.
val dumpAgpSources = tasks.register("dumpAgpSources") {
  group = "documentation"
  description = "Dumps given AGP sources"
}

// https://mvnrepository.com/artifact/com.android.tools.build/gradle
listOf(
  alpha.agp,
  rc.agp,
  final.agp,
).forEach { agp ->
  val dependency = agp.get()
  val version = requireNotNull(dependency.version)
  val configuration = configurations.create("agp-$version") { dependencies.add(dependency) }

  val dumpSources = tasks.register<DumpSources>("dump-$version-sources") {
    group = "documentation"
    description = "Dumps AGP $version sources into the output directory"

    outputDirectory = layout.projectDirectory.dir(version)
    inputSources = provider {
      val componentIds = configuration
        .incoming
        .resolutionResult
        .allDependencies
        .filterIsInstance<ResolvedDependencyResult>()
        .map { it.selected.id }
        .filterIsInstance<ModuleComponentIdentifier>()
        .filter { it.group.startsWith(agpGroupPrefix) }
        .toSet()

      dependencies
        .createArtifactResolutionQuery()
        .forComponents(componentIds)
        .withArtifacts(JvmLibrary::class, SourcesArtifact::class)
        .execute()
        .resolvedComponents
        .flatMap { it.getArtifacts(SourcesArtifact::class) }
        .filterIsInstance<ResolvedArtifactResult>()
        .map {
          val id = it.id.componentIdentifier as ModuleComponentIdentifier
          DumpSources.Resolved(
            group = id.group,
            module = id.module,
            file = it.file,
          )
        }
    }
  }

  // Hook anchor task to all version-specific tasks.
  dumpAgpSources {
    dependsOn(dumpSources)
  }
}

/**
 * Replacement of [Copy], which defers the source and destination configurations.
 */
@CacheableTask
abstract class DumpSources @Inject constructor(
  private val archiveOperations: ArchiveOperations,
  private val fileSystemOperations: FileSystemOperations,
) : DefaultTask() {
  @get:Input
  abstract val inputSources: ListProperty<Resolved>

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun dump() {
    inputSources.get().forEach { resolved ->
      logger.lifecycle("Extracting: $resolved")

      fileSystemOperations.copy {
        // There should be no duplicates in sources, so fail if any are found.
        duplicatesStrategy = DuplicatesStrategy.FAIL

        from(archiveOperations.zipTree(resolved.file))
        into(outputDirectory.get().asFile.resolve("${resolved.group}/${resolved.module}"))
      }
    }
  }

  /**
   * Serializable copy of [ResolvedArtifactResult] for CC support.
   */
  data class Resolved(
    val group: String,
    val module: String,
    val file: File,
  ) : Serializable
}
