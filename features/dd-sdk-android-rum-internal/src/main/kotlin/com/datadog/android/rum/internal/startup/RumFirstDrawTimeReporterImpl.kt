/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

// These types are public only so that :features:dd-sdk-android-rum and
// :features:dd-sdk-android-rum-prelaunch can share them across module boundaries. They are not
// part of the SDK's public API and carry no KDoc for that reason.
@file:Suppress(
    "PackageNameVisibility",
    "UndocumentedPublicClass",
    "UndocumentedPublicFunction",
    "UndocumentedPublicProperty"
)

package com.datadog.android.rum.internal.startup

import android.app.Activity
import android.os.Handler
import android.util.Log
import com.datadog.android.rum.internal.utils.window.RumWindowCallbacksRegistry

@Suppress("UnsafeThirdPartyFunctionCall")
class RumFirstDrawTimeReporterImpl(
    private val timeProviderNs: () -> Long,
    private val windowCallbacksRegistry: RumWindowCallbacksRegistry,
    private val handler: Handler,
    private val logTag: String = "DD/AppLaunch",
    private val warnLogger: (message: String, throwable: Throwable) -> Unit = { message, throwable ->
        Log.w(logTag, message, throwable)
    }
) : RumFirstDrawTimeReporter {

    override fun subscribeToFirstFrameDrawn(
        activity: Activity,
        callback: RumFirstDrawTimeReporter.Callback
    ): RumFirstDrawTimeReporter.Handle {
        return RumFirstDrawTimeReporterHandleImpl(
            callback = callback,
            activity = activity,
            warnLogger = warnLogger,
            timeProviderNs = timeProviderNs,
            windowCallbacksRegistry = windowCallbacksRegistry,
            handler = handler
        )
    }
}
