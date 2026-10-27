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
 * @property applicationId Configured RUM application identifier.
 * @property session Session information, or null when no session is available.
 * @property viewId Identifier of the associated view, if any.
 * @property viewName Name of the associated view, if any.
 * @property viewUrl URL of the associated view, if any.
 * @property actionId Identifier of the associated action, if any.
 * @property viewType Category of the associated view, or [RumViewType.NONE] when unavailable.
 * @property syntheticsTestId Identifier of the Synthetics test, if any.
 * @property syntheticsResultId Identifier of the Synthetics test result, if any.
 * @property viewTimestamp Start timestamp of the view in milliseconds.
 * @property viewTimestampOffset Server time offset used by the view, in milliseconds.
 * @property hasReplay Whether the view has Session Replay data.
 */
data class RumContext(
    val applicationId: String,
    val session: RumSessionContext? = null,
    val viewId: String? = null,
    val viewName: String? = null,
    val viewUrl: String? = null,
    val actionId: String? = null,
    val viewType: RumViewType = RumViewType.NONE,
    val syntheticsTestId: String? = null,
    val syntheticsResultId: String? = null,
    val viewTimestamp: Long = 0L,
    val viewTimestampOffset: Long = 0L,
    val hasReplay: Boolean = false
) {

    companion object {
        /** Legacy sentinel used at boundaries that require an identifier. */
        const val NULL_UUID: String = RumSessionConstants.EMPTY_RUM_SESSION_ID

        /** Sampling percentage that keeps every session. */
        const val SAMPLE_ALL_RATE: Float = 100f
    }
}

/** Returns the published RUM snapshot, or null if the entry is absent or has an unexpected type. */
fun Map<String, Any?>.rumContext(): RumContext? =
    this[FeatureContextKeys.RUM_CONTEXT] as? RumContext
