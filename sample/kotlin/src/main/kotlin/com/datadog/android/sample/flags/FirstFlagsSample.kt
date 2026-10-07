/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.flags

import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.model.FlagsClientEvent

/** Logs the first event and evaluates an example flag directly through the assigned client. */
internal fun logAndEvaluateFirstFlags(
    client: FlagsClient,
    event: FlagsClientEvent,
    log: (String) -> Unit
) {
    log("Installed flag keys: ${event.flagsChanged ?: "<absent>"}")
    val details = client.resolve("my-flag-key", false)
    log("my-flag-key = ${details.value} (reason=${details.reason})")
}
