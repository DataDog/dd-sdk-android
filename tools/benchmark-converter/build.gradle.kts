plugins {
    id("application")
    id("org.jetbrains.kotlin.jvm")
    kotlin("plugin.serialization")

}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

application {
    mainClass = "com.datadog.android.benchmark_converter.BenchmarkConverterKt"
}

dependencies {
    implementation(libs.kotlinxSerializationJson)
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")
}
