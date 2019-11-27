import com.datadog.gradle.Dependencies
import com.datadog.gradle.config.*
import com.datadog.gradle.testImplementation

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("android.extensions")
    `maven-publish`
    id("com.github.ben-manes.versions")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("thirdPartyLicences")
    jacoco
}

val isLogEnabledInRelease: String
    get() {
        return localProperties.getProperty(LocalProjectProperties.FORCE_ENABLE_LOGCAT) ?: "false"
    }
val isLogEnabledInDebug: String
    get() {
        return "true"
    }

android {
    compileSdkVersion(AndroidConfig.TARGET_SDK)
    buildToolsVersion(AndroidConfig.BUILD_TOOLS_VERSION)

    defaultConfig {
        minSdkVersion(AndroidConfig.MIN_SDK)
        targetSdkVersion(AndroidConfig.TARGET_SDK)
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name
    }

    sourceSets.named("main") {
        java.srcDir("src/main/kotlin")
    }
    sourceSets.named("test") {
        java.srcDir("src/test/kotlin")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        getByName("release") {
            buildConfigField("Boolean",
                    GlobalBuildConfigProperties.LOGCAT_ENABLED,
                    isLogEnabledInRelease)
        }

        getByName("debug") {
            buildConfigField("Boolean",
                    GlobalBuildConfigProperties.LOGCAT_ENABLED,
                    isLogEnabledInDebug)
        }
    }
}

dependencies {
    implementation(Dependencies.Libraries.Gson)
    implementation(Dependencies.Libraries.Kotlin)
    implementation(Dependencies.Libraries.OkHttp)

    testImplementation(Dependencies.Libraries.JUnit5)
    testImplementation(Dependencies.Libraries.TestTools)
    testImplementation(Dependencies.Libraries.OkHttpMock)
}

kotlinConfig()
detektConfig()
ktLintConfig()
junitConfig()
jacocoConfig()
dependencyUpdateConfig()
publishingConfig("${rootDir.canonicalPath}/repo")
