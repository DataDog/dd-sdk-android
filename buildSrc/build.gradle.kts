import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    id("com.github.ben-manes.versions") version ("0.27.0")
    id("org.jlleitschuh.gradle.ktlint") version ("9.1.0")
}

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.41")
    }
    repositories {
        mavenCentral()
    }
}

apply(plugin = "kotlin")
apply(plugin = "java-gradle-plugin")

repositories {
    mavenCentral()
    google()
    maven { setUrl("https://plugins.gradle.org/m2/") }
    maven { setUrl("https://maven.google.com") }
}

dependencies {

    // Dependencies used to configure the gradle plugins
    compile("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.3.41")
    compile("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.41")
    compile("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.1.1")
    compile("org.jlleitschuh.gradle:ktlint-gradle:9.1.0")
    compile("com.android.tools.build:gradle:3.5.1")
    compile("com.github.ben-manes:gradle-versions-plugin:0.27.0")

    testCompile("junit:junit:4.12")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}

tasks.named("check") {
//    dependsOn("dependencyUpdates")
//    dependsOn("ktlintCheck")
}
