/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.Window
import com.datadog.android.sessionreplay.internal.NoOpRecordCallback
import com.datadog.android.sessionreplay.internal.RecordCallback
import com.datadog.android.sessionreplay.internal.RecordWriter
import com.datadog.android.sessionreplay.internal.SessionReplayLifecycleCallback
import com.datadog.android.sessionreplay.internal.processor.RecordedDataProcessor
import com.datadog.android.sessionreplay.internal.recorder.ComposedOptionSelectorDetector
import com.datadog.android.sessionreplay.internal.recorder.DefaultOptionSelectorDetector
import com.datadog.android.sessionreplay.internal.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.internal.recorder.SnapshotProducer
import com.datadog.android.sessionreplay.internal.recorder.TreeViewTraversal
import com.datadog.android.sessionreplay.internal.recorder.ViewOnDrawInterceptor
import com.datadog.android.sessionreplay.internal.recorder.WindowCallbackInterceptor
import com.datadog.android.sessionreplay.internal.recorder.WindowInspector
import com.datadog.android.sessionreplay.internal.recorder.callback.OnWindowRefreshedCallback
import com.datadog.android.sessionreplay.internal.recorder.mapper.MapperTypeWrapper
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.TimeProvider
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class SessionReplayRecorder(
    private val appContext: Application,
    rumContextProvider: RumContextProvider,
    privacy: SessionReplayPrivacy,
    recordWriter: RecordWriter,
    timeProvider: TimeProvider,
    recordCallback: RecordCallback = NoOpRecordCallback(),
    customMappers: List<MapperTypeWrapper> = emptyList(),
    customOptionSelectorDetectors: List<OptionSelectorDetector> = emptyList(),
    private val windowInspector: WindowInspector = WindowInspector
) : OnWindowRefreshedCallback, Recorder {

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
    private var shouldRecord = false
    private val uiHandler = Handler(Looper.getMainLooper())
    internal var sessionReplayLifecycleCallback = SessionReplayLifecycleCallback(this)

    override fun registerCallbacks(appContext: Application) {
        sessionReplayLifecycleCallback.register(appContext)
    }

    override fun resumeRecorders() {
        uiHandler.post {
            shouldRecord = true
            val windows = sessionReplayLifecycleCallback.getCurrentActiveWindows()
            val decorViews = windowInspector.getGlobalWindowViews()
            windowCallbackInterceptor.intercept(windows, appContext)
            viewOnDrawInterceptor.intercept(decorViews, appContext)
        }
    }

    override fun stopRecorders() {
        uiHandler.post {
            viewOnDrawInterceptor.stopIntercepting()
            windowCallbackInterceptor.stopIntercepting()
            shouldRecord = false
        }
    }

    override fun onWindowsAdded(windows: List<Window>) {
        if (shouldRecord) {
            val decorViews = windowInspector.getGlobalWindowViews()
            windowCallbackInterceptor.intercept(windows, appContext)
            viewOnDrawInterceptor.intercept(decorViews, appContext)
        }
    }

    override fun onWindowsRemoved(windows: List<Window>) {
        if (shouldRecord) {
            val decorViews = windowInspector.getGlobalWindowViews()
            windowCallbackInterceptor.stopIntercepting(windows)
            viewOnDrawInterceptor.intercept(decorViews, appContext)
        }
    }

    companion object {
        private val THREAD_POOL_MAX_KEEP_ALIVE_MS = TimeUnit.SECONDS.toMillis(5)
        private const val CORE_DEFAULT_POOL_SIZE = 1 // Only one thread will be kept alive
    }
}
