/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.flags.model.FlagsClientEvent

/** Receives the first accepted assignment installation for a client. */
fun interface FirstFlagsCallback {
    /**
     * Called at most once, outside SDK locks, with a usable [client].
     * [event] contains the complete first installed key list, including an empty list.
     * Client reads see the latest assignments, which may have advanced since this event.
     */
    fun onFirstFlags(client: FlagsClient, event: FlagsClientEvent)
}
