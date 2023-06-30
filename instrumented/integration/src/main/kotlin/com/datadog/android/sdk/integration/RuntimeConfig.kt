/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration

import android.os.Build
import com.datadog.android._InternalProxy
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.log.Logger
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.v2.api.SdkCore
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
    var sessionReplayEndpointUrl: String = LOCALHOST

    val LONG_TASK_LARGE_THRESHOLD = Long.MAX_VALUE

    fun logger(sdkCore: SdkCore): Logger {
        // Initialise Logger
        val logger = Logger.Builder(sdkCore)
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

    fun tracer(sdkCore: SdkCore): AndroidTracer {
        return AndroidTracer.Builder(sdkCore).build()
    }

    fun credentials(): Credentials {
        return Credentials(
            clientToken = DD_TOKEN,
            env = INTEGRATION_TESTS_ENVIRONMENT,
            variant = Credentials.NO_VARIANT
        )
    }

    fun configBuilder(): Configuration.Builder {
        return Configuration.Builder(
            crashReportsEnabled = true
        )
            .apply {
                _InternalProxy.allowClearTextHttp(this)
            }
            .setUploadFrequency(UploadFrequency.FREQUENT)
    }

    fun rumConfigBuilder(): RumConfiguration.Builder {
        return RumConfiguration.Builder(APP_ID)
            .useCustomEndpoint(rumEndpointUrl)
    }

    fun sessionReplayConfigBuilder(sampleRate: Float): SessionReplayConfiguration.Builder {
        return SessionReplayConfiguration.Builder(sampleRate)
            .useCustomEndpoint(sessionReplayEndpointUrl)
    }

    fun logsConfigBuilder(): LogsConfiguration.Builder {
        return LogsConfiguration.Builder()
            .useCustomEndpoint(logsEndpointUrl)
    }

    fun tracesConfigBuilder(): TraceConfiguration.Builder {
        return TraceConfiguration.Builder()
            .useCustomEndpoint(tracesEndpointUrl)
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
