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

    // Analysis tools
    id("com.github.ben-manes.versions")

    // Tests
    id("de.mobilej.unmock")
    id("org.jetbrains.kotlinx.kover")

    // Internal Generation
    id("thirdPartyLicences")
    id("transitiveDependencies")
    id("binary-compatibility-validator")
}

android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    namespace = "com.com.datadog.trace"
}

dependencies {
    coreLibraryDesugaring(libs.desugarJdk)
    implementation(libs.moshi)
    implementation(libs.jctools)
    implementation(libs.kotlin)
    implementation(libs.okHttp)
    implementation(libs.androidXAnnotation)
    implementation(libs.datadogSketchesJava)
    implementation(libs.re2j)

    // TODO: RUM-3268 Port and enable the groovy unit tests

    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
}
kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
