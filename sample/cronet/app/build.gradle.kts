import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.datadogGradlePlugin)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.15.0")
        force("androidx.core:core-ktx:1.15.0")
    }
}

android {
    namespace = "com.datadog.cronet.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.datadog.cronet.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "DD_APP_ID",
            "\"${localProperties.getProperty("DD_APP_ID", "")}\""
        )

        buildConfigField(
            "String",
            "DD_API_TOKEN",
            "\"${localProperties.getProperty("DD_API_TOKEN", "")}\""
        )
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.bundles.core)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.datadog)
    implementation(libs.play.services.cronet)
    implementation(platform(libs.androidx.compose.bom))
}