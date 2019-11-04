package com.datadog.gradle

object Dependencies {

    object Versions {
        // Commons
        const val Kotlin = "1.3.41"
        const val AndroidToolsPlugin = "3.5.1"

        // Tests
        const val Jacoco = "0.8.4"

        // Tools
        const val Detekt = "1.0.1"
        const val KtLint = "8.2.0"
        const val DependencyVersion = "0.27.0"
    }

    object Libraries {

        const val Kotlin = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.Kotlin}"

        @JvmField
        val JUnit5Extensions = arrayOf(
                "org.mockito:mockito-junit-jupiter:2.23.0"
        )
    }

    object ClassPaths {
        const val AndroidTools = "com.android.tools.build:gradle:${Versions.AndroidToolsPlugin}"
        const val Kotlin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.Kotlin}"
        const val KtLint = "org.jlleitschuh.gradle:ktlint-gradle:${Versions.KtLint}"
    }

    object Repositories {
        const val Gradle = "https://plugins.gradle.org/m2/"
        const val Google = "https://maven.google.com"
    }

    object PluginNamespaces {
        const val Detetk = "io.gitlab.arturbosch"
        const val KtLint = "org.jlleitschuh.gradle"
        const val DependencyVersion = "com.github.ben-manes"
        const val Kotlin = "org.jetbrains.kotlin"
        const val KotlinAndroid = "org.jetbrains.kotlin.android"
    }
}
