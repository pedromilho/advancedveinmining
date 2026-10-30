import java.util.Properties

plugins { java }

group = "br.pedro.hytale"
version = "0.5.0"

java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }

val local = Properties().apply {
    val config = rootProject.file("local.properties")
    if (config.exists()) config.inputStream().use { load(it) }
}
val hytaleHome = providers.gradleProperty("hytaleHome")
    .orElse(providers.environmentVariable("HYTALE_HOME"))
    .orElse(local.getProperty("hytale.home", "D:/Games/Hytale"))
val serverJar = file("${hytaleHome.get()}/install/release/package/game/latest/Server/HytaleServer.jar")

dependencies { compileOnly(files(serverJar)) }

tasks.compileJava {
    doFirst {
        check(serverJar.isFile) { "HytaleServer.jar ausente: $serverJar. Configure hytale.home em local.properties." }
    }
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("manifest.json") { expand("version" to project.version) }
}

// Testes de comportamento sem dependência do servidor ou de bibliotecas externas.
val verifyVeinTraversal by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("br.pedro.hytale.veinminer.VeinTraversalTest")
}
tasks.check { dependsOn(verifyVeinTraversal) }

val verifySettings by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("br.pedro.hytale.veinminer.SettingsTest")
}
tasks.check { dependsOn(verifySettings) }
tasks.test {
    // Os cenários são executados pelo JavaExec acima, sem runner JUnit.
    failOnNoDiscoveredTests.set(false)
    dependsOn(verifyVeinTraversal, verifySettings)
}
