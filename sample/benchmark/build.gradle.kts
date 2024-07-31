import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.java17
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
}

@Suppress("StringLiteralDuplication")
android {
    namespace = "com.datadog.sample.benchmark"
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name
        multiDexEnabled = true

        buildFeatures {
            buildConfig = true
        }
        vectorDrawables.useSupportLibrary = true
    }
    compileOptions {
        java17()
    }

    val bmPassword = System.getenv("BM_STORE_PASSWD")
    signingConfigs {
        if (bmPassword != null) {
            create("release") {
                storeFile = File(project.rootDir, "sample-benchmark.keystore")
                storePassword = bmPassword
                keyAlias = "dd-sdk-android"
                keyPassword = bmPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }

        getByName("release") {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt")
            )
            isMinifyEnabled = true
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
        }
    }
}

dependencies {

    implementation(libs.kotlin)

    // Android dependencies
    implementation(libs.androidXMultidex)
    implementation(libs.bundles.androidXNavigation)
    implementation(libs.androidXAppCompat)
    implementation(libs.androidXConstraintLayout)
    implementation(libs.googleMaterial)
    implementation(libs.glideCore)
}

kotlinConfig()
junitConfig()
dependencyUpdateConfig()
