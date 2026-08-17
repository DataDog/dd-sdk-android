/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.app.Application
import android.webkit.WebView
import android.widget.TextView
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.SessionReplayInternalCallback
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMapperTypeWrapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedTextViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewGroupFallbackMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedWebViewMapper
import com.datadog.android.sessionreplay.internal.recorder.Recorder
import com.datadog.android.sessionreplay.internal.recorder.RecordingTimeBank
import com.datadog.android.sessionreplay.internal.recorder.TimeBank
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider

/**
 * Wires every collaborator scoped to one composition recording session: the orchestrator and its
 * schedulers, the completion queue and its executor, and the draw-signal interception.
 */
internal class DefaultCompositionPipelineFactory(
    private val sdkCore: FeatureSdkCore,
    private val internalCallback: SessionReplayInternalCallback,
    private val dynamicOptimizationEnabled: Boolean,
    private val snapshotProducerFactory: (ActiveWindowSource, RumContextProvider) -> CapturedSnapshotProducer = {
            windowSource,
            rumContextProvider
        ->
        AndroidCapturedSnapshotProducer(
            windowSource = windowSource,
            scopeProvider = DefaultRumViewScopeProvider(rumContextProvider),
            timeProvider = sdkCore.timeProvider,
            traversal = AndroidWindowTraversal(mapperRegistry = builtInCapturedMappers(sdkCore.internalLogger))
        )
    },
    private val recordingTimeBankFactory: () -> TimeBank = { RecordingTimeBank() }
) : CompositionPipelineFactory {

    override fun create(
        recordWriter: RecordWriter,
        rumContextProvider: RumContextProvider,
        application: Application
    ): Recorder {
        val internalLogger = sdkCore.internalLogger
        val windowSource = ActiveWindowSource()
        val completionQueue = SnapshotCompletionQueue(
            executorService = sdkCore.createSingleThreadExecutorService(PROCESSING_EXECUTOR_NAME),
            processor = DefaultSnapshotCompletionProcessor(
                rumContextProvider = rumContextProvider,
                recordWriter = recordWriter,
                internalLogger = internalLogger
            ),
            internalLogger = internalLogger
        )
        val orchestrator = SnapshotCaptureOrchestrator(
            producer = snapshotProducerFactory(windowSource, rumContextProvider),
            processor = ImmediateCapturedSnapshotProcessor(),
            consumer = completionQueue,
            timeProvider = TimeProviderCaptureTimeProvider(sdkCore.timeProvider),
            captureScheduler = HandlerCaptureTaskScheduler(),
            mainThreadExecutor = HandlerCaptureMainThreadExecutor(),
            expiryScheduler = ScheduledExecutorCaptureTaskScheduler(
                executorService = sdkCore.createScheduledExecutorService(EXPIRY_EXECUTOR_NAME),
                internalLogger = internalLogger
            ),
            timeBudget = createTimeBudget(),
            internalLogger = internalLogger
        )
        val interceptor = CompositionViewOnDrawInterceptor(
            windowSource = windowSource,
            onWindowsChanged = CompositionChangeListener { windows ->
                orchestrator.requestCapture(CompositionChangeset.of(windows))
            },
            internalLogger = internalLogger
        )
        return CompositionCapturePipeline(
            orchestrator = orchestrator,
            lifecycle = AndroidSnapshotCaptureLifecycle(
                application = application,
                interceptor = interceptor,
                internalLogger = internalLogger,
                currentActivity = internalCallback.getCurrentActivity()
            ),
            completionQueue = completionQueue,
            internalLogger = internalLogger
        )
    }

    private fun createTimeBudget(): CaptureTimeBudget = if (dynamicOptimizationEnabled) {
        val skippedFrameNotifier = CaptureSkippedFrameNotifier(sdkCore)
        TimeBankCaptureTimeBudget(recordingTimeBankFactory(), skippedFrameNotifier::notifySkippedFrame)
    } else {
        CaptureTimeBudget.UNLIMITED
    }

    private companion object {
        const val PROCESSING_EXECUTOR_NAME = "sr-composition-processing"
        const val EXPIRY_EXECUTOR_NAME = "sr-composition-expiry"

        fun builtInCapturedMappers(internalLogger: InternalLogger): CapturedViewMapperRegistry =
            CapturedViewMapperRegistry(
                mappers = listOf(
                    CapturedMapperTypeWrapper(WebView::class.java, CapturedWebViewMapper()),
                    CapturedMapperTypeWrapper(
                        TextView::class.java,
                        CapturedTextViewMapper(internalLogger = internalLogger)
                    )
                ),
                fallbackMapper = CapturedViewGroupFallbackMapper(internalLogger = internalLogger),
                internalLogger = internalLogger
            )
    }
}
