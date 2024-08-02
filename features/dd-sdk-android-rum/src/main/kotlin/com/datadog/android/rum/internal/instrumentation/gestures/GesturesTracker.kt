/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.Context
import android.view.Window
import com.datadog.android.api.SdkCore
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface GesturesTracker {

    fun startTracking(
        window: Window?,
        context: Context,
        sdkCore: SdkCore
    )

    fun stopTracking(window: Window?, context: Context)
}
