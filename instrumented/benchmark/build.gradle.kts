import com.datadog.gradle.Dependencies
import com.datadog.gradle.androidTestImplementation
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.detektConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.ktLintConfig

plugins {
    id("com.android.library")
    id("androidx.benchmark")
    kotlin("android")
    kotlin("android.extensions")
    `maven-publish`
    id("com.github.ben-manes.versions")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("reviewBenchmark")
    jacoco
}

android {

    compileSdkVersion(AndroidConfig.TARGET_SDK)
    buildToolsVersion(AndroidConfig.BUILD_TOOLS_VERSION)

    defaultConfig {
        minSdkVersion(AndroidConfig.MIN_SDK)
        targetSdkVersion(AndroidConfig.TARGET_SDK)
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name

        multiDexEnabled = true

        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArgument(
            "androidx.benchmark.suppressErrors",
            "EMULATOR,DEBUGGABLE,UNLOCKED,ENG-BUILD"
        )
        testInstrumentationRunnerArgument(
            "androidx.benchmark.output.enable",
            "true"
        )
    }

    // TODO when using Android Plugin 3.6.+
    // enableAdditionalTestOutput=true
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    sourceSets.named("main") {
        java.srcDir("src/main/kotlin")
    }
    sourceSets.named("test") {
        java.srcDir("src/test/kotlin")
    }
    sourceSets.named("androidTest") {
        java.srcDir("src/androidTest/kotlin")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packagingOptions {
        exclude("META-INF/jvm.kotlin_module")
        exclude("META-INF/LICENSE.md")
        exclude("META-INF/LICENSE-notice.md")
    }
}

repositories {
    google()
    mavenLocal()
    mavenCentral()
}

dependencies {
    // if you want to test the library in production you should change this dependency with the
    // latest release maven artifact either local or live
    // (e.g. "com.datadoghq:dd-sdk-android:1.0.0")
    implementation(project(":dd-sdk-android"))

    implementation(Dependencies.Libraries.Kotlin)
    implementation(Dependencies.Libraries.KotlinReflect)
    implementation(Dependencies.Libraries.AndroidXMultidex)

    androidTestImplementation(project(":tools:unit"))
    androidTestImplementation(Dependencies.Libraries.IntegrationTests)
    androidTestImplementation(Dependencies.Libraries.JetpackBenchmark)
    androidTestImplementation(Dependencies.Libraries.OkHttpMock)

    detekt(project(":tools:detekt"))
    detekt(Dependencies.Libraries.DetektCli)
}

kotlinConfig()
detektConfig()
ktLintConfig()

reviewBenchmark {

    // Logs Benchmarks
    addThreshold(
        "benchmark_create_one_log",
        TimeUnit.MICROSECONDS.toNanos(500)
    )
    addThreshold(
        "benchmark_create_one_log_with_throwable",
        TimeUnit.MILLISECONDS.toNanos(1)
    )
    addThreshold(
        "benchmark_create_one_log_with_tags",
        TimeUnit.MILLISECONDS.toNanos(1)
    )
    addThreshold(
        "benchmark_create_one_log_with_attributes",
        TimeUnit.MILLISECONDS.toNanos(1)
    )

    // Traces Benchmarks
    addThreshold(
        "benchmark_creating_span",
        TimeUnit.MILLISECONDS.toNanos(1)
    )
    addThreshold(
        "benchmark_creating_span_with_throwable",
        TimeUnit.MILLISECONDS.toNanos(1)
    )
    addThreshold(
        "benchmark_creating_spans_with_baggage_items_and_logs",
        TimeUnit.MILLISECONDS.toNanos(1)
    )
    addThreshold(
        "benchmark_creating_heavy_load_of_spans",
        TimeUnit.MILLISECONDS.toNanos(400)
    )
    addThreshold(
        "benchmark_creating_medium_load_of_spans",
        TimeUnit.MILLISECONDS.toNanos(250)
    )

    // LogIo Benchmarks

    addThreshold(
        "benchmark_write_logs_on_disk",
        TimeUnit.MILLISECONDS.toNanos(60)
    )
    addThreshold(
        "benchmark_read_logs_from_disk",
        TimeUnit.MILLISECONDS.toNanos(60)
    )
}
