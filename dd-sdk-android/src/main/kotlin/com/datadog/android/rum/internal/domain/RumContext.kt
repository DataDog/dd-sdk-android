/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import java.util.UUID

internal data class RumContext(
    val applicationId: UUID = UUID(0, 0),
    val sessionId: UUID = UUID(0, 0),
    val viewId: UUID? = null
)
