/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.time

// there is no NoOpImplementation on purpose, we don't want to have 0 values for the
// case when this instance is used.
/**
 * Interface to provide the current time in both device and server time references.
 */
interface TimeProvider {

    /**
     * Returns the current device timestamp in milliseconds.
     */
    fun getDeviceTimestamp(): Long

    /**
     * Returns the current server timestamp in milliseconds.
     */
    fun getServerTimestamp(): Long

    /**
     * Returns the offset between the device and server time references in nanoseconds.
     */
    fun getServerOffsetNanos(): Long

    /**
     * Returns the offset between the device and server time references in milliseconds.
     */
    fun getServerOffsetMillis(): Long
}
