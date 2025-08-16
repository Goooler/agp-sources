@file:Suppress("UnstableApiUsage")

import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version embeddedKotlinVersion
}

/**
 * 1) Update AGP versions in gradle/xx.versions.toml
 * 2) Run './gradlew dumpSources'
 * 3) Check changeset into source control
 */

// Match all directories that look like version numbers, e.g. 8.11.1, 8.13.0-alpha02.
val versionDirPattern = """
  ^\d+\.\d+\.\d+(-alpha\d+)?$
""".trimIndent().toRegex()

rootDir.listFiles().orEmpty()
  .filter { it.isDirectory && versionDirPattern.matches(it.name) }
  .forEach { dir ->
    sourceSets.register(dir.name) {
      java.srcDir(dir)
    }
  }

val shared by configurations.registering {
  isCanBeResolved = true
  description = "Shared configuration for all source sets."
}

configurations.configureEach {
  // Share dependencies between all source sets.
  if (name != shared.name) {
    extendsFrom(shared.get())
  }
}

dependencies {
  shared(gradleApi())

  // Add all AGP dependencies but the AGP itself.
  configurations.detachedConfiguration(dependencies.create(stable.agp.get()))
    .resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
      val id = artifact.moduleVersion.id
      if (artifact.moduleVersion.id.group.startsWith("com.android")) return@forEach
      shared("${id.group}:${id.name}:${id.version}")
    }
}

tasks.withType<KotlinCompile>().configureEach {
  // We need KGP to configure Kotlin stuff, the source sets can't be compiled successfully.
  enabled = false
}

// Anchor task.
val dumpSources by tasks.registering

// https://mvnrepository.com/artifact/com.android.tools.build/gradle
listOf(
  stable.agp,
  alpha.agp,
).forEach { agp ->
  val agpVersion = requireNotNull(agp.get().version)
  // Create configuration for specific version of AGP.
  val agpConfiguration = configurations.create("agp$agpVersion") {
    // TODO: https://github.com/google/guava/issues/6801
    //  Fix `Cannot choose between the following variants of com.google.guava:guava:33.3.1-jre: androidRuntimeElements, jreRuntimeElements`.
    attributes {
      attribute(
        TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
        objects.named<TargetJvmEnvironment>(TargetJvmEnvironment.STANDARD_JVM),
      )
    }
  }

  // Add that version of AGP as a dependency to this configuration.
  agpConfiguration.dependencies.add(
    dependencies.create(agp.get())
  )

  // Create a task dedicated to extracting sources for that version.
  val agpDumpSources = tasks.register<Copy>("dump${agpVersion}Sources") {
    inputs.files(agpConfiguration)
    into(agpVersion)

    val componentIds = agpConfiguration
      .incoming
      .resolutionResult
      .allDependencies
      .filterIsInstance<DefaultResolvedDependencyResult>()
      .filter { (it.selected.id as ModuleComponentIdentifier).group.startsWith("com.android.tools") }
      .map { it.selected.id }
      .toSet()

    val result = dependencies.createArtifactResolutionQuery()
      .forComponents(componentIds)
      .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java)
      .execute()

    result.resolvedComponents.forEach { component ->
      component.getArtifacts(SourcesArtifact::class.java)
        .filterIsInstance<ResolvedArtifactResult>()
        .forEach { ar ->
          logger.lifecycle("Found ${ar.file}.")
          val id = ar.id.componentIdentifier as ModuleComponentIdentifier
          val group = id.group
          val module = id.module
          from(zipTree(ar.file)) {
            into("$group/$module")
          }
        }
    }
  }

  // Hook anchor task to all version-specific tasks.
  dumpSources.configure {
    dependsOn(agpDumpSources)
  }
}
