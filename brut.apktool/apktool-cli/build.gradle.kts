val gitRevision = rootProject.extra["gitRevision"] as String
val apktoolVersion = rootProject.extra["apktoolVersion"] as String
val r8: Configuration = configurations.create("r8")

plugins {
    application
}

dependencies {
    implementation(project(":brut.apktool:apktool-lib"))
    implementation(libs.commons.cli)
    // Expose the smali/baksmali command line tools through `apktool smali` / `apktool baksmali`.
    implementation(libs.smali)
    implementation(libs.baksmali)
    r8(libs.r8)
}

application {
    mainClass.set("brut.apktool.Main")

    tasks.run.get().workingDir = file(System.getProperty("user.dir"))
}

tasks {
    processResources {
        from("src/main/resources") {
            include("apktool.properties")
            expand("version" to apktoolVersion, "gitrev" to gitRevision)
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        includeEmptyDirs = false
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.register<Delete>("cleanOutputDirectory") {
    delete(fileTree("build/libs") {
        exclude("apktool-cli-sources.jar")
        exclude("apktool-cli-javadoc.jar")
        exclude("apktool-cli-all.jar")
    })
}

val shadowJar = tasks.register("shadowJar", Jar::class) {
    dependsOn("build")
    dependsOn("cleanOutputDirectory")

    group = "build"
    description = "Creates a single executable JAR with all dependencies"
    manifest.attributes["Main-Class"] = "brut.apktool.Main"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Resolve the runtime classpath lazily: doing it while the build is still being
    // configured breaks with the included `smali` build (composite dependency substitution).
    from(configurations.runtimeClasspath.map { classpath -> classpath.map(::zipTree) })
    with(tasks.jar.get())
}

tasks.register<JavaExec>("proguard") {
    dependsOn(shadowJar)

    val proguardRules = file("proguard-rules.pro")
    val originalJar = shadowJar.flatMap { it.archiveFile }
    val outputJar = layout.buildDirectory.file("libs/apktool_$apktoolVersion.jar")

    inputs.file(originalJar)
    inputs.file(proguardRules)
    outputs.file(outputJar)

    classpath(r8)
    mainClass.set("com.android.tools.r8.R8")

    doFirst {
        args = listOf(
            "--release",
            "--classfile",
            "--no-minification",
            "--map-diagnostics:UnusedProguardKeepRuleDiagnostic", "info", "none",
            "--lib", javaLauncher.get().metadata.installationPath.toString(),
            "--output", outputJar.get().asFile.absolutePath,
            "--pg-conf", proguardRules.absolutePath,
            originalJar.get().asFile.absolutePath
        )
    }
}
