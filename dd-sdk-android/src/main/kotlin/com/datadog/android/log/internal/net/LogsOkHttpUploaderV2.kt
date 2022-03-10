/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.net

import com.datadog.android.core.internal.net.DataOkHttpUploaderV2
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.log.Logger
import okhttp3.Call

internal open class LogsOkHttpUploaderV2(
    endpoint: String,
    clientToken: String,
    source: String,
    sdkVersion: String,
    callFactory: Call.Factory,
    androidInfoProvider: AndroidInfoProvider,
    internalLogger: Logger
) : DataOkHttpUploaderV2(
    buildUrl(endpoint, TrackType.LOGS),
    clientToken,
    source,
    sdkVersion,
    callFactory,
    CONTENT_TYPE_JSON,
    androidInfoProvider,
    internalLogger
) {

    override fun buildQueryParameters(): Map<String, Any> {
        return mapOf(
            QUERY_PARAM_SOURCE to source
        )
    }
}
