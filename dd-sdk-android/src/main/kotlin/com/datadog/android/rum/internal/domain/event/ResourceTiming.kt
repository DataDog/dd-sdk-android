/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

internal data class ResourceTiming(
    val dnsStart: Long,
    val dnsDuration: Long,
    val connectStart: Long,
    val connectDuration: Long,
    val sslStart: Long,
    val sslDuration: Long,
    val firstByteStart: Long,
    val firstByteDuration: Long,
    val downloadStart: Long,
    val downloadDuration: Long
)
