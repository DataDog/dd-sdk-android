import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.kotlinConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "io.airlift.compress.v3"

    compileOptions {
        // Allow access to sun.misc.Unsafe which is available on Android but hidden from SDK
        isCoreLibraryDesugaringEnabled = false
    }

    lint {
        // Suppress warning about sun.misc.Unsafe usage
        disable += "UnsafeOptInUsageError"
    }
}

// Allow compilation with sun.misc.Unsafe by adding it to bootclasspath
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-XDignore.symbol.file")
}

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()

dependencies {
    implementation(libs.kotlin)
}
