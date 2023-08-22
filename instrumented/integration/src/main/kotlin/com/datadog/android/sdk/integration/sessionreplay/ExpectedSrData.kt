/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import com.google.gson.JsonElement

internal data class ExpectedSrData(
    val sessionId: String,
    val applicationId: String,
    val viewId: String,
    val records: List<JsonElement>
)
