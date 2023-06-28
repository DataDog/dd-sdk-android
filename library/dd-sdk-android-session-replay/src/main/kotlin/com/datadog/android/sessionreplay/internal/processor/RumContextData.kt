/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext

internal data class RumContextData(
    internal val timestamp: Long,
    internal val newRumContext: SessionReplayRumContext,
    internal val prevRumContext: SessionReplayRumContext
)
