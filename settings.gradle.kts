@file:Suppress("UnstableApiUsage")

pluginManagement {
  repositories {
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
  }
  versionCatalogs {
    create("alpha") {
      from(files("gradle/alpha.versions.toml"))
    }
    create("beta") {
      from(files("gradle/beta.versions.toml"))
    }
    create("rc") {
      from(files("gradle/rc.versions.toml"))
    }
    create("final") {
      from(files("gradle/final.versions.toml"))
    }
  }
}
