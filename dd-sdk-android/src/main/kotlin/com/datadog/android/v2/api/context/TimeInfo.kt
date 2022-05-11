/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api.context

/**
 * Holds information about the current local and server time.
 * @property deviceTimeNs the current time as known by the System on the device (nanoseconds)
 * @property serverTimeNs the current time synchronized with our NTP server(s) (nanoseconds)
 */
data class TimeInfo(
    val deviceTimeNs: Long,
    val serverTimeNs: Long
)
