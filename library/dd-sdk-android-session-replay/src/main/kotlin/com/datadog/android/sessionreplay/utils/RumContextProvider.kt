/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

/**
 * Provides the [SessionReplayRumContext] into the Session Replay module.
 */
interface RumContextProvider {
    /**
     * Returns the current RUM context as [SessionReplayRumContext].
     */
    fun getRumContext(): SessionReplayRumContext
}
