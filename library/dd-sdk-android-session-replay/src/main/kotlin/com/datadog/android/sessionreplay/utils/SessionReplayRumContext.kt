/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import java.util.UUID

/**
 * Provides the necessary RUM context for Session Replay records.
 * @param applicationId the RUM application id
 * @param sessionId the current RUM session id
 * @param viewId the current RUM view id
 */
data class SessionReplayRumContext(
    val applicationId: String = NULL_UUID,
    val sessionId: String = NULL_UUID,
    val viewId: String = NULL_UUID
) {

    internal fun isNotValid(): Boolean =
        applicationId == NULL_UUID ||
            sessionId == NULL_UUID ||
            viewId == NULL_UUID

    internal fun isValid(): Boolean =
        applicationId != NULL_UUID &&
            sessionId != NULL_UUID &&
            viewId != NULL_UUID

    companion object {
        private val NULL_UUID = UUID(0, 0).toString()
    }
}
