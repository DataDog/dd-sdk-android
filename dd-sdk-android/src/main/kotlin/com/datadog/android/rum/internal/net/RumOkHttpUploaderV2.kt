/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.DataOkHttpUploaderV2
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.RumAttributes
import okhttp3.Call

internal open class RumOkHttpUploaderV2(
    endpoint: String,
    clientToken: String,
    source: String,
    sdkVersion: String,
    callFactory: Call.Factory,
    androidInfoProvider: AndroidInfoProvider,
    private val coreFeature: CoreFeature
) : DataOkHttpUploaderV2(
    buildUrl(endpoint, TrackType.RUM),
    clientToken,
    source,
    sdkVersion,
    callFactory,
    CONTENT_TYPE_TEXT_UTF8,
    androidInfoProvider,
    sdkLogger
) {

    private val tags: String
        get() {
            val elements = mutableListOf(
                "${RumAttributes.SERVICE_NAME}:${coreFeature.serviceName}",
                "${RumAttributes.APPLICATION_VERSION}:" +
                    coreFeature.packageVersionProvider.version,
                "${RumAttributes.SDK_VERSION}:$sdkVersion",
                "${RumAttributes.ENV}:${coreFeature.envName}"
            )

            if (coreFeature.variant.isNotEmpty()) {
                elements.add("${RumAttributes.VARIANT}:${coreFeature.variant}")
            }

            return elements.joinToString(",")
        }

    // region DataOkHttpUploaderV2

    override fun buildQueryParameters(): Map<String, Any> {
        return mapOf(
            QUERY_PARAM_SOURCE to source,
            QUERY_PARAM_TAGS to tags
        )
    }

    // endregion
}
