import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories { mavenCentral() }

    dependencies {
        val kotlinVersion = "1.8.0"
        classpath(kotlin("gradle-plugin", version = kotlinVersion))
        classpath(kotlin("serialization", version = kotlinVersion))
    }
}

application {
    mainClassName = "com.tgbt.MainKt"
}

plugins {
    val kotlinVersion = "1.8.0"
    application
    kotlin("jvm").version(kotlinVersion)
    kotlin("plugin.serialization").version(kotlinVersion)
    id("com.github.johnrengelman.shadow") version "7.1.2"
}
repositories {
    mavenCentral()
    jcenter()
    maven("https://jitpack.io")
}

group = "com.tgbt"
version = "0.2"

repositories {
    mavenCentral()
}

val betterParseVersion = "0.4.4"
val serializationVersion = "1.4.1"
val ktorVersion = "2.2.1"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {

    implementation(kotlin("stdlib-jdk8"))
    implementation("com.github.h0tk3y.betterParse:better-parse-jvm:$betterParseVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-jetty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-json-jvm:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.4.5")
    implementation("org.postgresql:postgresql:42.5.1")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("com.vladsch.kotlin-jdbc:kotlin-jdbc:0.5.2")
    implementation("io.ktor:ktor-server-default-headers-jvm:2.2.1")
    implementation("io.ktor:ktor-server-call-logging-jvm:2.2.1")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_11.toString()
        }
    }

    named<ShadowJar>("shadowJar") {
        archiveFileName.set("tgbtbot.jar")
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "com.tgbt.MainKt"))
        }
    }

    register("stage") {
        dependsOn("shadowJar")
    }
}