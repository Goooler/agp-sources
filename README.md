# agp-sources

Contains the per-release bundled sources to the Android Gradle Plugin, useful for general Android development and
pointing to open Google issues.

Want latest sources? Check out Google's main [dev branch][agp_main]!

## Updating sources

To pull a new version:

1. Update AGP versions in `gradle/*.versions.toml`
2. Run
    ```sh
    ./gradlew dumpAgpSources
    ```
3. Check the changeset into source control.

The `dumpAgpSources` Gradle task will automatically download AGP and its transitive dependencies from
the Google repository and unzip them from your local Gradle cache directory.

From there, use your favorite diff tool to easily examine changes across versions:

![Diff example](/images/agp-diff.webp)

[agp_main]: https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-master-dev/build-system/
