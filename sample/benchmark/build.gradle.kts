import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.configureFlavorForBenchmark
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.java17
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.plugin.InstrumentationMode

plugins {
    id("com.android.application")
    kotlin("android")
    alias(libs.plugins.composeCompilerPlugin)
    kotlin("kapt")
    kotlin("plugin.serialization")
    id("kotlin-parcelize")
    alias(libs.plugins.datadogGradlePlugin)
    id("transitiveDependencies")
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
        configureFlavorForBenchmark(project.rootDir)
    }
    compileOptions {
        java17()
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }

    flavorDimensions += listOf("datadog")
    productFlavors {
        register("noDatadog") {
            isDefault = true
            dimension = "datadog"
        }
        register("withDatadog") {
            dimension = "datadog"
        }
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
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isMinifyEnabled = true
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            } ?: kotlin.run {
                signingConfig = signingConfigs.findByName("debug")
            }
        }
    }
}

datadog {
    composeInstrumentation = InstrumentationMode.AUTO
}

dependencies {

    implementation(libs.kotlin)

    // Android dependencies
    implementation(libs.adapterDelegatesViewBinding)
    implementation(libs.androidXMultidex)
    implementation(libs.bundles.androidXNavigation)
    implementation(libs.androidXAppCompat)
    implementation(libs.androidXConstraintLayout)
    implementation(libs.androidXLifecycleCompose)
    implementation(libs.googleMaterial)
    implementation(libs.bundles.glide)
    implementation(libs.timber)
    implementation(platform(libs.androidXComposeBom))
    implementation(libs.material3Android)
    implementation(libs.bundles.androidXCompose)
    implementation(libs.coilCompose)
    implementation(libs.daggerLib)
    kapt(libs.daggerCompiler)
    kapt(libs.glideCompiler)
    implementation(libs.coroutinesCore)
    implementation(libs.bundles.ktorClient)
    implementation(libs.kotlinxSerializationJson)
    implementation(project(":features:dd-sdk-android-logs"))
    implementation(project(":features:dd-sdk-android-rum"))
    implementation(project(":features:dd-sdk-android-trace"))
    implementation(project(":features:dd-sdk-android-trace-otel"))
    implementation(project(":features:dd-sdk-android-ndk"))
    implementation(project(":features:dd-sdk-android-webview"))
    implementation(project(":features:dd-sdk-android-session-replay"))
    implementation(project(":features:dd-sdk-android-session-replay-material"))
    implementation(project(":features:dd-sdk-android-session-replay-compose"))
    implementation(project(":integrations:dd-sdk-android-compose"))
    implementation(project(":integrations:dd-sdk-android-glide"))
    implementation(project(":integrations:dd-sdk-android-okhttp"))
    implementation(project(":tools:benchmark"))

    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.systemStubsJupiter)
    testImplementation(libs.ktorClientMock)
}

kotlinConfig()
junitConfig()
dependencyUpdateConfig()
