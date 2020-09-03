/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

internal data class ResourceTiming(
    val dnsStart: Long = 0L,
    val dnsDuration: Long = 0L,
    val connectStart: Long = 0L,
    val connectDuration: Long = 0L,
    val sslStart: Long = 0L,
    val sslDuration: Long = 0L,
    val firstByteStart: Long = 0L,
    val firstByteDuration: Long = 0L,
    val downloadStart: Long = 0L,
    val downloadDuration: Long = 0L
)
