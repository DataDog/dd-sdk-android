import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.configureFlavorForAutoApp
import com.datadog.gradle.config.configureFlavorForTvApp
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.java17
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.taskConfig

plugins {
    id("com.android.application")
    kotlin("android")
    id("com.github.ben-manes.versions")
    alias(libs.plugins.datadogGradlePlugin)
}

android {
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK_FOR_AUTO
        targetSdk = AndroidConfig.TARGET_SDK
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name
        multiDexEnabled = true

        buildFeatures {
            buildConfig = true
        }

        configureFlavorForAutoApp(project.rootDir)
    }

    namespace = "com.datadog.sample.automotive"

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        java17()
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }

        getByName("release") {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // Datadog Libraries
    implementation(project(":features:dd-sdk-android-logs"))
    implementation(project(":features:dd-sdk-android-rum"))
    implementation(project(":integrations:dd-sdk-android-okhttp"))
    implementation(project(":integrations:dd-sdk-android-okhttp-otel"))

    // Desugaring SDK
    coreLibraryDesugaring(libs.androidDesugaringSdk)

    implementation(libs.kotlin)

    // Android dependencies
    implementation(libs.androidXMultidex)
    implementation(libs.androidXCoreKtx)
    implementation(libs.androidXAppCompat)
    implementation(libs.androidXLegacySupportV4)
    implementation(libs.androidXLegacySupportV13)

    // Android Car
     implementation(libs.androidXCarApp)
     implementation(libs.androidXCarAutomotive)
}

kotlinConfig(evaluateWarningsAsErrors = false)
taskConfig<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}
junitConfig()
dependencyUpdateConfig()


