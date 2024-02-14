//import com.datadog.gradle.config.androidLibraryConfig
//import com.datadog.gradle.config.dependencyUpdateConfig
//import com.datadog.gradle.config.javadocConfig
//import com.datadog.gradle.config.junitConfig
//import com.datadog.gradle.config.kotlinConfig
//
//apply from: "$rootDir/gradle/java.gradle"
//
//dependencies {
//  implementation deps.slf4j
//
//  testImplementation project(':utils:test-utils')
//}


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
    namespace = "datadog.common.container"
}

dependencies {
    implementation(libs.kotlin)
    implementation(libs.slf4j)

    // Generate NoOp implementations
    ksp(project(":tools:noopfactory"))

    // Lint rules
    lintPublish(project(":tools:lint"))

    // Testing
    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    unmock(libs.robolectric)
//    testImplementation project(':utils:test-utils')
}

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
