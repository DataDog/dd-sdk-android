/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.DeviceTest
import com.datadog.gradle.config.configureDetekt
import dev.detekt.gradle.Detekt
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("dev.detekt")
}

configureDetekt()

detekt {
    // Debug covers shared production and unit test sources.
    ignoredBuildTypes.add("release")
}

listOf("com.android.application", "com.android.library").forEach { pluginId ->
    pluginManager.withPlugin(pluginId) {
        extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
            variant.components.forEach { component ->
                configureDetektClasspath(component.name)
                // Device tests can belong exclusively to the release variant.
                if (variant.buildType == "release" && component is DeviceTest) {
                    val taskName = "detekt${component.name.replaceFirstChar { it.uppercaseChar() }}"
                    tasks.named("detektTest") {
                        dependsOn(tasks.withType<Detekt>().named { it == taskName })
                    }
                }
            }
        }
    }
}

tasks.withType<Detekt>().configureEach {
    val buildDirectory = layout.buildDirectory
    exclude { it.file.startsWith(buildDirectory.get().asFile) }
}

fun configureDetektClasspath(componentName: String) {
    val taskSuffix = componentName.replaceFirstChar { it.uppercaseChar() }
    val kotlinCompilations = extensions.getByType<KotlinAndroidExtension>().target.compilations
    tasks.withType<Detekt>().named { it == "detekt$taskSuffix" }.configureEach {
        val compilation = kotlinCompilations.named(componentName)
        val kotlinCompile = tasks.named<KotlinJvmCompile>("compile${taskSuffix}Kotlin")
        val javaCompile = tasks.named<JavaCompile>("compile${taskSuffix}JavaWithJavac")
        // Include generated classes alongside the Kotlin compilation classpath.
        classpath.setFrom(
            compilation.map { it.output.classesDirs },
            kotlinCompile.map { it.libraries },
            kotlinCompile.flatMap { it.destinationDirectory },
            javaCompile.flatMap { it.destinationDirectory }
        )
    }
}
