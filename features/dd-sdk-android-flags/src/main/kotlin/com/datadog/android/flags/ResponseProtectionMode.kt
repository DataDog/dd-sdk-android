/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * The enforcement mode of a [ResponseProtection].
 */
enum class ResponseProtectionMode {
    /**
     * The SDK rejects new flag assignment data unless the selected protection method succeeds.
     * The SDK does not fall back to an unprotected request or to another protection method.
     */
    REQUIRED
}
