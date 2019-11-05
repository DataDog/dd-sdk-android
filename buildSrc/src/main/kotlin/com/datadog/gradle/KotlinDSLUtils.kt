package com.datadog.gradle

import org.gradle.api.artifacts.dsl.DependencyHandler

fun DependencyHandler.compile(dependencies : Array<String>) {
    dependencies.forEach {
        add("compile", it)
    }
}

fun DependencyHandler.compileOnly(dependencies : Array<String>) {
    dependencies.forEach {
        add("compileOnly", it)
    }
}

fun DependencyHandler.testCompile(dependencies : Array<String>) {
    dependencies.forEach {
        add("testCompile", it)
    }
}

fun DependencyHandler.testImplementation(dependencies : Array<String>) {
    dependencies.forEach {
        add("testImplementation", it)
    }
}