/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * The main Gradle [Plugin].
 */
class JsonSchemaPlugin : Plugin<Project> {

    // region Plugin

    /**
     * {@inheritDoc}.
     */
    override fun apply(target: Project) {
        val extension = target.extensions
            .create(EXTENSION_NAME, JsonSchemaExtension::class.java)

        val task = target.tasks
            .create(TASK_REVIEW_NAME, GenerateJsonSchemaTask::class.java)
        task.setParams(extension = extension)

        target.tasks.named("preBuild") { dependsOn(task) }
    }

    // endregion

    companion object {
        internal const val EXTENSION_NAME = "jsonSchema2Poko"
        internal const val TASK_REVIEW_NAME = "generateJsonSchema2Poko"
    }
}
