@file:Suppress("UnstableApiUsage", "ConvertCallChainIntoSequence")

import java.io.Serializable

plugins {
  kotlin("jvm") version embeddedKotlinVersion
}

val agpGroupPrefix = "com.android.tools"
val kotlinGroup = "org.jetbrains.kotlin"

// Match all directories that look like version numbers, e.g. 8.11.1, 8.13.0-alpha02, 9.0.0-beta01, and 8.13.0-rc02.
val versionDirPattern = """
  ^\d+\.\d+\.\d+(-(?:alpha|beta|rc)\d+)?$
""".trimIndent().toRegex()

rootDir.listFiles().orEmpty()
  .filter { it.isDirectory && versionDirPattern.matches(it.name) }
  .forEach { dir ->
    sourceSets.register(dir.name) {
      java.srcDir(dir)
    }
  }

val compileOnly by configurations.getting

configurations.configureEach {
  if (name != compileOnly.name) {
    // Share compileOnly dependencies for all source sets.
    extendsFrom(compileOnly)
  }
  resolutionStrategy.eachDependency {
    if (requested.group == kotlinGroup) {
      useVersion(embeddedKotlinVersion)
    }
  }
}

dependencies {
  compileOnly(gradleApi())
  compileOnly(kotlin("gradle-plugin"))

  // Add all AGP dependencies but the AGP itself.
  configurations.detachedConfiguration(create(final.agp.get()))
    .resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
      with(artifact.moduleVersion.id) {
        if (group.startsWith(agpGroupPrefix)) return@forEach
        compileOnly("$group:$name:$version")
      }
    }
}

// Anchor task.
val dumpSources by tasks.registering

// https://mvnrepository.com/artifact/com.android.tools.build/gradle
listOf(
  alpha.agp,
  beta.agp,
  rc.agp,
  final.agp,
).forEach { agp ->
  val dependency = agp.get()
  val version = requireNotNull(dependency.version)
  val configuration = configurations.create("agp$version}") { dependencies.add(dependency) }

  val dumpSingleAgpSources = tasks.register<DumpSingleAgpSources>("dump${version}Sources") {
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
          Resolved(
            group = id.group,
            module = id.module,
            file = it.file,
          )
        }
    }
  }

  // Hook anchor task to all version-specific tasks.
  dumpSources {
    dependsOn(dumpSingleAgpSources)
  }
}

/**
 * Serializable copy of [ResolvedArtifactResult] for CC support.
 */
data class Resolved(
  @Input val group: String,
  @Input val module: String,
  @InputFile @get:PathSensitive(PathSensitivity.RELATIVE) val file: File,
) : Serializable

/**
 * Replacement of [Copy], which defers the source and destination configurations.
 */
@CacheableTask
abstract class DumpSingleAgpSources : DefaultTask() {
  @get:Inject
  protected abstract val archiveOperations: ArchiveOperations

  @get:Inject
  protected abstract val fileSystemOperations: FileSystemOperations

  @get:Nested
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
}
