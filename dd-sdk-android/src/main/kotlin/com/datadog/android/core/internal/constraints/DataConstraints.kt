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

    fun validateAttributes(
        attributes: Map<String, Any?>,
        keyPrefix: String? = null,
        attributesGroupName: String? = null
    ): Map<String, Any?>

    fun validateTags(tags: List<String>): List<String>

    fun validateEvent(rumEvent: Any): Any
}
