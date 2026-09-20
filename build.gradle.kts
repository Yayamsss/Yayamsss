plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
    application
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    // Embeddable GraalJS — runs on a plain JDK 21, no GraalVM JDK required.
    implementation("org.graalvm.polyglot:polyglot:24.1.1")
    implementation("org.graalvm.polyglot:js-community:24.1.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jsoup:jsoup:1.18.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    implementation(compose.desktop.currentOs)
}

kotlin {
    jvmToolchain(17)
}

application {
    // Compose Desktop UI is the default entry point; the original CLI POC is
    // still available via the `runCli` task below.
    mainClass.set("lnreader.ui.AppKt")
}

tasks.register<JavaExec>("runCli") {
    group = "application"
    description = "Runs the original CLI POC (manifest -> plugin -> popularNovels -> parseNovel -> parseChapter)."
    mainClass.set("lnreader.cli.CliKt")
    classpath = sourceSets["main"].runtimeClasspath
    if (project.hasProperty("args")) {
        args((project.property("args") as String).split(" "))
    }
}

tasks.test {
    useJUnitPlatform()
}
