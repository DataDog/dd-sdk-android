import com.datadog.gradle.Dependencies
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.kotlinConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.datadog.android.zstd"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cFlags("-Oz", "-fvisibility=hidden", "-flto")
                cppFlags("-std=c++17", "-Oz", "-fvisibility=hidden", "-flto")
                arguments("-DANDROID_STL=none")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = Dependencies.Versions.CMake
        }
    }

    ndkVersion = Dependencies.Versions.Ndk
}

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()

dependencies {
    implementation(libs.kotlin)
}
