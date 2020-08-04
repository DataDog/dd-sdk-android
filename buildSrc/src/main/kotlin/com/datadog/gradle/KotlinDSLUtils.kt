/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle

import org.gradle.api.artifacts.dsl.DependencyHandler

fun DependencyHandler.api(dependencies: Array<String>) {
    dependencies.forEach {
        add("api", it)
    }
}

fun DependencyHandler.compile(dependencies: Array<String>) {
    dependencies.forEach {
        add("compile", it)
    }
}

fun DependencyHandler.compileOnly(dependencies: Array<String>) {
    dependencies.forEach {
        add("compileOnly", it)
    }
}

fun DependencyHandler.testCompile(dependencies: Array<String>) {
    dependencies.forEach {
        add("testCompile", it)
    }
}

fun DependencyHandler.implementation(dependencies: Array<String>) {
    dependencies.forEach {
        add("implementation", it)
    }
}

fun DependencyHandler.testImplementation(dependencies: Array<String>) {
    dependencies.forEach {
        add("testImplementation", it)
    }
}

fun DependencyHandler.androidTestImplementation(dependencies: Array<String>) {
    dependencies.forEach {
        add("androidTestImplementation", it)
    }
}
