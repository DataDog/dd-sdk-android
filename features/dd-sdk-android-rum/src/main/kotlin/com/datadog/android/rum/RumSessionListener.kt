/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

/**
 * An interface to get informed whenever a new session is starting,
 * providing you with Datadog's session id.
 */
interface RumSessionListener {

    /**
     * Called whenever a new session is started.
     * @param sessionId the Session's id (matching the `session.id` attribute in Datadog's RUM events)
     * @param isDiscarded whether or not the session is discarded by the sample rate
     * (when `true` it means no event in this session will be kept).
     */
    fun onSessionStarted(sessionId: String, isDiscarded: Boolean)
}
