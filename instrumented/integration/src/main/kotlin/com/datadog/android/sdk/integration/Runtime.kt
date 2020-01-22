/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.sdk.integration

import android.os.Build
import com.datadog.android.log.Logger

internal object Runtime {

    fun logger(): Logger {
        // Initialise Logger
        val logger = Logger.Builder()
            .setNetworkInfoEnabled(true)
            .build()

        // Attributes
        stringAttributes.forEach { logger.addAttribute(it.key, it.value) }
        intAttribute.forEach { logger.addAttribute(it.key, it.value) }

        // tags
        keyValuePairsTags.forEach { logger.addTag(it.key, it.value) }
        singleValueTags.forEach { logger.addTag(it) }

        return logger
    }

    val keyValuePairsTags = mapOf(
        "flavor" to BuildConfig.FLAVOR,
        "build_type" to BuildConfig.BUILD_TYPE,
        "blank" to ""
    )

    val singleValueTags = listOf(
        "datadog",
        "mobile"
    )

    val allTags = keyValuePairsTags
        .map { (k, v) -> if (v.isNotBlank()) "$k:$v" else k }
        .union(singleValueTags)
        .toList()

    val stringAttributes = mapOf(
        "version_name" to BuildConfig.VERSION_NAME,
        "device" to Build.DEVICE,
        "null_string" to null
    )

    val intAttribute = mapOf(
        "version_code" to BuildConfig.VERSION_CODE,
        "sdk_int" to Build.VERSION.SDK_INT
    )

    val allAttributes = mutableMapOf<String, Any?>().apply {
        putAll(stringAttributes)
        putAll(intAttribute)
    }

    const val DD_TOKEN = "MYTESTAPPTOKEN"
    const val DD_CONTENT_TYPE = "application/json"
}
