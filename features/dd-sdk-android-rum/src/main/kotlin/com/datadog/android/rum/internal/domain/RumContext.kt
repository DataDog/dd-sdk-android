/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.RumViewScope
import java.util.UUID

internal data class RumContext(
    val applicationId: String = NULL_UUID,
    val sessionId: String = NULL_UUID,
    val isSessionActive: Boolean = false,
    val viewId: String? = null,
    val viewName: String? = null,
    val viewUrl: String? = null,
    val actionId: String? = null,
    val sessionState: RumSessionScope.State = RumSessionScope.State.NOT_TRACKED,
    val sessionStartReason: RumSessionScope.StartReason = RumSessionScope.StartReason.USER_APP_LAUNCH,
    val viewType: RumViewScope.RumViewType = RumViewScope.RumViewType.NONE,
    val syntheticsTestId: String? = null,
    val syntheticsResultId: String? = null
) {

    fun toMap(): Map<String, Any?> {
        return mapOf(
            APPLICATION_ID to applicationId,
            SESSION_ID to sessionId,
            SESSION_STATE to sessionState.asString,
            VIEW_ID to viewId,
            VIEW_NAME to viewName,
            VIEW_URL to viewUrl,
            VIEW_TYPE to viewType.asString,
            ACTION_ID to actionId,
            SYNTHETICS_TEST_ID to syntheticsTestId,
            SYNTHETICS_RESULT_ID to syntheticsResultId
        )
    }

    companion object {
        val NULL_UUID = UUID(0, 0).toString()

        // be careful when changing values below, they may be indirectly referenced (as string
        // literal) from other modules
        const val APPLICATION_ID = "application_id"
        const val SESSION_ID = "session_id"
        const val SESSION_STATE = "session_state"
        const val SESSION_START_REASON = "session_start_reason"
        const val VIEW_ID = "view_id"
        const val VIEW_NAME = "view_name"
        const val VIEW_URL = "view_url"
        const val VIEW_TYPE = "view_type"
        const val ACTION_ID = "action_id"
        const val SYNTHETICS_TEST_ID = "synthetics_test_id"
        const val SYNTHETICS_RESULT_ID = "synthetics_result_id"

        fun fromFeatureContext(featureContext: Map<String, Any?>): RumContext {
            val applicationId = featureContext[APPLICATION_ID] as? String
            val sessionId = featureContext[SESSION_ID] as? String
            val sessionState = RumSessionScope.State.fromString(
                featureContext[SESSION_STATE] as? String
            )
            val sessionStartReason = RumSessionScope.StartReason.fromString(
                featureContext[SESSION_START_REASON] as? String
            )
            val viewId = featureContext[VIEW_ID] as? String
            val viewName = featureContext[VIEW_NAME] as? String
            val viewUrl = featureContext[VIEW_URL] as? String
            val viewType = RumViewScope.RumViewType.fromString(featureContext[VIEW_TYPE] as? String)
            val actionId = featureContext[ACTION_ID] as? String
            val syntheticsTestId = featureContext[SYNTHETICS_TEST_ID] as? String
            val syntheticsResultId = featureContext[SYNTHETICS_RESULT_ID] as? String

            return RumContext(
                applicationId = applicationId ?: NULL_UUID,
                sessionId = sessionId ?: NULL_UUID,
                sessionState = sessionState ?: RumSessionScope.State.NOT_TRACKED,
                sessionStartReason = sessionStartReason ?: RumSessionScope.StartReason.USER_APP_LAUNCH,
                viewId = viewId,
                viewName = viewName,
                viewUrl = viewUrl,
                viewType = viewType ?: RumViewScope.RumViewType.NONE,
                actionId = actionId,
                syntheticsTestId = syntheticsTestId,
                syntheticsResultId = syntheticsResultId
            )
        }
    }
}
