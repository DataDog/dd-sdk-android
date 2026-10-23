/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.rum.RumContext

internal fun MutableMap<String, Any?>.setRumContext(context: RumContext?) {
    clear()
    if (context == null) return

    this[FeatureContextKeys.RUM_CONTEXT] = context

    // Keep the flat fields for existing Android readers and Flutter Session Replay.
    // TODO RUM-19153: Remove after all supported consumers have migrated to the typed snapshot.
    this["application_id"] = context.applicationId
    this["session_id"] = context.sessionId
    this["session_active"] = context.isSessionActive
    this["session_state"] = context.sessionState.asString
    this["session_start_reason"] = context.sessionStartReason.asString
    this["view_id"] = context.viewId
    this["view_name"] = context.viewName
    this["view_url"] = context.viewUrl
    this["view_type"] = context.viewType.asString
    this["action_id"] = context.actionId
    this["synthetics_test_id"] = context.syntheticsTestId
    this["synthetics_result_id"] = context.syntheticsResultId
    this["view_timestamp"] = context.viewTimestamp
    this["view_has_replay"] = context.hasReplay
    this["session_has_timeseries"] = context.hasTimeseries
    this["view_timestamp_offset"] = context.viewTimestampOffset
    this["session_sample_rate"] = context.sessionSampleRate
}
