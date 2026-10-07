/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.flags.model.FlagsClientEvent

/** Receives a flag client event. */
fun interface FlagsClientEventListener {
    /**
     * Handles [event]. [FlagsClient.onFirstFlags] replays an available event on the registering
     * thread; pending delivery runs on an SDK background worker.
     * Dispatch UI work to the appropriate thread.
     */
    fun onEvent(event: FlagsClientEvent)
}
