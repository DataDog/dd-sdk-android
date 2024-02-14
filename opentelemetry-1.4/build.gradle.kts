import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.library")
    kotlin("android")
    id("com.google.devtools.ksp")

    // Publish
    id("org.jetbrains.dokka")

    // Analysis tools
    id("com.github.ben-manes.versions")

    // Tests
    id("de.mobilej.unmock")
    id("org.jetbrains.kotlinx.kover")

    // Internal Generation
    id("thirdPartyLicences")
    id("apiSurface")
    id("transitiveDependencies")
    id("binary-compatibility-validator")
}

android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    namespace = "datadog.trace.instrumentation.opentelemetry14"

}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation(project(":dd-trace-api"))
    implementation(project(":dd-trace-core"))
    implementation(project(":internal-api"))
    compileOnly(libs.openTelemetry)
    compileOnly(group = "com.google.auto.value", name = "auto-value-annotations", version = "1.6.6")

    // Generate NoOp implementations
    ksp(project(":tools:noopfactory"))

    // Lint rules
    lintPublish(project(":tools:lint"))

    compileOnly(group = "com.github.spotbugs", name = "spotbugs-annotations", version = "4.2.0")

    // Testing
//    testImplementation group: 'io.opentelemetry', name: 'opentelemetry-api', version: openTelemetryVersion
//    testImplementation group: 'org.skyscreamer', name: 'jsonassert', version: '1.5.1'
//    latestDepTestImplementation group: 'io.opentelemetry', name: 'opentelemetry-api', version: '1+'
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    unmock(libs.robolectric)
}

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()

