plugins {
    kotlin("jvm") version "1.9.23"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "org.ldemetrios"
version = "0.1"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.xenomachina:kotlin-argparser:+")
    implementation("org.ldemetrios:typst4k:+")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveFileName.set("${project.name}-${project.version}.jar")
    mergeServiceFiles()
    manifest {
        attributes(mapOf("Main-Class" to "org.ldemetrios.escape.CLIKt"))
    }
}
