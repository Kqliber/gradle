
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.5.10"
    `java-gradle-plugin`
    `maven-publish`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "0.14.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "me.mattstudios"
version = "0.2.0.1"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.mattstudios.me/artifactory/public")
    maven("https://jitpack.io")
}

val shadowImplementation: Configuration by configurations.creating
configurations["compileOnly"].extendsFrom(shadowImplementation)
configurations["testImplementation"].extendsFrom(shadowImplementation)

dependencies {
    shadowImplementation(kotlin("stdlib"))
    shadowImplementation("org.ow2.asm:asm:9.1")
    shadowImplementation("org.ow2.asm:asm-tree:9.1")
    shadowImplementation("org.ow2.asm:asm-util:9.1")
    shadowImplementation("org.ow2.asm:asm-commons:9.1")
    shadowImplementation("com.google.code.gson:gson:2.8.6")

    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.10")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.1.10")

    shadowImplementation("dev.triumphteam:triumph-gradle-annotations:0.0.1")
    shadowImplementation("org.yaml:snakeyaml:1.29")
}

val shadowJarTask = tasks.named("shadowJar", ShadowJar::class.java)

shadowJarTask.configure {
    archiveClassifier.set("")
    configurations = listOf(shadowImplementation)
}

// Required for plugin substitution to work in samples project
artifacts {
    add("runtimeOnly", shadowJarTask)
}

tasks.whenTaskAdded {
    if (name == "publishPluginJar" || name == "generateMetadataFileForPluginMavenPublication") {
        dependsOn(tasks.named("shadowJar"))
    }
}

// Disabling default jar task as it is overridden by shadowJar
tasks.named("jar").configure {
    enabled = false
}

val ensureDependenciesAreInlined by tasks.registering {
    description = "Ensures all declared dependencies are inlined into shadowed jar"
    group = HelpTasksPlugin.HELP_GROUP
    dependsOn(tasks.shadowJar)

    doLast {
        val nonInlinedDependencies = mutableListOf<String>()
        zipTree(tasks.shadowJar.flatMap { it.archiveFile }).visit {
            if (!isDirectory) {
                val path = relativePath
                if (
                    !path.startsWith("META-INF") &&
                    path.lastName.endsWith(".class")
                ) {
                    nonInlinedDependencies.add(path.pathString)
                }
            }
        }
        if (nonInlinedDependencies.isNotEmpty()) {
            throw GradleException("Found non inlined dependencies: $nonInlinedDependencies")
        }
    }
}

tasks.named("check") {
    dependsOn(ensureDependenciesAreInlined)
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
        }
    }

    withType<ShadowJar> {
        mapOf(
            "me.mattstudios" to "matt",
            "com.google" to "google",
            "net.md_5" to "what",
            "org.bukkit" to "org"
        ).forEach { relocate(it.key, "dev.triumphteam.lib.${it.value}") }
    }

    test {
        useJUnitPlatform()
    }
}

// Work around publishing shadow jars
afterEvaluate {
    publishing {
        publications {
            withType<MavenPublication> {
                if (name == "pluginMaven") {
                    setArtifacts(listOf(shadowJarTask.get()))
                }
            }
        }
    }
}


gradlePlugin {
    plugins {
        create("triumph") {
            id = "me.mattstudios.triumph"
            displayName = "Triumph Gradle"
            description = "Plugin with utilities for the Triumph projects."
            implementationClass = "dev.triumphteam.TriumphPlugin"
        }
    }
}

pluginBundle {
    website = "https://mf.mattstudios.me"
    vcsUrl = "https://github.com/TriumphDev/gradle"
    tags = listOf("spigot", "bukkit", "minecraft")
}