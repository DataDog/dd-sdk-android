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
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import com.datadog.android.sessionreplay.internal.LifecycleCallback
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

internal class SessionReplayRecorder : OnWindowRefreshedCallback, Recorder {

    private val appContext: Application
    private val rumContextProvider: RumContextProvider
    private val privacy: SessionReplayPrivacy
    private val recordWriter: RecordWriter
    private val timeProvider: TimeProvider
    private val recordCallback: RecordCallback
    private val customMappers: List<MapperTypeWrapper>
    private val customOptionSelectorDetectors: List<OptionSelectorDetector>
    private val windowInspector: WindowInspector
    private val windowCallbackInterceptor: WindowCallbackInterceptor
    private val sessionReplayLifecycleCallback: LifecycleCallback
    private val processor: RecordedDataProcessor
    private val viewOnDrawInterceptor: ViewOnDrawInterceptor
    private val uiHandler: Handler

    @Suppress("UnsafeThirdPartyFunctionCall") // workQueue can't be null
    private val processorExecutorService = ThreadPoolExecutor(
        CORE_DEFAULT_POOL_SIZE,
        CORE_DEFAULT_POOL_SIZE,
        THREAD_POOL_MAX_KEEP_ALIVE_MS,
        TimeUnit.MILLISECONDS,
        LinkedBlockingDeque()
    )
    private var shouldRecord = false

    constructor(
        appContext: Application,
        rumContextProvider: RumContextProvider,
        privacy: SessionReplayPrivacy,
        recordWriter: RecordWriter,
        timeProvider: TimeProvider,
        recordCallback: RecordCallback = NoOpRecordCallback(),
        customMappers: List<MapperTypeWrapper> = emptyList(),
        customOptionSelectorDetectors: List<OptionSelectorDetector> = emptyList(),
        windowInspector: WindowInspector = WindowInspector
    ) {
        this.appContext = appContext
        this.rumContextProvider = rumContextProvider
        this.privacy = privacy
        this.recordWriter = recordWriter
        this.timeProvider = timeProvider
        this.recordCallback = recordCallback
        this.customMappers = customMappers
        this.customOptionSelectorDetectors = customOptionSelectorDetectors
        this.windowInspector = windowInspector
        this.processor = RecordedDataProcessor(
            rumContextProvider,
            timeProvider,
            processorExecutorService,
            recordWriter,
            recordCallback
        )
        this.viewOnDrawInterceptor = ViewOnDrawInterceptor(
            processor,
            SnapshotProducer(
                TreeViewTraversal(customMappers + privacy.mappers()),
                ComposedOptionSelectorDetector(
                    customOptionSelectorDetectors + DefaultOptionSelectorDetector()
                )
            )
        )
        this.windowCallbackInterceptor = WindowCallbackInterceptor(processor, viewOnDrawInterceptor, timeProvider)
        this.sessionReplayLifecycleCallback = SessionReplayLifecycleCallback(this)
        this.uiHandler = Handler(Looper.getMainLooper())
    }

    @VisibleForTesting
    constructor(
        appContext: Application,
        rumContextProvider: RumContextProvider,
        privacy: SessionReplayPrivacy,
        recordWriter: RecordWriter,
        timeProvider: TimeProvider,
        recordCallback: RecordCallback = NoOpRecordCallback(),
        customMappers: List<MapperTypeWrapper> = emptyList(),
        customOptionSelectorDetectors: List<OptionSelectorDetector>,
        windowInspector: WindowInspector = WindowInspector,
        windowCallbackInterceptor: WindowCallbackInterceptor,
        sessionReplayLifecycleCallback: LifecycleCallback,
        viewOnDrawInterceptor: ViewOnDrawInterceptor,
        processor: RecordedDataProcessor,
        uiHandler: Handler
    ) {
        this.appContext = appContext
        this.rumContextProvider = rumContextProvider
        this.privacy = privacy
        this.recordWriter = recordWriter
        this.timeProvider = timeProvider
        this.recordCallback = recordCallback
        this.customMappers = customMappers
        this.customOptionSelectorDetectors = customOptionSelectorDetectors
        this.windowInspector = windowInspector
        this.processor = processor
        this.viewOnDrawInterceptor = viewOnDrawInterceptor
        this.windowCallbackInterceptor = windowCallbackInterceptor
        this.sessionReplayLifecycleCallback = sessionReplayLifecycleCallback
        this.uiHandler = uiHandler
    }

    @MainThread
    override fun registerCallbacks() {
        appContext.registerActivityLifecycleCallbacks(sessionReplayLifecycleCallback)
    }

    @MainThread
    override fun unregisterCallbacks() {
        appContext.unregisterActivityLifecycleCallbacks(sessionReplayLifecycleCallback)
    }

    @MainThread
    override fun resumeRecorders() {
        uiHandler.post {
            shouldRecord = true
            val windows = sessionReplayLifecycleCallback.getCurrentWindows()
            val decorViews = windowInspector.getGlobalWindowViews()
            windowCallbackInterceptor.intercept(windows, appContext)
            viewOnDrawInterceptor.intercept(decorViews, appContext)
        }
    }

    @MainThread
    override fun stopRecorders() {
        uiHandler.post {
            viewOnDrawInterceptor.stopIntercepting()
            windowCallbackInterceptor.stopIntercepting()
            shouldRecord = false
        }
    }

    @MainThread
    override fun onWindowsAdded(windows: List<Window>) {
        if (shouldRecord) {
            val decorViews = windowInspector.getGlobalWindowViews()
            windowCallbackInterceptor.intercept(windows, appContext)
            viewOnDrawInterceptor.intercept(decorViews, appContext)
        }
    }

    @MainThread
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
