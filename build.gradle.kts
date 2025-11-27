@file:Suppress("UnstableApiUsage", "ConvertCallChainIntoSequence")

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
  val agpVersion = requireNotNull(agp.get().version)
  // Create configuration for specific version of AGP.
  val agpConfiguration = configurations.create("agp$agpVersion")
  // Add that version of AGP as a dependency to this configuration.
  agpConfiguration.dependencies.add(dependencies.create(agp.get()))

  // Create a task dedicated to extracting sources for that version.
  val dumpSingleAgpSources = tasks.register<Copy>("dump${agpVersion}Sources") {
    inputs.files(agpConfiguration)
    into(layout.projectDirectory.dir(agpVersion))
    // There should be no duplicates in sources, so fail if any are found.
    duplicatesStrategy = DuplicatesStrategy.FAIL

    val componentIds = agpConfiguration
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
      .forEach {
        logger.lifecycle("Found sources jar: ${it.file}")
        val id = it.id.componentIdentifier as ModuleComponentIdentifier
        from(zipTree(it.file)) {
          into("${id.group}/${id.module}")
        }
      }
  }

  // Hook anchor task to all version-specific tasks.
  dumpSources {
    dependsOn(dumpSingleAgpSources)
  }
}
