/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import java.util.concurrent.atomic.AtomicReference

/**
 * Event sent by [ApmNetworkInstrumentation] to [TracingFeature] via `sendEvent`
 * to register an [AtomicReference] that should be kept up-to-date with the current
 * RUM session sample rate.
 *
 * TracingFeature updates all registered references whenever the RUM feature context
 * changes, ensuring that [com.datadog.android.trace.DeterministicTraceSampler] can
 * read the latest session sample rate with a simple volatile read (no locking).
 *
 * @param ref the [AtomicReference] that TracingFeature will update with the
 * latest RUM session sample rate.
 */
internal data class SessionSampleRateRegistrationEvent(val ref: AtomicReference<Float>)
