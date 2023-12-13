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
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.LifecycleCallback
import com.datadog.android.sessionreplay.internal.RecordWriter
import com.datadog.android.sessionreplay.internal.SessionReplayLifecycleCallback
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.processor.MutationResolver
import com.datadog.android.sessionreplay.internal.processor.RecordedDataProcessor
import com.datadog.android.sessionreplay.internal.processor.RumContextDataHandler
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

internal class SessionReplayRecorder : OnWindowRefreshedCallback, Recorder {

    private val appContext: Application
    private val rumContextProvider: RumContextProvider
    private val privacy: SessionReplayPrivacy
    private val recordWriter: RecordWriter
    private val timeProvider: TimeProvider
    private val customMappers: List<MapperTypeWrapper>
    private val customOptionSelectorDetectors: List<OptionSelectorDetector>
    private val windowInspector: WindowInspector
    private val windowCallbackInterceptor: WindowCallbackInterceptor
    private val sessionReplayLifecycleCallback: LifecycleCallback
    private val recordedDataQueueHandler: RecordedDataQueueHandler
    private val viewOnDrawInterceptor: ViewOnDrawInterceptor
    private val internalLogger: InternalLogger

    private val uiHandler: Handler
    private var shouldRecord = false

    constructor(
        appContext: Application,
        rumContextProvider: RumContextProvider,
        privacy: SessionReplayPrivacy,
        recordWriter: RecordWriter,
        timeProvider: TimeProvider,
        customMappers: List<MapperTypeWrapper> = emptyList(),
        customOptionSelectorDetectors: List<OptionSelectorDetector> = emptyList(),
        windowInspector: WindowInspector = WindowInspector,
        internalLogger: InternalLogger
    ) {
        val rumContextDataHandler = RumContextDataHandler(
            rumContextProvider,
            timeProvider,
            internalLogger
        )

        val processor = RecordedDataProcessor(
            recordWriter,
            MutationResolver(internalLogger)
        )

        this.appContext = appContext
        this.rumContextProvider = rumContextProvider
        this.privacy = privacy
        this.recordWriter = recordWriter
        this.timeProvider = timeProvider
        this.customMappers = customMappers
        this.customOptionSelectorDetectors = customOptionSelectorDetectors
        this.windowInspector = windowInspector
        this.recordedDataQueueHandler = RecordedDataQueueHandler(
            processor = processor,
            rumContextDataHandler = rumContextDataHandler,
            timeProvider = timeProvider,
            internalLogger = internalLogger
        )
        this.viewOnDrawInterceptor = ViewOnDrawInterceptor(
            recordedDataQueueHandler = recordedDataQueueHandler,
            SnapshotProducer(
                TreeViewTraversal(customMappers + privacy.mappers()),
                rumContextProvider,
                ComposedOptionSelectorDetector(
                    customOptionSelectorDetectors + DefaultOptionSelectorDetector()
                )
            )
        )
        this.windowCallbackInterceptor = WindowCallbackInterceptor(
            recordedDataQueueHandler,
            viewOnDrawInterceptor,
            timeProvider,
            internalLogger
        )
        this.sessionReplayLifecycleCallback = SessionReplayLifecycleCallback(this)
        this.uiHandler = Handler(Looper.getMainLooper())
        this.internalLogger = internalLogger
    }

    @VisibleForTesting
    @Suppress("LongParameterList")
    constructor(
        appContext: Application,
        rumContextProvider: RumContextProvider,
        privacy: SessionReplayPrivacy,
        recordWriter: RecordWriter,
        timeProvider: TimeProvider,
        customMappers: List<MapperTypeWrapper> = emptyList(),
        customOptionSelectorDetectors: List<OptionSelectorDetector>,
        windowInspector: WindowInspector = WindowInspector,
        windowCallbackInterceptor: WindowCallbackInterceptor,
        sessionReplayLifecycleCallback: LifecycleCallback,
        viewOnDrawInterceptor: ViewOnDrawInterceptor,
        recordedDataQueueHandler: RecordedDataQueueHandler,
        uiHandler: Handler,
        internalLogger: InternalLogger
    ) {
        this.appContext = appContext
        this.rumContextProvider = rumContextProvider
        this.privacy = privacy
        this.recordWriter = recordWriter
        this.timeProvider = timeProvider
        this.customMappers = customMappers
        this.customOptionSelectorDetectors = customOptionSelectorDetectors
        this.windowInspector = windowInspector
        this.recordedDataQueueHandler = recordedDataQueueHandler
        this.viewOnDrawInterceptor = viewOnDrawInterceptor
        this.windowCallbackInterceptor = windowCallbackInterceptor
        this.sessionReplayLifecycleCallback = sessionReplayLifecycleCallback
        this.uiHandler = uiHandler
        this.internalLogger = internalLogger
    }

    override fun stopProcessingRecords() {
        recordedDataQueueHandler.clearAndStopProcessingQueue()
    }

    override fun registerCallbacks() {
        appContext.registerActivityLifecycleCallbacks(sessionReplayLifecycleCallback)
    }

    override fun unregisterCallbacks() {
        appContext.unregisterActivityLifecycleCallbacks(sessionReplayLifecycleCallback)
    }

    override fun resumeRecorders() {
        uiHandler.post {
            shouldRecord = true
            val windows = sessionReplayLifecycleCallback.getCurrentWindows()
            val decorViews = windowInspector.getGlobalWindowViews(internalLogger)
            windowCallbackInterceptor.intercept(windows, appContext)
            viewOnDrawInterceptor.intercept(decorViews)
        }
    }

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
            val decorViews = windowInspector.getGlobalWindowViews(internalLogger)
            windowCallbackInterceptor.intercept(windows, appContext)
            viewOnDrawInterceptor.intercept(decorViews)
        }
    }

    @MainThread
    override fun onWindowsRemoved(windows: List<Window>) {
        if (shouldRecord) {
            val decorViews = windowInspector.getGlobalWindowViews(internalLogger)
            windowCallbackInterceptor.stopIntercepting(windows)
            viewOnDrawInterceptor.intercept(decorViews)
        }
    }

    override fun handeWebViewRecord(record: String) {
        uiHandler.post {
            if (shouldRecord) {
                recordedDataQueueHandler.addWebViewItem(record)
            }
        }
    }
}
