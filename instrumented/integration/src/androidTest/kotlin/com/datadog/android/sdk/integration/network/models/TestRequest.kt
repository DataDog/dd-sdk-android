/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sdk.integration.network.models

data class TestRequest(
    val url: String,
    val method: String,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: String? = null
)
