import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.github.johnrengelman.shadow") version "4.0.4"
}

group = "com.tridevmc.atlas"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.guava:guava:28.1-jre")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("org.tinylog:tinylog:1.3.6")
    implementation("org.ow2.asm:asm:8.0.1")
    implementation("org.ow2.asm:asm-commons:8.0.1")

    testCompile("junit", "junit", "4.12")
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "com.tridevmc.atlas.Atlas")
    }
}

tasks {
    named<ShadowJar>("shadowJar") {
        archiveBaseName.set("shadow")
        mergeServiceFiles()
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}