/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.net

import com.datadog.android.core.internal.net.DataOkHttpUploaderV2
import com.datadog.android.core.internal.utils.sdkLogger
import okhttp3.Call

internal open class TracesOkHttpUploaderV2(
    endpoint: String,
    clientToken: String,
    source: String,
    callFactory: Call.Factory
) : DataOkHttpUploaderV2(
    buildUrl(endpoint, TRACK_TYPE),
    clientToken,
    source,
    callFactory,
    CONTENT_TYPE_TEXT_UTF8,
    sdkLogger
) {

    companion object {
        private const val TRACK_TYPE = "spans"
    }
}
