
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
    namespace = "datadog.trace.test.util"
}

//dependencies {
//  api deps.groovy
//  api deps.spock
//
//  api deps.bytebuddy
//  api deps.bytebuddyagent
//
//  api(group="com.github.stefanbirkner", name="system-rules", version="1.19.0"
//  api(group="commons-fileupload", name="commons-fileupload", version="1.5")
//}
