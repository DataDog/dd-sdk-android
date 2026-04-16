/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import android.os.Handler
import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.utils.window.RumWindowCallbacksRegistry

internal class RumFirstDrawTimeReporterImpl(
    private val internalLogger: InternalLogger,
    private val timeProviderNs: () -> Long,
    private val windowCallbacksRegistry: RumWindowCallbacksRegistry,
    private val handler: Handler
) : RumFirstDrawTimeReporter {

    override fun subscribeToFirstFrameDrawn(
        activity: Activity,
        callback: RumFirstDrawTimeReporter.Callback
    ): RumFirstDrawTimeReporter.Handle {
        return RumFirstDrawTimeReporterHandleImpl(
            callback = callback,
            activity = activity,
            internalLogger = internalLogger,
            timeProviderNs = timeProviderNs,
            windowCallbacksRegistry = windowCallbacksRegistry,
            handler = handler
        )
    }
}
