// Top-level build file. Plugin versions are declared here and applied (without version)
// in app/build.gradle.kts. Keeping versions pinned in one place avoids the version-drift
// problems that cause most first-open Gradle sync failures.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
