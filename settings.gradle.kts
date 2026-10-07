plugins {
    // Allows Gradle to auto-download the JDK requested via -PtestJdkVersion.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "apktool-cli"
include(
    "brut.j.common", "brut.j.util", "brut.j.dir", "brut.j.xml", "brut.j.yaml",
    "brut.apktool:apktool-lib", "brut.apktool:apktool-cli"
)

// Build smali/baksmali from the `smali` git submodule (ksmali) instead of pulling
// prebuilt artifacts. The submodule publishes under the official Maven coordinates
// (com.android.tools.smali), so we map those modules onto its projects.
includeBuild("smali") {
    dependencySubstitution {
        substitute(module("com.android.tools.smali:smali")).using(project(":smali"))
        substitute(module("com.android.tools.smali:smali-baksmali")).using(project(":baksmali"))
        substitute(module("com.android.tools.smali:smali-dexlib2")).using(project(":dexlib2"))
        substitute(module("com.android.tools.smali:smali-util")).using(project(":util"))
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {}
    }
}
