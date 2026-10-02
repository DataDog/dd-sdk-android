/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.flags

import android.content.SharedPreferences
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.model.FlagsClientEvent

/** Logs the first event and evaluates the saved flag directly through the assigned client. */
internal fun logAndEvaluateFirstFlags(
    client: FlagsClient,
    event: FlagsClientEvent,
    selection: SharedPreferences,
    log: (String) -> Unit
) {
    log("Installed flag keys: ${event.flagsChanged ?: "<absent>"}")
    val key = selection.getString(OpenFeatureFragment.FIRST_FLAGS_KEY, null)
    if (key == null) {
        log("Select and evaluate a Boolean flag in OpenFeature, then relaunch the app.")
    } else {
        val defaultValue = selection.getBoolean(OpenFeatureFragment.FIRST_FLAGS_DEFAULT, false)
        val details = client.resolve(key, defaultValue)
        log("$key = ${details.value} (reason=${details.reason})")
    }
}
