/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.app.Application
import android.view.Window

/**
 * The Session Replay related LifecycleCallback interface.
 * It will be registered as `Application.ActivityLifecycleCallbacks` and will decide when the
 * activity can be recorded or not based on the `onActivityResume`, `onActivityPause` callbacks.
 * This is only meant for internal usage and later will change visibility from public to internal.
 */
internal interface LifecycleCallback : Application.ActivityLifecycleCallbacks {

    fun getCurrentWindows(): List<Window>
}
