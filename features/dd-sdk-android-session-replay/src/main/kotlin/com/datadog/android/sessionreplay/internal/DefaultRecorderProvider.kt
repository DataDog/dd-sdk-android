/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.app.Application
import android.os.Build
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.ActionBarContainer
import androidx.appcompat.widget.SwitchCompat
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry
import com.datadog.android.internal.utils.ImageViewUtils
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.MapperTypeWrapper
import com.datadog.android.sessionreplay.SessionReplayInternalCallback
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
import com.datadog.android.sessionreplay.internal.composition.ActiveWindowSource
import com.datadog.android.sessionreplay.internal.composition.AndroidCapturedSnapshotProducer
import com.datadog.android.sessionreplay.internal.composition.AndroidSnapshotCaptureLifecycle
import com.datadog.android.sessionreplay.internal.composition.AndroidWindowTraversal
import com.datadog.android.sessionreplay.internal.composition.CapturePipelineSelector
import com.datadog.android.sessionreplay.internal.composition.CaptureSkippedFrameNotifier
import com.datadog.android.sessionreplay.internal.composition.CaptureTimeBudget
import com.datadog.android.sessionreplay.internal.composition.CapturedSnapshotProducer
import com.datadog.android.sessionreplay.internal.composition.CompositionCapturePipeline
import com.datadog.android.sessionreplay.internal.composition.CompositionChangeListener
import com.datadog.android.sessionreplay.internal.composition.CompositionChangeset
import com.datadog.android.sessionreplay.internal.composition.CompositionViewOnDrawInterceptor
import com.datadog.android.sessionreplay.internal.composition.DefaultOrientationProvider
import com.datadog.android.sessionreplay.internal.composition.DefaultRumViewScopeProvider
import com.datadog.android.sessionreplay.internal.composition.DefaultSnapshotCompletionProcessor
import com.datadog.android.sessionreplay.internal.composition.HandlerCaptureMainThreadExecutor
import com.datadog.android.sessionreplay.internal.composition.HandlerCaptureTaskScheduler
import com.datadog.android.sessionreplay.internal.composition.PixelFallbackSnapshotProcessor
import com.datadog.android.sessionreplay.internal.composition.ScheduledExecutorCaptureTaskScheduler
import com.datadog.android.sessionreplay.internal.composition.SnapshotCaptureOrchestrator
import com.datadog.android.sessionreplay.internal.composition.SnapshotCompletionQueue
import com.datadog.android.sessionreplay.internal.composition.TimeBankCaptureTimeBudget
import com.datadog.android.sessionreplay.internal.composition.TimeProviderCaptureTimeProvider
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedEditTextMapper
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
import com.datadog.android.sessionreplay.internal.recorder.SessionReplayRecorder
import com.datadog.android.sessionreplay.internal.recorder.TimeBank
import com.datadog.android.sessionreplay.internal.recorder.mapper.ActionBarContainerMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.CheckBoxMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.CheckedTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.NumberPickerMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ProgressBarWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.RadioButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.SeekBarWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.SwitchCompatMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WebViewWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.internal.recorder.resources.buildResourceResolver
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.storage.ResourcesWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposer
import com.datadog.android.sessionreplay.recorder.mapper.EditTextMapper
import com.datadog.android.sessionreplay.recorder.mapper.ImageViewMapper
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.recorder.privacy.TextDetector
import com.datadog.android.sessionreplay.recorder.resources.DefaultDrawableCopier
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

