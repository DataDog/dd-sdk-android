/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.Window
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.MapperTypeWrapper
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.LifecycleCallback
import com.datadog.android.sessionreplay.internal.SessionReplayLifecycleCallback
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.processor.MutationResolver
import com.datadog.android.sessionreplay.internal.processor.RecordedDataProcessor
import com.datadog.android.sessionreplay.internal.processor.RumContextDataHandler
import com.datadog.android.sessionreplay.internal.recorder.callback.OnWindowRefreshedCallback
import com.datadog.android.sessionreplay.internal.recorder.mapper.DecorViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.HiddenViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ViewWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapCachesManager
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapPool
import com.datadog.android.sessionreplay.internal.recorder.resources.DefaultImageWireframeHelper
import com.datadog.android.sessionreplay.internal.recorder.resources.ImageTypeResolver
import com.datadog.android.sessionreplay.internal.recorder.resources.MD5HashGenerator
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourcesLRUCache
import com.datadog.android.sessionreplay.internal.recorder.resources.WebPImageCompression
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.storage.ResourcesWriter
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.TimeProvider
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import java.util.concurrent.ConcurrentLinkedQueue

internal class SessionReplayRecorder : OnWindowRefreshedCallback, Recorder {

    private val appContext: Application
    private val rumContextProvider: RumContextProvider
    private val textAndInputPrivacy: TextAndInputPrivacy
    private val imagePrivacy: ImagePrivacy
    private val touchPrivacy: TouchPrivacy
    private val recordWriter: RecordWriter
    private val timeProvider: TimeProvider
    private val mappers: List<MapperTypeWrapper<*>>
    private val customOptionSelectorDetectors: List<OptionSelectorDetector>
    private val windowInspector: WindowInspector
    private val windowCallbackInterceptor: WindowCallbackInterceptor
    private val sessionReplayLifecycleCallback: LifecycleCallback
    private val recordedDataQueueHandler: RecordedDataQueueHandler
    private val viewOnDrawInterceptor: ViewOnDrawInterceptor
    private val internalLogger: InternalLogger
    private val resourceDataStoreManager: ResourceDataStoreManager

    private val uiHandler: Handler
    private var shouldRecord = false

    constructor(
        appContext: Application,
        resourcesWriter: ResourcesWriter,
        rumContextProvider: RumContextProvider,
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy,
        touchPrivacy: TouchPrivacy,
        recordWriter: RecordWriter,
        timeProvider: TimeProvider,
        mappers: List<MapperTypeWrapper<*>> = emptyList(),
        customOptionSelectorDetectors: List<OptionSelectorDetector> = emptyList(),
        windowInspector: WindowInspector = WindowInspector,
        sdkCore: FeatureSdkCore,
        resourceDataStoreManager: ResourceDataStoreManager,
        dynamicOptimizationEnabled: Boolean
    ) {
        val internalLogger = sdkCore.internalLogger
        val rumContextDataHandler = RumContextDataHandler(
            rumContextProvider,
            timeProvider,
            internalLogger
        )

        val processor = RecordedDataProcessor(
            resourceDataStoreManager,
            resourcesWriter,
            recordWriter,
            MutationResolver(internalLogger)
        )

        val applicationId = rumContextProvider.getRumContext().applicationId

        this.appContext = appContext
        this.rumContextProvider = rumContextProvider
        this.textAndInputPrivacy = textAndInputPrivacy
        this.imagePrivacy = imagePrivacy
        this.touchPrivacy = touchPrivacy
        this.recordWriter = recordWriter
        this.timeProvider = timeProvider
        this.mappers = mappers
        this.customOptionSelectorDetectors = customOptionSelectorDetectors
        this.windowInspector = windowInspector
        this.recordedDataQueueHandler = RecordedDataQueueHandler(
            processor = processor,
            rumContextDataHandler = rumContextDataHandler,
            internalLogger = internalLogger,
            executorService = sdkCore.createSingleThreadExecutorService(
                "sr-event-processing"
            ),
            recordedDataQueue = ConcurrentLinkedQueue()
        )
        this.resourceDataStoreManager = resourceDataStoreManager

        val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
        val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter
        val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver
        val drawableToColorMapper: DrawableToColorMapper = DrawableToColorMapper.getDefault()

        val defaultVWM = ViewWireframeMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )

        val bitmapCachesManager = BitmapCachesManager(
            bitmapPool = BitmapPool(),
            resourcesLRUCache = ResourcesLRUCache(),
            logger = internalLogger
        )

