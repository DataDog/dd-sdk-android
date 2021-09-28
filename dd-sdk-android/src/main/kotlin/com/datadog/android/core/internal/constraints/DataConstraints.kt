/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.constraints

/**
 * This interface allows sanitizing logs locally before uploading them to the servers.
 */
internal interface DataConstraints {

    fun <T : Any?> validateAttributes(
        attributes: Map<String, T>,
        keyPrefix: String? = null,
        attributesGroupName: String? = null,
        reservedKeys: Set<String> = emptySet()
    ): Map<String, T>

    fun validateTags(tags: List<String>): List<String>

    fun validateTimings(timings: Map<String, Long>): Map<String, Long>
}
