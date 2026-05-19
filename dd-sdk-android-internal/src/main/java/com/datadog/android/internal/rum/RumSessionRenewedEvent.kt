/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.rum

/**
 * Event broadcast by RUM whenever a session is renewed (created for the first time, or renewed
 * after inactivity timeout, max-duration timeout, or an explicit reset).
 *
 * @param sessionId The ID of the newly renewed RUM session.
 * @param sessionSampleRate The RUM session sample rate (percentage, 0..100) configured for this
 * session. Cross-feature samplers (e.g. continuous profiling) combine this with their own rate.
 */
data class RumSessionRenewedEvent(
    val sessionId: String,
    val sessionSampleRate: Float
)
