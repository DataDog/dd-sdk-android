/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.flags

import android.content.SharedPreferences
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.model.FlagsClientEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Called after client construction; awaits the first event without blocking the SDK callback. */
internal fun CoroutineScope.logAndEvaluateFirstFlags(
    client: FlagsClient,
    firstFlags: Deferred<FlagsClientEvent>,
    selection: SharedPreferences,
    log: (String) -> Unit
): Job = launch {
    val event = firstFlags.await()
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
