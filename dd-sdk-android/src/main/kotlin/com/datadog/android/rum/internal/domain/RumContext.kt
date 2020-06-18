/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import java.util.UUID

internal data class RumContext(
    val applicationId: String = NULL_SESSION_ID,
    val sessionId: String = NULL_SESSION_ID,
    val viewId: String? = null,
    val viewUrl: String? = null,
    val actionId: String? = null
) {

    companion object {
        val NULL_SESSION_ID = UUID(0, 0).toString()
    }
}
