import com.datadog.gradle.Dependencies
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.kotlinConfig
import org.gradle.api.JavaVersion

plugins {
    id("com.android.application")
    kotlin("android")
}

val nightlyTestsTokenKey = "NIGHTLY_TESTS_TOKEN"
val nightlyTestsRumAppIdKey = "NIGHTLY_TESTS_RUM_APP_ID"

android {

    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name

        multiDexEnabled = true
        vectorDrawables.useSupportLibrary = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            nightlyTestsTokenKey,
            "\"${project.findProperty(nightlyTestsTokenKey)}\""
        )
        buildConfigField(
            "String",
            nightlyTestsRumAppIdKey,
            "\"${project.findProperty(nightlyTestsRumAppIdKey)}\""
        )
        externalNativeBuild {
            cmake {
                cppFlags.add("-std=c++14")
            }
        }
    }

    namespace = "com.datadog.android.nightly"

    sourceSets.named("main") {
        java.srcDir("src/main/kotlin")
    }
    sourceSets.named("androidTest") {
        java.srcDir("src/androidTest/kotlin")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packagingOptions {
        resources {
            excludes += "META-INF/*"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    externalNativeBuild {
        cmake {
            path = File("$projectDir/src/main/cpp/CMakeLists.txt")
            version = Dependencies.Versions.CMake
        }
    }
    ndkVersion = Dependencies.Versions.Ndk
}

repositories {
    google()
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(project(":dd-sdk-android"))
    implementation(project(":library:dd-sdk-android-ndk"))
    implementation(project(":library:dd-sdk-android-logs"))

    implementation(libs.bundles.androidXNavigation)
    implementation(libs.gson)
    implementation(libs.kotlin)
    implementation(libs.bundles.androidXSupportBase)
    implementation(libs.androidXMultidex)
    implementation(libs.elmyr)
    implementation(libs.okHttp)

    // Ktor (local server)
    implementation(libs.bundles.ktor)

    androidTestImplementation(project(":tools:unit")) {
        // We need to exclude this otherwise R8 will fail while trying to desugar a function
        // available only for API 26 and above
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.mockito")
    }
    androidTestImplementation(libs.bundles.integrationTests)
}

kotlinConfig()
