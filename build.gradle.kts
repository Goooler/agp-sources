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

val defaultAgpDependencies = configurations.register("defaultAgpDependencies") {
  dependencies.add(final.agp.get())
  resolutionStrategy.activateDependencyLocking()
}

dependencies {
  compileOnly(gradleApi())
  compileOnly(final.bundletool)

  defaultAgpDependencies.get()
    .incoming
    .artifactView {
      componentFilter { id ->
        // Add all AGP dependencies but the AGP itself.
        !(id as ModuleComponentIdentifier).group.startsWith(agpGroupPrefix)
      }
    }.files.let(::compileOnly)
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
    inputSources = configuration.incoming
      .artifactView {
        withVariantReselection()
        attributes {
          attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
          attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
          attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
          attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
        }
        componentFilter { id ->
          (id as ModuleComponentIdentifier).group.startsWith(agpGroupPrefix)
        }
        lenient(true)
      }
      .artifacts.resolvedArtifacts
      .map { artifacts ->
        artifacts.map { artifact ->
          val id = artifact.id.componentIdentifier as ModuleComponentIdentifier
          DumpSources.Resolved(
            group = id.group,
            module = id.module,
            file = artifact.file,
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

  /**
   * Serializable copy of [ResolvedArtifactResult] for CC support.
   */
  data class Resolved(
    @get:Input val group: String,
    @get:Input val module: String,
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) val file: File,
  ) : Serializable
}
