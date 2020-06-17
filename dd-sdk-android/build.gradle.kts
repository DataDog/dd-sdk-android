/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.Dependencies
import com.datadog.gradle.api
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.BuildConfigPropertiesKeys
import com.datadog.gradle.config.GradlePropertiesKeys
import com.datadog.gradle.config.bintrayConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektConfig
import com.datadog.gradle.config.jacocoConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.ktLintConfig
import com.datadog.gradle.config.publishingConfig
import com.datadog.gradle.implementation
import com.datadog.gradle.testImplementation
import org.jetbrains.kotlin.kapt3.base.Kapt.kapt

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("android.extensions")
    kotlin("kapt")
    `maven-publish`
    id("com.github.ben-manes.versions")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("thirdPartyLicences")
    id("apiSurface")
    id("cloneDependencies")
    id("org.jetbrains.dokka")
    id("com.jfrog.bintray")
    id("de.mobilej.unmock")
    jacoco
}

fun isLogEnabledInRelease(): String {
    return project.findProperty(GradlePropertiesKeys.FORCE_ENABLE_LOGCAT) as? String ?: "false"
}

android {
    compileSdkVersion(AndroidConfig.TARGET_SDK)
    buildToolsVersion(AndroidConfig.BUILD_TOOLS_VERSION)

    defaultConfig {
        minSdkVersion(AndroidConfig.MIN_SDK)
        targetSdkVersion(AndroidConfig.TARGET_SDK)
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name
        externalNativeBuild {
            cmake {
                cppFlags.add("-std=c++14")
            }
        }
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        getByName("release") {
            buildConfigField(
                "Boolean",
                BuildConfigPropertiesKeys.LOGCAT_ENABLED,
                isLogEnabledInRelease()
            )
        }

        getByName("debug") {
            buildConfigField(
                "Boolean",
                BuildConfigPropertiesKeys.LOGCAT_ENABLED,
                "true"
            )
        }
    }

    packagingOptions {
        exclude("META-INF/jvm.kotlin_module")
        exclude("META-INF/LICENSE.md")
        exclude("META-INF/LICENSE-notice.md")
    }

    lintOptions {
        isWarningsAsErrors = true
        isAbortOnError = true
        isCheckReleaseBuilds = false
        isCheckGeneratedSources = true
    }

    externalNativeBuild {
        cmake {
            path = File("${projectDir}/src/main/cpp/CMakeLists.txt")
            version = "3.10.2"
        }
    }
    ndkVersion = "21.3.6528147"
}

dependencies {
    implementation(Dependencies.Libraries.Gson)
    implementation(Dependencies.Libraries.Kotlin)
    implementation(Dependencies.Libraries.OkHttp)

    implementation(Dependencies.Libraries.AndroidXWorkManager)
    implementation(Dependencies.Libraries.AndroidXCore)
    implementation(Dependencies.Libraries.AndroidXAppCompat)
    implementation(Dependencies.Libraries.AndroidXRecyclerView)
    implementation(Dependencies.Libraries.AndroidXNavigation)

    api(Dependencies.Libraries.TracingOt)
    kapt(project(":tools:noopfactory"))

    testImplementation(project(":tools:unit"))
    testImplementation(Dependencies.Libraries.JUnit5)
    testImplementation(Dependencies.Libraries.TestTools)
    testImplementation(Dependencies.Libraries.OkHttpMock)

    unmock(Dependencies.Libraries.Robolectric)

    detekt(project(":tools:detekt"))
    detekt(Dependencies.Libraries.DetektCli)
}

cloneDependencies {
    clone(
        "https://github.com/DataDog/dd-trace-java.git",
        "dd-trace-ot",
        "v0.50.0",
        listOf(
            "dd-trace-ot.gradle",
            "README.md",
            "jfr-openjdk/",
            "src/jmh/", // JVM based benchmark, not relevant for ART/Dalvik
            "src/traceAgentTest/",
            "src/ot33CompatabilityTest/",
            "src/ot31CompatabilityTest/",
            "src/test/resources/",
            "src/main/java/datadog/trace/common/processor/",
            "src/main/java/datadog/trace/common/sampling/RuleBasedSampler.java",
            "src/main/java/datadog/trace/common/serialization",
            "src/main/java/datadog/trace/common/writer/unixdomainsockets",
            "src/main/java/datadog/trace/common/writer/ddagent",
            "src/main/java/datadog/trace/common/writer/DDAgentWriter.java",
            "src/main/java/datadog/opentracing/resolver",
            "src/main/java/datadog/opentracing/ContainerInfo.java",
            "src/test"
        )
    )
    clone(
        "https://github.com/DataDog/dd-trace-java.git",
        "dd-trace-api",
        "v0.50.0",
        listOf(
            "dd-trace-api.gradle",
            "src/main/java/datadog/trace/api/GlobalTracer.java",
            "src/main/java/datadog/trace/api/CorrelationIdentifier.java",
            "src/test"
        )
    )
    clone(
        "https://github.com/DataDog/dd-trace-java.git",
        "utils/thread-utils",
        "v0.50.0",
        listOf(
            "thread-utils.gradle",
            "src/test/"
        )
    )
}

unMock {
    keep("android.os.BaseBundle")
    keep("android.os.Bundle")
    keep("android.os.Parcel")
    keepStartingWith("com.android.internal.util.")
    keepStartingWith("android.util.")
    keep("android.content.ComponentName")
}

kotlinConfig()
detektConfig()
ktLintConfig()
junitConfig()
jacocoConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig("${rootDir.canonicalPath}/repo")
bintrayConfig()
