/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.webview

import com.datadog.android.core.internal.utils.sdkLogger
import com.google.gson.JsonObject

internal class LogsEventConsumer {

    fun consume(event: JsonObject) {
        // just to make the code compile without warning. This will be removed in the next PR.
        sdkLogger.i(event.toString())
    }
}
