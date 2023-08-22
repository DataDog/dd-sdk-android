/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.constraints

/**
 * This interface allows sanitizing logs locally before uploading them to the servers.
 */
interface DataConstraints {

    /**
     * Validates attributes according to the particular rules.
     *
     * @param T type of attribute values.
     * @param attributes Attributes to validate.
     * @param keyPrefix Optional key prefix for the attributes root (ex. "usr").
     * @param attributesGroupName Optional description for the attributes to validate.
     * @param reservedKeys Collection of reserved key. If attribute name is in the reserved key,
     * this attribute will be rejected.
     */
    fun <T : Any?> validateAttributes(
        attributes: Map<String, T>,
        keyPrefix: String? = null,
        attributesGroupName: String? = null,
        reservedKeys: Set<String> = emptySet()
    ): MutableMap<String, T>

    /**
     * Validates tags according to the particular rules.
     *
     * @param tags Tags to validate.
     */
    fun validateTags(tags: List<String>): List<String>

    /**
     * Validate timings according to the particular rules.
     *
     * @param timings Timings to validate.
     */
    fun validateTimings(timings: Map<String, Long>): MutableMap<String, Long>
}
