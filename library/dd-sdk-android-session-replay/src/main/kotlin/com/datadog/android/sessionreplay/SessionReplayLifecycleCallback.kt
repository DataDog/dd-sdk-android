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
import com.datadog.android.sessionreplay.recorder.ScreenRecorder
import com.datadog.android.sessionreplay.recorder.SnapshotProducer
import com.datadog.android.sessionreplay.utils.RumContextProvider
import com.datadog.android.sessionreplay.utils.SessionReplayTimeProvider
import com.datadog.android.sessionreplay.writer.RecordWriter
import java.util.WeakHashMap
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The SessionReplay implementation of the [LifecycleCallback].
 */
@SuppressWarnings("UndocumentedPublicFunction")
class SessionReplayLifecycleCallback(
    rumContextProvider: RumContextProvider,
    privacy: SessionReplayPrivacy,
    serializedRecordWriter: SerializedRecordWriter
) : LifecycleCallback {

    private val timeProvider = SessionReplayTimeProvider()

    @Suppress("UnsafeThirdPartyFunctionCall") // workQueue can't be null
    private val processorExecutorService = ThreadPoolExecutor(
        CORE_DEFAULT_POOL_SIZE,
        Runtime.getRuntime().availableProcessors(),
        THREAD_POOL_MAX_KEEP_ALIVE_MS,
        TimeUnit.MILLISECONDS,
        LinkedBlockingDeque()
    )
    internal var recorder: Recorder = ScreenRecorder(
        SnapshotProcessor(
            rumContextProvider,
            timeProvider,
            processorExecutorService,
            RecordWriter(serializedRecordWriter)
        ),
        SnapshotProducer(privacy.mapper()),
        timeProvider
    )
    internal val resumedActivities: WeakHashMap<Activity, Any?> = WeakHashMap()

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

    override fun register(appContext: Application) {
        appContext.registerActivityLifecycleCallbacks(this)
    }

    override fun unregisterAndStopRecorders(appContext: Application) {
        appContext.unregisterActivityLifecycleCallbacks(this)
        resumedActivities.keys.forEach {
            it?.let { activity ->
                recorder.stopRecording(activity)
            }
        }
        resumedActivities.clear()
    }

    // endregion

    companion object {
        private val THREAD_POOL_MAX_KEEP_ALIVE_MS = TimeUnit.SECONDS.toMillis(5)
        private const val CORE_DEFAULT_POOL_SIZE = 1 // Only one thread will be kept alive
    }
}
