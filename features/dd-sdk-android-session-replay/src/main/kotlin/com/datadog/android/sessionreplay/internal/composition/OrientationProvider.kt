/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.res.Resources

/** Isolates the current device orientation so it can be substituted in tests. */
internal fun interface OrientationProvider {
    fun currentOrientation(): Int
}

/** The completion queue reports failures; an unsuccessful read must not masquerade as an orientation value. */
internal class DefaultOrientationProvider : OrientationProvider {
    @Suppress("UnsafeThirdPartyFunctionCall") // System resources are read at the completion queue's error boundary.
    override fun currentOrientation(): Int = Resources.getSystem().configuration.orientation
}
