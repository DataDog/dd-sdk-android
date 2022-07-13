/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration

import android.os.Build
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.log.Logger
import com.datadog.android.tracing.AndroidTracer
import java.util.UUID

internal object RuntimeConfig {

    val APP_ID = UUID.randomUUID().toString()

    const val INTEGRATION_TESTS_ENVIRONMENT = "integration-tests"
    const val DD_TOKEN = "MYTESTAPPTOKEN"
    const val CONTENT_TYPE_JSON = "application/json"
    const val CONTENT_TYPE_TEXT = "text/plain;charset=UTF-8"
    private const val LOCALHOST = "http://localhost"

    var logsEndpointUrl: String = LOCALHOST
    var tracesEndpointUrl: String = LOCALHOST
    var rumEndpointUrl: String = LOCALHOST

    val LONG_TASK_LARGE_THRESHOLD = Long.MAX_VALUE

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

    fun tracer(): AndroidTracer {
        return AndroidTracer.Builder().build()
    }

    fun credentials(): Credentials {
        return Credentials(
            DD_TOKEN,
            INTEGRATION_TESTS_ENVIRONMENT,
            Credentials.NO_VARIANT,
            APP_ID
        )
    }

    fun configBuilder(): Configuration.Builder {
        return Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true,
            sessionReplayEnabled = true
        )
            .useCustomLogsEndpoint(logsEndpointUrl)
            .useCustomRumEndpoint(rumEndpointUrl)
            .useCustomTracesEndpoint(tracesEndpointUrl)
            .setUploadFrequency(UploadFrequency.FREQUENT)
    }

    val keyValuePairsTags = mapOf(
        "build_type" to BuildConfig.BUILD_TYPE,
        "blank" to "",
        "env" to INTEGRATION_TESTS_ENVIRONMENT,
        "version" to BuildConfig.VERSION_NAME
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
}
