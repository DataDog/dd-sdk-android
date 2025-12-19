import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.datadoghq.dd-sdk-android-gradle-plugin")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

android {
    namespace = "com.datadog.cronet.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.datadog.cronet.sample"
        minSdk = 24
        targetSdk = 36
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