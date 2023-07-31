/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.time

// there is no NoOpImplementation on purpose, we don't want to have 0 values for the
// case when this instance is used.
internal interface TimeProvider {

    fun getDeviceTimestamp(): Long

    fun getServerTimestamp(): Long

    fun getServerOffsetNanos(): Long

    fun getServerOffsetMillis(): Long
}
