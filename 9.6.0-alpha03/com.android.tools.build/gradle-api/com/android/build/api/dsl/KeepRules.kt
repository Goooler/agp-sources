package com.android.build.api.dsl

import java.io.File
import org.gradle.api.Incubating
import org.gradle.api.provider.SetProperty
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition

/** DSL object for external library dependencies keep rules configurations. */
interface KeepRules {

  @Incubating
  @Deprecated("Renamed to ignoreFrom", replaceWith = ReplaceWith("ignoreFrom"))
  fun ignoreExternalDependencies(vararg ids: String)

  /**
   * Ignore keep rules from listed external dependencies. External dependencies can be specified via GAV coordinates(e.g.
   * "groupId:artifactId:version") or in the format of "groupId:artifactId" in which case dependencies are ignored as long as they match
   * groupId & artifactId.
   */
  fun ignoreFrom(vararg ids: String)

  @Incubating
  @Deprecated("Renamed to ignoreFromAllExternalDependencies", replaceWith = ReplaceWith("ignoreFromAllExternalDependencies"))
  fun ignoreAllExternalDependencies(ignore: Boolean)

  /** Ignore keep rules from all the external dependencies. */
  @Incubating fun ignoreFromAllExternalDependencies(ignore: Boolean)

  /** Ignore keep rules from all the external dependencies. */
  var ignoreFromAllExternalDependencies: Boolean

  /** Keep rules files set */
  @get:HiddenInDefinition @get:Incubating @Deprecated("Use keepRules source folder instead") val files: SetProperty<File>

  /**
   * This flag will include default keep rules that enables shrinking, obfuscation, and optimization of bytecode when `optimization.enable`
   * is on.
   *
   * Default value is true. When merging the values from build types and product flavors, if any have this as false, it will be false in the
   * eventual variant.
   *
   * It's the equivalent of using `getDefaultProguardFile("proguard-android-optimize.txt")`
   */
  var includeDefault: Boolean
}
