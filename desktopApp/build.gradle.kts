plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    application
}

dependencies {
    implementation(project(":composeApp"))
    implementation(compose.desktop.currentOs)
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("lnreader.desktop.MainKt")
}

tasks.register<JavaExec>("runCli") {
    group = "application"
    description = "Runs the LNReader CLI flow (manifest -> plugin -> popularNovels -> parseNovel -> parseChapter)."
    mainClass.set("lnreader.cli.CliKt")
    classpath = sourceSets["main"].runtimeClasspath
    if (project.hasProperty("args")) {
        args((project.property("args") as String).split(" "))
    }
}
