/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.rum

/**
 * Snapshot of an existing RUM session, including sampled-out and expired sessions.
 *
 * @property id RUM session identifier.
 * @property state Tracking state of the session.
 * @property startReason Trigger that created the session.
 * @property isActive Whether the session scope is active.
 * @property sampleRate Session sampling rate as a percentage, from 0 to 100.
 * @property hasTimeseries Whether the session has collected timeseries data.
 */
data class RumSessionContext(
    val id: String,
    val state: RumSessionState,
    val startReason: RumSessionStartReason,
    val isActive: Boolean,
    val sampleRate: Float,
    val hasTimeseries: Boolean
)
