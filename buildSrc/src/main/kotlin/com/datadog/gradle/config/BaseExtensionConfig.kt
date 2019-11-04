package com.datadog.gradle.config

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.findByType

inline fun <reified T : Any> Project.extensionConfig(
    crossinline configure: T.() -> Unit
) {

    project.afterEvaluate {
        val ext: T? = extensions.findByType(T::class)
        ext?.configure()
    }
}

inline fun <reified T : Task> Project.taskConfig(
    crossinline configure: T.() -> Unit
) {
    project.afterEvaluate {
        tasks.withType(T::class.java) { configure() }
    }
}
