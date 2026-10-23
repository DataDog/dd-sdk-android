/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.rum

import com.datadog.android.internal.FeatureContextKeys

/**
 * Snapshot of RUM state exchanged between SDK features.
 *
 * Public for access across SDK modules. The data-class contract preserves the copying and
 * equality semantics used by RUM scopes; it is an SDK-internal API.
 *
 * @property applicationId RUM application identifier, or [NULL_UUID] when unavailable.
 * @property sessionId RUM session identifier, or [NULL_UUID] before a session is created.
 * @property isSessionActive Whether the session scope is active.
 * @property viewId Identifier of the associated view, if any.
 * @property viewName Name of the associated view, if any.
 * @property viewUrl URL of the associated view, if any.
 * @property actionId Identifier of the associated action, if any.
 * @property sessionState Tracking state of the session.
 * @property sessionStartReason Trigger that created the session.
 * @property viewType Category of the associated view, or [RumViewType.NONE] when unavailable.
 * @property syntheticsTestId Identifier of the Synthetics test, if any.
 * @property syntheticsResultId Identifier of the Synthetics test result, if any.
 * @property viewTimestamp Start timestamp of the view in milliseconds.
 * @property viewTimestampOffset Server time offset used by the view, in milliseconds.
 * @property hasReplay Whether the view has Session Replay data.
 * @property hasTimeseries Whether the session has collected timeseries data.
 * @property sessionSampleRate Session sampling rate as a percentage, from 0 to 100.
 */
data class RumContext(
    val applicationId: String = NULL_UUID,
    val sessionId: String = NULL_UUID,
    val isSessionActive: Boolean = false,
    val viewId: String? = null,
    val viewName: String? = null,
    val viewUrl: String? = null,
    val actionId: String? = null,
    val sessionState: RumSessionState = RumSessionState.NOT_TRACKED,
    val sessionStartReason: RumSessionStartReason = RumSessionStartReason.USER_APP_LAUNCH,
    val viewType: RumViewType = RumViewType.NONE,
    val syntheticsTestId: String? = null,
    val syntheticsResultId: String? = null,
    val viewTimestamp: Long = 0L,
    val viewTimestampOffset: Long = 0L,
    val hasReplay: Boolean = false,
    val hasTimeseries: Boolean = false,
    val sessionSampleRate: Float = SAMPLE_ALL_RATE
) {

    companion object {
        /** Sentinel used when an application or session identifier is unavailable. */
        const val NULL_UUID: String = RumSessionConstants.EMPTY_RUM_SESSION_ID

        /** Sampling percentage that keeps every session. */
        const val SAMPLE_ALL_RATE: Float = 100f
    }
}

/** Returns the published RUM snapshot, or null if the entry is absent or has an unexpected type. */
fun Map<String, Any?>.rumContext(): RumContext? =
    this[FeatureContextKeys.RUM_CONTEXT] as? RumContext
