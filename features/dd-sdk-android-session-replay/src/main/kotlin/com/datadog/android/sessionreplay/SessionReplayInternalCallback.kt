/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.app.Activity
import com.datadog.android.sessionreplay.internal.recorder.SessionReplayRecorder
import com.datadog.tools.annotation.NoOpImplementation

/**
 * Defines points of change for clients to override existing functionality with platform specific code.
 */
@NoOpImplementation
interface SessionReplayInternalCallback {
    /**
     * Retrieves the current activity, allowing clients to pass it when needed.
     * This is used by [SessionReplayRecorder] to register fragment lifecycle callbacks
     * that were missed because the client was initialized after the `Application.onCreate` phase.
     */
    fun getCurrentActivity(): Activity?
}