internal class DefaultRecorderProvider(
    private val sdkCore: FeatureSdkCore,
    private val textAndInputPrivacy: TextAndInputPrivacy,
    private val imagePrivacy: ImagePrivacy,
    private val touchPrivacyManager: TouchPrivacyManager,
    private val customMappers: List<MapperTypeWrapper<*>>,
    private val customOptionSelectorDetectors: List<OptionSelectorDetector>,
    private val customDrawableMappers: List<DrawableToColorMapper>,
    private val dynamicOptimizationEnabled: Boolean,
    private val internalCallback: SessionReplayInternalCallback,
    private val heatmapsEnabled: Boolean,
    private val compositionTreeRecordingEnabled: Boolean,
    private val compositionHostDecomposer: CompositionHostDecomposer? = null,
    private val textDetector: TextDetector? = null,
    private val compositionPipelineFactory: (() -> Recorder)? = null,
    private val compositionSnapshotProducerFactory: (
        (ActiveWindowSource, RumContextProvider) -> CapturedSnapshotProducer
    )? = null,
    private val recordingTimeBankFactory: () -> TimeBank = { RecordingTimeBank() }
) : RecorderProvider {

    override fun provideSessionReplayRecorder(
        resourceDataStoreManager: ResourceDataStoreManager,
        resourceWriter: ResourcesWriter,
        recordWriter: RecordWriter,
        rumContextProvider: RumContextProvider,
        application: Application,
        embeddedContentSlotRegistry: EmbeddedContentSlotRegistry
    ): Recorder {
        val heatmapIdentifierRegistry = if (heatmapsEnabled) LazyHeatmapIdentifierRegistry(sdkCore) else null
        return CapturePipelineSelector(
            compositionEnabled = compositionTreeRecordingEnabled,
            compositionFactory = {
                compositionPipelineFactory?.invoke() ?: createCompositionPipeline(
                    resourceDataStoreManager,
                    resourceWriter,
                    recordWriter,
                    rumContextProvider,
                    application,
                    embeddedContentSlotRegistry,
                    heatmapIdentifierRegistry
                )
            },
            legacyFactory = {
                SessionReplayRecorder(
                    application,
                    resourceDataStoreManager = resourceDataStoreManager,
                    resourcesWriter = resourceWriter,
                    rumContextProvider = rumContextProvider,
                    imagePrivacy = imagePrivacy,
                    touchPrivacyManager = touchPrivacyManager,
                    textAndInputPrivacy = textAndInputPrivacy,
                    recordWriter = recordWriter,
                    timeProvider = sdkCore.timeProvider,
                    mappers = customMappers + builtInMappers(),
                    customOptionSelectorDetectors = customOptionSelectorDetectors,
                    customDrawableMappers = customDrawableMappers,
                    sdkCore = sdkCore,
                    dynamicOptimizationEnabled = dynamicOptimizationEnabled,
                    internalCallback = internalCallback,
                    embeddedContentSlotRegistry = embeddedContentSlotRegistry,
                    heatmapIdentifierRegistry = heatmapIdentifierRegistry
                )
            }
        ).create()
    }

    @Suppress("LongParameterList")
    private fun createCompositionPipeline(
        resourceDataStoreManager: ResourceDataStoreManager,
        resourceWriter: ResourcesWriter,
        recordWriter: RecordWriter,
        rumContextProvider: RumContextProvider,
        application: Application,
        embeddedContentSlotRegistry: EmbeddedContentSlotRegistry,
        heatmapIdentifierRegistry: HeatmapIdentifierRegistry?
    ): Recorder {
        val internalLogger = sdkCore.internalLogger
        val windowSource = ActiveWindowSource()
        // Native View content only - see setCompositionTreeRecordingEnabled's own doc for why
        // Jetpack Compose content doesn't get heatmap identifiers here either, same as the legacy
        // pipeline.
        val heatmapResolver = heatmapIdentifierRegistry?.let {
            HeatmapIdentifierResolver(
                appPackageName = application.packageName,
                registry = it,
                internalLogger = internalLogger
            )
        }
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
            // This pipeline's own drain trigger only runs as a side effect of a full capture
            // generation completing, not on every View.onDraw() like the legacy pipeline - so it
            // needs its own eager drain to avoid the resource queue's expiry window on a screen
            // that renders once and then sits idle. See ResourceItemCreationHandler's own doc.
            eagerResourceDrain = true
        )
        val completionQueue = createCompletionQueue(
            recordWriter,
            rumContextProvider,
            internalLogger,
            resourceResolverBundle.dataQueueHandler
        )
        val orchestrator = createCaptureOrchestrator(
            windowSource,
            rumContextProvider,
            completionQueue,
            resourceResolverBundle.resourceResolver,
            internalLogger,
            heatmapResolver
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
            resourceResolver = resourceResolverBundle.resourceResolver,
            resourceDataQueueHandler = resourceResolverBundle.dataQueueHandler
        )
    }

    private fun createCompletionQueue(
        recordWriter: RecordWriter,
        rumContextProvider: RumContextProvider,
        internalLogger: InternalLogger,
        resourceDataQueueHandler: DataQueueHandler
    ): SnapshotCompletionQueue = SnapshotCompletionQueue(
        executorService = sdkCore.createSingleThreadExecutorService("sr-composition-processing"),
        processor = DefaultSnapshotCompletionProcessor(
            rumContextProvider = rumContextProvider,
            recordWriter = recordWriter,
            internalLogger = internalLogger,
            timeProvider = sdkCore.timeProvider,
            orientationProvider = DefaultOrientationProvider(),
            resourceDataQueueHandler = resourceDataQueueHandler
        ),
        internalLogger = internalLogger
    )

    @Suppress("LongParameterList")
    private fun createCaptureOrchestrator(
        windowSource: ActiveWindowSource,
        rumContextProvider: RumContextProvider,
        completionQueue: SnapshotCompletionQueue,
        resourceResolver: ResourceResolver,
        internalLogger: InternalLogger,
        heatmapResolver: HeatmapIdentifierResolver?
    ): SnapshotCaptureOrchestrator {
        val skippedFrameNotifier = CaptureSkippedFrameNotifier(sdkCore)
        val producer = compositionSnapshotProducerFactory?.invoke(windowSource, rumContextProvider)
            ?: defaultCompositionSnapshotProducer(windowSource, rumContextProvider, heatmapResolver)
        val mainThreadExecutor = HandlerCaptureMainThreadExecutor()
        // Shared rather than one-per-consumer: sdkCore.createScheduledExecutorService allocates a
        // brand-new background thread with no pooling, and PixelFallbackSnapshotProcessor has no
        // shutdown hook of its own - only SnapshotCaptureOrchestrator.shutdown()'s own
        // expiryScheduler.shutdown() call ever reclaims it.
        val sharedScheduler = ScheduledExecutorCaptureTaskScheduler(
            executorService = sdkCore.createScheduledExecutorService("sr-composition-expiry"),
            internalLogger = internalLogger
        )
        return SnapshotCaptureOrchestrator(
            producer = producer,
            processor = PixelFallbackSnapshotProcessor(
                resourceResolver,
                textDetector,
                mainThreadExecutor,
                sharedScheduler
            ),
            consumer = completionQueue,
            timeProvider = TimeProviderCaptureTimeProvider(sdkCore.timeProvider),
            captureScheduler = HandlerCaptureTaskScheduler(),
            mainThreadExecutor = mainThreadExecutor,
            expiryScheduler = sharedScheduler,
            timeBudget = if (dynamicOptimizationEnabled) {
                TimeBankCaptureTimeBudget(
                    recordingTimeBankFactory(),
                    skippedFrameNotifier::notifySkippedFrame
                )
            } else {
                CaptureTimeBudget.UNLIMITED
            },
            internalLogger = internalLogger
        )
    }

    private fun defaultCompositionSnapshotProducer(
        windowSource: ActiveWindowSource,
        rumContextProvider: RumContextProvider,
        heatmapResolver: HeatmapIdentifierResolver?
    ): AndroidCapturedSnapshotProducer = AndroidCapturedSnapshotProducer(
        windowSource = windowSource,
        scopeProvider = DefaultRumViewScopeProvider(rumContextProvider),
        timeProvider = sdkCore.timeProvider,
        traversal = AndroidWindowTraversal(
            mapperRegistry = builtInCapturedMappers(),
            composeHostDecomposer = compositionHostDecomposer,
            rootImagePrivacy = imagePrivacy,
            rootTextAndInputPrivacy = textAndInputPrivacy,
            internalLogger = sdkCore.internalLogger,
            heatmapResolver = heatmapResolver
        )
    )

    private fun builtInCapturedMappers(): CapturedViewMapperRegistry {
        val internalLogger = sdkCore.internalLogger
        return CapturedViewMapperRegistry(
            mappers = listOf(
                CapturedMapperTypeWrapper(WebView::class.java, CapturedWebViewMapper()),
                CapturedMapperTypeWrapper(
                    EditText::class.java,
                    CapturedEditTextMapper(internalLogger = internalLogger)
                ),
                CapturedMapperTypeWrapper(
                    TextView::class.java,
                    CapturedTextViewMapper<TextView>(internalLogger = internalLogger)
                )
            ),
            fallbackMapper = CapturedPixelFallbackMapper(
                fallbackMapper = CapturedViewGroupFallbackMapper(internalLogger = internalLogger),
                internalLogger = internalLogger
            ),
            internalLogger = internalLogger
        )
    }

    @Suppress("LongMethod")
    private fun builtInMappers(): List<MapperTypeWrapper<*>> {
        val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
        val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter
        val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver
        val drawableToColorMapper: DrawableToColorMapper = DrawableToColorMapper.getDefault()
        val imageViewMapper = ImageViewMapper(
            viewIdentifierResolver = viewIdentifierResolver,
            colorStringFormatter = colorStringFormatter,
            viewBoundsResolver = viewBoundsResolver,
            drawableToColorMapper = drawableToColorMapper,
            imageViewUtils = ImageViewUtils,
            drawableCopier = DefaultDrawableCopier()
        )
        val textViewMapper = TextViewMapper<TextView>(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )

        val mappersList = mutableListOf(
            MapperTypeWrapper(
                SwitchCompat::class.java,
                SwitchCompatMapper(
                    textViewMapper as TextViewMapper<SwitchCompat>,
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                RadioButton::class.java,
                RadioButtonMapper(
                    textViewMapper as TextViewMapper<RadioButton>,
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper,
                    internalLogger = sdkCore.internalLogger
                )
            ),
            MapperTypeWrapper(
                CheckBox::class.java,
                CheckBoxMapper(
                    textViewMapper as TextViewMapper<CheckBox>,
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper,
                    internalLogger = sdkCore.internalLogger
                )
            ),
            MapperTypeWrapper(
                CheckedTextView::class.java,
                CheckedTextViewMapper(
                    textViewMapper as TextViewMapper<CheckedTextView>,
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                EditText::class.java,
                EditTextMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                Button::class.java,
                ButtonMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                TextView::class.java,
                textViewMapper
            ),
            MapperTypeWrapper(
                ImageView::class.java,
                imageViewMapper
            ),
            MapperTypeWrapper(
                ActionBarContainer::class.java,
                ActionBarContainerMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                WebView::class.java,
                WebViewWireframeMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                SeekBar::class.java,
                SeekBarWireframeMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                ProgressBar::class.java,
                ProgressBarWireframeMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper,
                    true
                )
            )
        )

        getNumberPickerMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )?.let {
            mappersList.add(0, MapperTypeWrapper(NumberPicker::class.java, it))
        }
        return mappersList
    }

    private fun getNumberPickerMapper(
        viewIdentifierResolver: ViewIdentifierResolver,
        colorStringFormatter: ColorStringFormatter,
        viewBoundsResolver: ViewBoundsResolver,
        drawableToColorMapper: DrawableToColorMapper
    ): WireframeMapper<NumberPicker>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            NumberPickerMapper(
                viewIdentifierResolver,
                colorStringFormatter,
                viewBoundsResolver,
                drawableToColorMapper
            )
        } else {
            null
        }
    }
}
