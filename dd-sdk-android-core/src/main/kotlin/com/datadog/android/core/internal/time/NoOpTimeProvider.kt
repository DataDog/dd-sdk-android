/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.time

internal class NoOpTimeProvider : TimeProvider {
    override fun getDeviceTimestamp(): Long = System.currentTimeMillis()

    override fun getServerTimestamp(): Long = System.currentTimeMillis()

    override fun getServerOffsetNanos(): Long = 0L

    override fun getServerOffsetMillis(): Long = 0L
}
