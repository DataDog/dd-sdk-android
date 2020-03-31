/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.constraints

internal class NoOpLogConstraints : LogConstraints {
    override fun validateAttributes(
        attributes: Map<String, Any?>
    ): Map<String, Any?> {
        return attributes
    }

    override fun validateTags(
        tags: List<String>
    ): List<String> {
        return tags
    }
}
