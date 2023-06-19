/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

/**
 * This object contains constant values for all the Datadog NTP Endpoint urls used in the SDK.
 */
internal enum class DatadogNtpEndpoint(val host: String) {

    /**
     * Endpoint for the Network Time Protocol time syncing.
     */
    NTP_0("0.datadog.pool.ntp.org"),

    /**
     * Endpoint for the Network Time Protocol time syncing.
     */
    NTP_1("1.datadog.pool.ntp.org"),

    /**
     * Endpoint for the Network Time Protocol time syncing.
     */
    NTP_2("2.datadog.pool.ntp.org"),

    /**
     * Endpoint for the Network Time Protocol time syncing.
     */
    NTP_3("3.datadog.pool.ntp.org")
}
