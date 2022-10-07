/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.app.Application

/**
 * The Session Replay related LifecycleCallback interface.
 * It will be registered as `Application.ActivityLifecycleCallbacks` and will decide when the
 * activity can be recorded or not based on the `onActivityResume`, `onActivityPause` callbacks.
 * This is only meant for internal usage and later will change visibility from public to internal.
 */
interface LifecycleCallback : Application.ActivityLifecycleCallbacks {

    /**
     * Registers the callback on the Application lifecycle.
     * @param appContext
     */
    fun register(appContext: Application)

    /**
     * Unregister the callback and stops any related recorders that were previously started.
     * @param appContext
     */
    fun unregisterAndStopRecorders(appContext: Application)
}
