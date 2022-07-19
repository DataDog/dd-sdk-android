/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.datadog.android.sessionreplay.processor.SnapshotProcessor
import com.datadog.android.sessionreplay.recorder.Recorder
import com.datadog.android.sessionreplay.recorder.SnapshotRecorder
import java.util.WeakHashMap

class SessionReplayLifecycleCallback :
    Application.ActivityLifecycleCallbacks {

    internal var recorder: Recorder = SnapshotRecorder(SnapshotProcessor())
    private val resumedActivities: WeakHashMap<Activity, Any?> = WeakHashMap()

    // region callback

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // No Op
    }

    override fun onActivityStarted(activity: Activity) {
        // No Op
    }

    override fun onActivityResumed(activity: Activity) {
        recorder.startRecording(activity)
        resumedActivities[activity] = null
    }

    override fun onActivityPaused(activity: Activity) {
        recorder.stopRecording(activity)
        resumedActivities.remove(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        // No Op
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // No Op
    }

    override fun onActivityDestroyed(activity: Activity) {
        // No Op
    }

    // endregion

    // region Api

    fun register(appContext: Application) {
        appContext.registerActivityLifecycleCallbacks(this)
    }

    fun unregisterAndStopRecorders(appContext: Application) {
        appContext.unregisterActivityLifecycleCallbacks(this)
        resumedActivities.keys.forEach {
            it?.let { activity ->
                recorder.startRecording(activity)
            }
        }
    }

    // endregion
}
