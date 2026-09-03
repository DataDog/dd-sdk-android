/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.app.Application
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.SessionReplayInternalCallback
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedBackgroundShapeStyleResolver
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedEditTextMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedEmbeddedContentMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMapperTypeWrapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedPixelFallbackMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedTextViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewGroupFallbackMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedWebViewMapper
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistry
import com.datadog.android.sessionreplay.internal.recorder.HeatmapIdentifierResolver
import com.datadog.android.sessionreplay.internal.recorder.Recorder
import com.datadog.android.sessionreplay.internal.recorder.RecordingTimeBank
import com.datadog.android.sessionreplay.internal.recorder.TimeBank
import com.datadog.android.sessionreplay.internal.recorder.resources.buildResourceResolver
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.storage.ResourcesWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposer
import com.datadog.android.sessionreplay.recorder.privacy.TextDetector
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper

/**
 * Wires every collaborator scoped to one composition recording session: the orchestrator and its
 * schedulers, the completion queue and its executor, and the draw-signal and touch interception.
 */
@Suppress("LongParameterList")
internal class DefaultCompositionPipelineFactory(
    private val sdkCore: FeatureSdkCore,
    private val internalCallback: SessionReplayInternalCallback,
    private val touchPrivacyManager: TouchPrivacyManager,
    private val imagePrivacy: ImagePrivacy,
    private val textAndInputPrivacy: TextAndInputPrivacy,
    private val compositionHostDecomposer: CompositionHostDecomposer? = null,
    private val textDetector: TextDetector? = null,
    private val dynamicOptimizationEnabled: Boolean,
    private val customDrawableMappers: List<DrawableToColorMapper> = emptyList(),
    private val snapshotProducerFactory: ((ActiveWindowSource, RumContextProvider) -> CapturedSnapshotProducer)? = null,
    private val recordingTimeBankFactory: () -> TimeBank = { RecordingTimeBank() }
) : CompositionPipelineFactory {

    @Suppress("LongMethod")
    override fun create(
        resourceDataStoreManager: ResourceDataStoreManager,
        resourceWriter: ResourcesWriter,
        recordWriter: RecordWriter,
        rumContextProvider: RumContextProvider,
        application: Application,
        embeddedContentSlotRegistry: EmbeddedContentSlotRegistry,
        heatmapIdentifierRegistry: HeatmapIdentifierRegistry?
    ): Recorder {
        val internalLogger = sdkCore.internalLogger
        val windowSource = ActiveWindowSource(WindowTouchPrivacyReader(internalLogger = internalLogger)::read)
        val heatmapResolver = heatmapIdentifierRegistry?.let {
            HeatmapIdentifierResolver(application.packageName, it, internalLogger)
        }
        val skippedFrameNotifier = CaptureSkippedFrameNotifier(sdkCore)
        // Construction has a capture deadline and must not queue behind completion mapping or writes.
        val constructionExecutor = sdkCore.createSingleThreadExecutorService(CONSTRUCTION_EXECUTOR_NAME)
        val completionExecutor = sdkCore.createSingleThreadExecutorService(COMPLETION_EXECUTOR_NAME)
        val resourceResolverBundle = buildResourceResolver(
            appContext = application,
            sdkCore = sdkCore,
            resourceDataStoreManager = resourceDataStoreManager,
            resourcesWriter = resourceWriter,
            recordWriter = recordWriter,
            rumContextProvider = rumContextProvider,
            embeddedContentSlotRegistry = embeddedContentSlotRegistry,
            eventProcessingExecutorName = "sr-composition-event-processing",
            drawablesExecutorName = "sr-composition-drawables",
            eagerResourceDrain = true
        )
        val completionQueue = SnapshotCompletionQueue(
            executorService = completionExecutor,
            processor = DefaultSnapshotCompletionProcessor(
                rumContextProvider = rumContextProvider,
                recordWriter = recordWriter,
                internalLogger = internalLogger,
                timeProvider = sdkCore.timeProvider,
                resourceDataQueueHandler = resourceResolverBundle.dataQueueHandler,
                embeddedContentSlotRegistry = embeddedContentSlotRegistry
            ),
            internalLogger = internalLogger
        )
        val mainThreadExecutor = HandlerCaptureMainThreadExecutor()
        val sharedScheduler = ExecutorCaptureTaskScheduler(
            executorService = sdkCore.createScheduledExecutorService(EXPIRY_EXECUTOR_NAME),
            internalLogger = internalLogger
        )
        lateinit var orchestrator: SnapshotCaptureOrchestrator
        orchestrator = SnapshotCaptureOrchestrator(
            producer = snapshotProducerFactory?.invoke(windowSource, rumContextProvider)
                ?: defaultSnapshotProducer(
                    windowSource,
                    rumContextProvider,
                    heatmapResolver,
                    embeddedContentSlotRegistry
                ),
            processor = ChainedCapturedSnapshotProcessor(
                ExecutorCapturedSnapshotProcessor(constructionExecutor, internalLogger),
                PixelFallbackSnapshotProcessor(
                    resourceResolverBundle.resourceResolver,
                    textDetector,
                    mainThreadExecutor,
                    taskScheduler = sharedScheduler,
                    recaptureTrigger = RecaptureTrigger { orchestrator.requestCapture() }
                )
            ),
            consumer = completionQueue,
            timeProvider = CaptureTimeProvider { sdkCore.timeProvider.getDeviceElapsedTimeNanos() },
            captureScheduler = HandlerCaptureTaskScheduler(),
            mainThreadExecutor = mainThreadExecutor,
            expiryScheduler = sharedScheduler,
            timeBudget = createTimeBudget(),
            onFrameSkipped = skippedFrameNotifier::notifySkippedFrame,
            internalLogger = internalLogger
        )
        val interceptor = CompositionViewOnDrawInterceptor(
            windowSource = windowSource,
            onWindowsChanged = CompositionChangeListener { orchestrator.requestCapture() },
            internalLogger = internalLogger
        )
        val touchInterceptor = CompositionWindowTouchInterceptor(
            appContext = application,
            recordWriter = recordWriter,
            timeProvider = sdkCore.timeProvider,
            rumContextProvider = rumContextProvider,
            touchPrivacyManager = touchPrivacyManager,
            internalLogger = internalLogger,
            windowSource = windowSource
        )
        return CompositionCapturePipeline(
            orchestrator = orchestrator,
            lifecycle = AndroidSnapshotCaptureLifecycle(
                application = application,
                interceptor = interceptor,
                touchInterceptor = touchInterceptor,
                internalLogger = internalLogger,
                currentActivity = internalCallback.getCurrentActivity()
            ),
            completionQueue = completionQueue,
            constructionExecutor = constructionExecutor,
            internalLogger = internalLogger,
            resourceResolver = resourceResolverBundle.resourceResolver,
            resourceDataQueueHandler = resourceResolverBundle.dataQueueHandler
        )
    }

    private fun defaultSnapshotProducer(
        windowSource: ActiveWindowSource,
        rumContextProvider: RumContextProvider,
        heatmapResolver: HeatmapIdentifierResolver?,
        embeddedContentSlotRegistry: EmbeddedContentSlotRegistry
    ): CapturedSnapshotProducer {
        val embeddedContentMapper = CapturedEmbeddedContentMapper(
            embeddedContentSlotRegistry,
            internalLogger = sdkCore.internalLogger
        )
        return AndroidCapturedSnapshotProducer(
            windowSource = windowSource,
            scopeProvider = DefaultRumViewScopeProvider(rumContextProvider),
            timeProvider = sdkCore.timeProvider,
            traversal = AndroidWindowTraversal(
                mapperRegistry = builtInCapturedMappers(sdkCore.internalLogger, customDrawableMappers),
                internalLogger = sdkCore.internalLogger,
                composeHostDecomposer = compositionHostDecomposer,
                rootImagePrivacy = imagePrivacy,
                rootTextAndInputPrivacy = textAndInputPrivacy,
                heatmapResolver = heatmapResolver,
                embeddedContentMapper = embeddedContentMapper
            ),
            touchPrivacyManager = touchPrivacyManager,
            embeddedContentMapper = embeddedContentMapper
        )
    }

    private fun createTimeBudget(): CaptureTimeBudget = if (dynamicOptimizationEnabled) {
        TimeBankCaptureTimeBudget(recordingTimeBankFactory())
    } else {
        CaptureTimeBudget.UNLIMITED
    }

    private companion object {
        const val CONSTRUCTION_EXECUTOR_NAME = "sr-composition-construction"
        const val COMPLETION_EXECUTOR_NAME = "sr-composition-processing"
        const val EXPIRY_EXECUTOR_NAME = "sr-composition-expiry"

        fun builtInCapturedMappers(
            internalLogger: InternalLogger,
            customDrawableMappers: List<DrawableToColorMapper>
        ): CapturedViewMapperRegistry {
            val backgroundResolver = CapturedBackgroundShapeStyleResolver(
                drawableToColorMapper = DrawableToColorMapper.getDefault(customDrawableMappers)
            )
            return CapturedViewMapperRegistry(
                mappers = listOf(
                    CapturedMapperTypeWrapper(WebView::class.java, CapturedWebViewMapper()),
                    CapturedMapperTypeWrapper(
                        EditText::class.java,
                        CapturedEditTextMapper(
                            backgroundShapeStyleResolver = backgroundResolver,
                            internalLogger = internalLogger
                        )
                    ),
                    CapturedMapperTypeWrapper(
                        TextView::class.java,
                        CapturedTextViewMapper<TextView>(
                            backgroundShapeStyleResolver = backgroundResolver,
                            internalLogger = internalLogger
                        )
                    )
                ),
                fallbackMapper = CapturedPixelFallbackMapper(
                    fallbackMapper = CapturedViewGroupFallbackMapper(
                        backgroundShapeStyleResolver = backgroundResolver,
                        internalLogger = internalLogger
                    ),
                    internalLogger = internalLogger
                ),
                internalLogger = internalLogger
            )
        }
    }
}
