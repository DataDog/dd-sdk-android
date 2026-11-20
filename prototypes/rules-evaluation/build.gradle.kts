plugins {
    kotlin("jvm") version "2.0.21"
    id("com.google.protobuf") version "0.9.4"
    `java-library`
}

group = "com.datadog.flags.benchmark"
version = "0.0.1-prototype"

repositories { mavenCentral() }

dependencies {
    api("com.google.protobuf:protobuf-java:3.25.5")
    implementation("com.google.protobuf:protobuf-java-util:3.25.5")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

kotlin { jvmToolchain(17) }
protobuf { protoc { artifact = "com.google.protobuf:protoc:3.25.5" } }
tasks.test { useJUnitPlatform() }

// Keep this standalone experiment out of the shipping SDK's module graph.
providers.environmentVariable("DD_FLAGS_KOTLIN_BUILD_DIR").orNull?.let {
    layout.buildDirectory.set(file(it))
}
