/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.flags.model.FlagsClientEventDetails

/** Receives events for a single native client. Keep handlers short and non-blocking. */
fun interface FlagsClientEventHandler {
    /** Called after the assignments are installed, on the thread draining the client's event queue. */
    fun handle(details: FlagsClientEventDetails)
}
