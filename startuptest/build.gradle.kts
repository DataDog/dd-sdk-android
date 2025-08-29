import com.datadog.gradle.androidTestImplementation
import com.datadog.gradle.config.configureFlavorForSampleApp

plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.datadog.android.startuptest"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Link the target app module
    targetProjectPath = ":sample:kotlin"
    flavorDimensions += listOf("site")
    productFlavors {
        val regions = arrayOf("us1")
        regions.forEachIndexed { index, region ->
            register(region) {
                dimension = "site"
            }
        }
    }

    testOptions {
        animationsDisabled = true
    }
}

dependencies {
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test:rules:1.7.0")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
