import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig

plugins {
    // Build
    id("com.android.library")

    // Analysis tools
    id("com.github.ben-manes.versions")

    // Tests
    id("de.mobilej.unmock")

    // Internal Generation
    id("thirdPartyLicences")
    id("apiSurface")
    id("transitiveDependencies")
    id("binary-compatibility-validator")
}

android {
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    namespace = "datadog.trace"
}

dependencies {
    coreLibraryDesugaring(libs.desugarJdk)
    implementation(libs.slf4j)
    implementation(libs.moshi)
    implementation(libs.jctools)
    implementation(libs.kotlin)
    implementation(libs.okHttp)
    implementation(libs.kotlin)
    implementation(libs.androidXAnnotation)
    implementation(libs.datadogSketchesJava)
    implementation(libs.re2j)
    compileOnly(libs.spotbugs)

    // TODO: RUM-3268 Port and enable the groovy unit tests
}

androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
