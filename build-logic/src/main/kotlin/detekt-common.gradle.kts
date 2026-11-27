/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.android.build.api.variant.AndroidComponentsExtension
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
    // The sample and benchmark modules share Kotlin sources between debug and release.
    ignoredBuildTypes.add("release")
}

listOf("com.android.application", "com.android.library").forEach { pluginId ->
    pluginManager.withPlugin(pluginId) {
        extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
            variant.components.forEach { configureDetektClasspath(it.name) }
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
