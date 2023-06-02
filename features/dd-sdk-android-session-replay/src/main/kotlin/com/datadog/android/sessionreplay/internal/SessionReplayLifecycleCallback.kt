/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.fragment.app.FragmentActivity
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.processor.RecordedDataProcessor
import com.datadog.android.sessionreplay.internal.recorder.ComposedOptionSelectorDetector
import com.datadog.android.sessionreplay.internal.recorder.DefaultOptionSelectorDetector
import com.datadog.android.sessionreplay.internal.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.internal.recorder.SnapshotProducer
import com.datadog.android.sessionreplay.internal.recorder.TreeViewTraversal
import com.datadog.android.sessionreplay.internal.recorder.ViewOnDrawInterceptor
import com.datadog.android.sessionreplay.internal.recorder.WindowCallbackInterceptor
import com.datadog.android.sessionreplay.internal.recorder.callback.RecorderFragmentLifecycleCallback
import com.datadog.android.sessionreplay.internal.recorder.mapper.MapperTypeWrapper
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.TimeProvider
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The SessionReplay implementation of the [LifecycleCallback].
 */
internal class SessionReplayLifecycleCallback(
    rumContextProvider: RumContextProvider,
    privacy: SessionReplayPrivacy,
    recordWriter: RecordWriter,
    timeProvider: TimeProvider,
    recordCallback: RecordCallback = NoOpRecordCallback(),
    customMappers: List<MapperTypeWrapper> = emptyList(),
    customOptionSelectorDetectors: List<OptionSelectorDetector> = emptyList()
) : LifecycleCallback {

    @Suppress("UnsafeThirdPartyFunctionCall") // workQueue can't be null
    private val processorExecutorService = ThreadPoolExecutor(
        CORE_DEFAULT_POOL_SIZE,
        Runtime.getRuntime().availableProcessors(),
        THREAD_POOL_MAX_KEEP_ALIVE_MS,
        TimeUnit.MILLISECONDS,
        LinkedBlockingDeque()
    )
    internal val processor = RecordedDataProcessor(
        rumContextProvider,
        timeProvider,
        processorExecutorService,
        recordWriter,
        recordCallback
    )
    internal var viewOnDrawInterceptor = ViewOnDrawInterceptor(
        processor,
        SnapshotProducer(
            TreeViewTraversal(customMappers + privacy.mappers()),
            ComposedOptionSelectorDetector(
                customOptionSelectorDetectors + DefaultOptionSelectorDetector()
            )
        )
    )

    internal var windowCallbackInterceptor =
        WindowCallbackInterceptor(processor, viewOnDrawInterceptor, timeProvider)

    // region callback

    @MainThread
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // No Op
        if (activity is FragmentActivity) {
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                RecorderFragmentLifecycleCallback(windowCallbackInterceptor),
                true
            )
        }
    }

    @MainThread
    override fun onActivityStarted(activity: Activity) {
        // No Op
    }

    @MainThread
    override fun onActivityResumed(activity: Activity) {
        activity.window?.let {
            viewOnDrawInterceptor.intercept(listOf(it.decorView), activity)
            windowCallbackInterceptor.intercept(listOf(it), activity)
        }
    }

    @MainThread
    override fun onActivityPaused(activity: Activity) {
        activity.window?.let {
            viewOnDrawInterceptor.stopIntercepting(listOf(it.decorView))
            windowCallbackInterceptor.stopIntercepting(listOf(it))
        }
    }

    @MainThread
    override fun onActivityStopped(activity: Activity) {
        // No Op
    }

    @MainThread
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // No Op
    }

    @MainThread
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
        viewOnDrawInterceptor.stopIntercepting()
        windowCallbackInterceptor.stopIntercepting()
    }

    // endregion

    companion object {
        private val THREAD_POOL_MAX_KEEP_ALIVE_MS = TimeUnit.SECONDS.toMillis(5)
        private const val CORE_DEFAULT_POOL_SIZE = 1 // Only one thread will be kept alive
    }
}
