/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.res.Configuration
import android.content.res.Resources

/** Isolates the current device orientation so it can be substituted in tests. */
internal fun interface OrientationProvider {
    fun currentOrientation(): Int
}

/**
 * Reads [Resources.getSystem] rather than requiring an Activity/Application context - it reflects
 * true device rotation without needing any context threaded into the composition pipeline. Falls
 * back to a constant sentinel on failure rather than throwing: a session-replay signal must never
 * be able to crash the host app, and a constant value simply makes orientation-change gating inert
 * (the other full-snapshot triggers - new view, periodic checkpoint - are unaffected) rather than
 * unsafe in either direction.
 */
internal class DefaultOrientationProvider : OrientationProvider {
    @Suppress("TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall")
    override fun currentOrientation(): Int = try {
        Resources.getSystem().configuration.orientation
    } catch (@Suppress("SwallowedException") e: Exception) {
        Configuration.ORIENTATION_UNDEFINED
    }
}
