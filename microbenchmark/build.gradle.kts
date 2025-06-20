plugins {
    id("com.android.library")
    kotlin("android")
    id("androidx.benchmark")
}

android {
    namespace = "com.datadog.android.macrobenchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36

        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY"
    }

    testBuildType = "release"
    buildTypes {
        debug {
            // Since isDebuggable can"t be modified by gradle for library modules,
            // it must be done in a manifest - see src/androidTest/AndroidManifest.xml
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "benchmark-proguard-rules.pro"
            )
        }
        release {
            isDefault = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    androidTestImplementation(libs.androidXTestRunner)
    androidTestImplementation(libs.androidXTestJUnitExt)
    androidTestImplementation(libs.jUnit4)
    androidTestImplementation(libs.benchmarkJunit4)
    // Add your dependencies here. Note that you cannot benchmark code
    // in an app module this way - you will need to move any code you
    // want to benchmark to a library module:
    // https://developer.android.com/studio/projects/android-library#Convert

}