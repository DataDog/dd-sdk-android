/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.lint.InternalApi

/**
 * Utilities for building and parsing Datadog tags strings and maps.
 */
@Suppress("PackageNameVisibility")
object DdTagsUtils {

    internal const val TAG_SERVICE = "service"
    internal const val TAG_VERSION = "version"
    internal const val TAG_SDK_VERSION = "sdk_version"
    internal const val TAG_ENV = "env"
    internal const val TAG_VARIANT = "variant"

    private const val TAGS_SEPARATOR = ","
    private const val KEY_VALUE_SEPARATOR = ":"

    /**
     * Builds a Datadog tags string from a [DatadogContext].
     */
    @InternalApi
    fun toDdTagsString(ddContext: DatadogContext): String = buildString {
        append(TAG_SERVICE).append(KEY_VALUE_SEPARATOR).append(ddContext.service).append(TAGS_SEPARATOR)
        append(TAG_VERSION).append(KEY_VALUE_SEPARATOR).append(ddContext.version).append(TAGS_SEPARATOR)
        append(TAG_SDK_VERSION).append(KEY_VALUE_SEPARATOR).append(ddContext.sdkVersion).append(TAGS_SEPARATOR)
        append(TAG_ENV).append(KEY_VALUE_SEPARATOR).append(ddContext.env)
        if (ddContext.variant.isNotEmpty()) {
            append(TAGS_SEPARATOR).append(TAG_VARIANT).append(KEY_VALUE_SEPARATOR).append(ddContext.variant)
        }
    }

    /**
     * Builds a Datadog tags string from a map of tag key-value pairs.
     */
    @InternalApi
    fun toDdTagsString(tags: Map<String, String>): String = tags.entries.joinToString(TAGS_SEPARATOR) {
        "${it.key}${KEY_VALUE_SEPARATOR}${it.value}"
    }

    /**
     * Builds a Datadog tags map directly from a [DatadogContext].
     */
    @Suppress("UnsafeThirdPartyFunctionCall")
    @InternalApi
    fun toDdTagsMap(ddContext: DatadogContext): Map<String, String> = LinkedHashMap<String, String>().apply {
        put(TAG_SERVICE, ddContext.service)
        put(TAG_VERSION, ddContext.version)
        put(TAG_SDK_VERSION, ddContext.sdkVersion)
        put(TAG_ENV, ddContext.env)

        if (ddContext.variant.isNotEmpty()) {
            put(TAG_VARIANT, ddContext.variant)
        }
    }

    /**
     * Parses a Datadog tags string into a [Map].
     */
    @Suppress("UnsafeThirdPartyFunctionCall")
    @InternalApi
    fun toDdTagsMap(tagsString: String?): Map<String, String>? = LinkedHashMap<String, String>().also { tagsMap ->
        val tags = tagsString?.split(TAGS_SEPARATOR)
        if (tags.isNullOrEmpty()) return null
        for (tag in tags) {
            val keyValue = tag.split(KEY_VALUE_SEPARATOR, limit = 2)
                .takeIf { it.size == 2 && it[0].isNotEmpty() && it[1].isNotEmpty() }
                ?: continue

            tagsMap[keyValue[0]] = keyValue[1]
        }
        if (tagsMap.isEmpty()) return null
    }
}
