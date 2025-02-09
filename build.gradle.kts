plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("application")
}

group = "me.m0dii"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    implementation("com.github.mwiede:jsch:0.2.23")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
}

application {
    mainClass.set("me.m0dii.LogViewerAppKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "me.m0dii.LogViewerAppKt"
    }

    from(sourceSets.main.get().output) // Include compiled classes
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("log-viewer-fat")
    manifest {
        attributes["Main-Class"] = "me.m0dii.LogViewerAppKt"
    }
    from(sourceSets.main.get().output)

    // Handle duplicate META-INF files
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.exists() }.map { zipTree(it) }
    })
}

