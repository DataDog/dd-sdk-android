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
    val viewId: String? = null,
    val viewName: String? = null,
    val viewUrl: String? = null,
    val actionId: String? = null,
    val sessionState: RumSessionScope.State = RumSessionScope.State.NOT_TRACKED,
    val viewType: RumViewScope.RumViewType = RumViewScope.RumViewType.NONE
) {

    companion object {
        val NULL_UUID = UUID(0, 0).toString()
    }
}
