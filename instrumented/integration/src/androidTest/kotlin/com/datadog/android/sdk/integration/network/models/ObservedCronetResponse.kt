/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sdk.integration.network.models

internal data class ObservedCronetResponse(
    val path: String,
    val statusCode: Int,
    val wasCached: Boolean,
    val receivedByteCount: Long,
    val urlChainSize: Int,
    val metricsReceivedByteCount: Long? = null
)
