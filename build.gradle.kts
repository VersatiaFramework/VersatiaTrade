import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import org.yaml.snakeyaml.Yaml

// SnakeYAML parses plugin.yml in checkPluginYml
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.snakeyaml)
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.shadow)
    `maven-publish`
}

group = "dev.versatia"
version = "0.0.1"

// VersatiaAPI brings the Paper API and Kotlin, for compilation only: on the server these classes come
// from VersatiaCore (see plugin.yml). InvUI is shaded into the plugin JAR and relocated; see docs/build.md
dependencies {
    compileOnly(libs.versatia.api)
    ksp(libs.versatia.ksp)
    implementation(libs.invui)
    // The CommandAPI plugin is a soft dependency on the server; only its Kotlin DSL ships in this JAR
    compileOnly(libs.commandapi.paper.core)
    implementation(libs.commandapi.kotlin.paper)
    // Soft dependencies on the server: VersatiaInteractions adds the menu button, VaultUnlocked (or Vault) the money
    compileOnly("dev.versatia:VersatiaInteractions:${providers.gradleProperty("versatiaInteractionsVersion").get()}")
    compileOnly(libs.vaultunlocked.api)

    testImplementation(kotlin("test"))
    testImplementation(libs.versatia.api)
    // MockBukkit leaves the Paper API to the test runtime classpath
    testImplementation(libs.paper.api)
    testImplementation(libs.mockbukkit)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Paper 26.3 requires Java 25
java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

tasks.test {
    useJUnitPlatform()
}

// Expands ${version} in plugin.yml
tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// Paper skips a plugin whose plugin.yml does not parse; fail the build instead
val checkPluginYml = tasks.register("checkPluginYml") {
    group = "verification"
    description = "Checks that plugin.yml parses and names the plugin, its main class and VersatiaCore, and that there is no paper-plugin.yml."
    val pluginYml = layout.buildDirectory.file("resources/main/plugin.yml")
    val paperPluginYml = layout.projectDirectory.file("src/main/resources/paper-plugin.yml")
    dependsOn(tasks.processResources)
    inputs.files(pluginYml, paperPluginYml)
    doLast {
        val paperFile = paperPluginYml.asFile
        if (paperFile.exists())
            throw GradleException("Versatia plugins use plugin.yml; remove paper-plugin.yml ($paperFile). Paper prefers it to plugin.yml and loads the JAR as a Paper plugin, whose isolated class loader does not share VersatiaCore's Kotlin and VersatiaAPI classes the way depend: does. See https://versatia.dev/docs/getting-started/installation#pluginyml")
        val file = pluginYml.get().asFile
        if (!file.exists()) throw GradleException("$file is missing; add src/main/resources/plugin.yml")
        val root = file.reader().use { Yaml().load<Any?>(it) } as? Map<*, *>
            ?: throw GradleException("$file is not a YAML mapping")
        val missing = listOf("name", "version", "main").filter { root[it] == null }
        if (missing.isNotEmpty()) throw GradleException("$file lacks $missing")
        if ("VersatiaCore" !in (root["depend"] as? List<*>).orEmpty())
            throw GradleException("$file must list VersatiaCore under depend:")
    }
}

tasks.check {
    dependsOn(checkPluginYml)
}

// The plain JAR holds this plugin's classes only: the artifact plugins listening to its events compile against
tasks.jar {
    archiveClassifier = "plain"
}

// The plugin JAR: this plugin's classes plus InvUI, relocated so that every plugin keeps its own copy; see docs/build.md
tasks.shadowJar {
    archiveClassifier = ""
    // Lets Shadow's Kotlin module transformer see every module file; nothing else is duplicated
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    dependencies {
        include(dependency("xyz.xenondevs.invui:invui"))
        include(dependency("dev.jorel:commandapi-kotlin-.*"))
    }
    relocate("xyz.xenondevs.invui", "dev.versatia.trade.libs.invui")
    // InvUI is compiled against Mojang-mapped server internals, so Paper must not remap the JAR
    manifest {
        attributes("paperweight-mappings-namespace" to "mojang")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "VersatiaTrade"
            artifact(tasks.jar) { classifier = null }
            artifact(tasks.named("sourcesJar"))
        }
    }
}

// Machine-specific settings; see README.md
val localProperties = providers
    .fileContents(layout.projectDirectory.file("local.properties"))
    .asText
    .map { text -> Properties().apply { load(text.reader()) } }

val pluginsDir: Provider<String> = providers.gradleProperty("pluginsDir")
    .orElse(localProperties.map { it.getProperty("pluginsDir") })

val deployPlugin = tasks.register<Copy>("deployPlugin") {
    group = "versatia"
    description = "Copies the plugin JAR to the server plugins/ directory (pluginsDir from local.properties)."
    // Local copy: the onlyIf lambda must not capture script fields (configuration cache)
    val targetDir = pluginsDir
    onlyIf("pluginsDir is not set - see local.properties.example") { targetDir.isPresent }
    from(tasks.shadowJar)
    // Without the version, a newer build replaces the deployed JAR instead of sitting next to it
    rename { "VersatiaTrade.jar" }
    into(targetDir.orElse(layout.buildDirectory.dir("deploy").map { it.asFile.path }))
}

tasks.build {
    finalizedBy(deployPlugin)
}
