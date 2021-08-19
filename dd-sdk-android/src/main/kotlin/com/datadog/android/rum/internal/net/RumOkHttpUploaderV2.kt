/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.DataOkHttpUploaderV2
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.RumAttributes
import okhttp3.Call

internal open class RumOkHttpUploaderV2(
    endpoint: String,
    clientToken: String,
    source: String,
    callFactory: Call.Factory
) : DataOkHttpUploaderV2(
    buildUrl(endpoint, TrackType.RUM),
    clientToken,
    source,
    callFactory,
    CONTENT_TYPE_TEXT_UTF8,
    sdkLogger
) {

    private val tags: String by lazy {
        val elements = mutableListOf(
            "${RumAttributes.SERVICE_NAME}:${CoreFeature.serviceName}",
            "${RumAttributes.APPLICATION_VERSION}:${CoreFeature.packageVersion}",
            "${RumAttributes.SDK_VERSION}:${BuildConfig.SDK_VERSION_NAME}",
            "${RumAttributes.ENV}:${CoreFeature.envName}"
        )

        if (CoreFeature.variant.isNotEmpty()) {
            elements.add("${RumAttributes.VARIANT}:${CoreFeature.variant}")
        }

        elements.joinToString(",")
    }

    // region DataOkHttpUploader

    override fun buildQueryParameters(): Map<String, Any> {
        return mapOf(
            QUERY_PARAM_SOURCE to source,
            QUERY_PARAM_TAGS to tags
        )
    }

    // endregion
}
