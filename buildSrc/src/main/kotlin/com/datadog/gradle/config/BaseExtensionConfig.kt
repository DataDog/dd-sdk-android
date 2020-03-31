/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

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
