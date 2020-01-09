/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.gradle.plugin.gitdiff

import org.gradle.api.Task

data class Dependency(
    val task: Task,
    val dependsOn: List<String> = emptyList()
)