        val resourceResolver = ResourceResolver(
            applicationId = applicationId,
            recordedDataQueueHandler = recordedDataQueueHandler,
            bitmapCachesManager = bitmapCachesManager,
            drawableUtils = DrawableUtils(
                internalLogger,
                bitmapCachesManager,
                sdkCore.createSingleThreadExecutorService("drawables")
            ),
            logger = internalLogger,
            md5HashGenerator = MD5HashGenerator(internalLogger),
            webPImageCompression = WebPImageCompression(internalLogger)
        )

        this.viewOnDrawInterceptor = ViewOnDrawInterceptor(
            internalLogger = internalLogger,
            onDrawListenerProducer = DefaultOnDrawListenerProducer(
                snapshotProducer = SnapshotProducer(
                    DefaultImageWireframeHelper(
                        logger = internalLogger,
                        resourceResolver = resourceResolver,
                        viewIdentifierResolver = viewIdentifierResolver,
                        viewUtilsInternal = ViewUtilsInternal(),
                        imageTypeResolver = ImageTypeResolver()
                    ),
                    TreeViewTraversal(
                        mappers = mappers,
                        defaultViewMapper = defaultVWM,
                        decorViewMapper = DecorViewMapper(defaultVWM, viewIdentifierResolver),
                        hiddenViewMapper = HiddenViewMapper(
                            viewBoundsResolver = viewBoundsResolver,
                            viewIdentifierResolver = viewIdentifierResolver
                        ),
                        viewUtilsInternal = ViewUtilsInternal(),
                        internalLogger = internalLogger
                    ),
                    ComposedOptionSelectorDetector(
                        customOptionSelectorDetectors + DefaultOptionSelectorDetector()
                    ),
                    internalLogger = internalLogger
                ),
                recordedDataQueueHandler = recordedDataQueueHandler,
                sdkCore = sdkCore,
                dynamicOptimizationEnabled = dynamicOptimizationEnabled
            )
        )
        this.windowCallbackInterceptor = WindowCallbackInterceptor(
            recordedDataQueueHandler,
            viewOnDrawInterceptor,
            timeProvider,
            internalLogger,
            imagePrivacy,
            touchPrivacy,
            textAndInputPrivacy
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
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy,
        touchPrivacy: TouchPrivacy,
        recordWriter: RecordWriter,
        timeProvider: TimeProvider,
        mappers: List<MapperTypeWrapper<*>> = emptyList(),
        customOptionSelectorDetectors: List<OptionSelectorDetector>,
        windowInspector: WindowInspector = WindowInspector,
        windowCallbackInterceptor: WindowCallbackInterceptor,
        sessionReplayLifecycleCallback: LifecycleCallback,
        viewOnDrawInterceptor: ViewOnDrawInterceptor,
        recordedDataQueueHandler: RecordedDataQueueHandler,
        resourceDataStoreManager: ResourceDataStoreManager,
        uiHandler: Handler,
        internalLogger: InternalLogger
    ) {
        this.appContext = appContext
        this.rumContextProvider = rumContextProvider
        this.textAndInputPrivacy = textAndInputPrivacy
        this.imagePrivacy = imagePrivacy
        this.touchPrivacy = touchPrivacy
        this.recordWriter = recordWriter
        this.timeProvider = timeProvider
        this.mappers = mappers
        this.customOptionSelectorDetectors = customOptionSelectorDetectors
        this.windowInspector = windowInspector
        this.recordedDataQueueHandler = recordedDataQueueHandler
        this.viewOnDrawInterceptor = viewOnDrawInterceptor
        this.windowCallbackInterceptor = windowCallbackInterceptor
        this.sessionReplayLifecycleCallback = sessionReplayLifecycleCallback
        this.uiHandler = uiHandler
        this.internalLogger = internalLogger
        this.resourceDataStoreManager = resourceDataStoreManager
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
            viewOnDrawInterceptor.intercept(decorViews, textAndInputPrivacy, imagePrivacy)
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
            viewOnDrawInterceptor.intercept(decorViews, textAndInputPrivacy, imagePrivacy)
        }
    }

    @MainThread
    override fun onWindowsRemoved(windows: List<Window>) {
        if (shouldRecord) {
            val decorViews = windowInspector.getGlobalWindowViews(internalLogger)
            windowCallbackInterceptor.stopIntercepting(windows)
            viewOnDrawInterceptor.intercept(decorViews, textAndInputPrivacy, imagePrivacy)
        }
    }
}
