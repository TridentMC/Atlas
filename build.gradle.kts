import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.publish.PublishingExtension

plugins {
    java
    id("com.github.johnrengelman.shadow") version "4.0.4"
}

group = "com.tridevmc.atlas"
version = "0.0.1"
ext {
    set("versionTag", "")
}

repositories {
    mavenCentral()
    maven(url = "https://repo.tridevmc.com/")
}

dependencies {
    compile("com.google.guava:guava:28.1-jre")
    compile("com.google.code.gson:gson:2.8.6")
    compile("org.tinylog:tinylog:1.3.6")
    compile("org.ow2.asm:asm:8.0.1")
    compile("org.ow2.asm:asm-commons:8.0.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.3.1")
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "com.tridevmc.atlas.Atlas")
    }
}

val buildSlim = tasks.register<Jar>("buildSlim") {
    archiveBaseName.set("atlas")
}

val buildFull = tasks.register<ShadowJar>("buildFull") {
    archiveBaseName.set("atlas-full")
    mergeServiceFiles()
    from(project.sourceSets.main.get().output + project.configurations.runtimeClasspath)

    relocate("com.google", "com.tridevmc.atlas.repack.com.google")
    relocate("org.pmw", "com.tridevmc.atlas.repack.org.pmw")
    relocate("org.objectweb", "com.tridevmc.atlas.repack.org.objectweb")
}

tasks.build {
    dependsOn(buildSlim, buildFull)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    workingDir = File("test")
    workingDir.mkdir()
}

if (file("private.gradle").exists()) {
    apply(plugin = "maven-publish")
    artifacts {
        add("archives", buildSlim)
        add("archives", buildFull)
    }
    val artifactSlim = artifacts.add("archives", buildSlim)
    val artifactFull = artifacts.add("archives", buildFull)
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("atlas-slim") {
                groupId = project.group as String
                artifactId = "atlas-slim"
                version = project.version as String + project.ext.get("versionTag")
                artifact(artifactSlim)
            }
            create<MavenPublication>("atlas-full") {
                groupId = project.group as String
                artifactId = "atlas-full"
                version = project.version as String + project.ext.get("versionTag")
                artifact(artifactFull)
            }
        }
    }
    apply(from = "private.gradle")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}