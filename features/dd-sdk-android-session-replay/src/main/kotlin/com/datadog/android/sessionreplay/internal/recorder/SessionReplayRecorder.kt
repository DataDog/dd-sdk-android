/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.MapperTypeWrapper
import com.datadog.android.sessionreplay.SessionReplayInternalCallback
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.LifecycleCallback
import com.datadog.android.sessionreplay.internal.SessionReplayLifecycleCallback
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.processor.MutationResolver
import com.datadog.android.sessionreplay.internal.processor.RecordedDataProcessor
import com.datadog.android.sessionreplay.internal.processor.ResourceQueueImpl
import com.datadog.android.sessionreplay.internal.processor.RumContextDataHandler
import com.datadog.android.sessionreplay.internal.recorder.callback.OnWindowRefreshedCallback
import com.datadog.android.sessionreplay.internal.recorder.mapper.DecorViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.HiddenViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.PixelCopyFallbackMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ViewWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapCachesManager
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapPool
import com.datadog.android.sessionreplay.internal.recorder.resources.DefaultImageWireframeHelper
import com.datadog.android.sessionreplay.internal.recorder.resources.ImageTypeResolver
import com.datadog.android.sessionreplay.internal.recorder.resources.MD5HashGenerator
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceDrawableKeyGenerator
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourcesLRUCache
import com.datadog.android.sessionreplay.internal.recorder.resources.WebPImageCompression
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.storage.ResourcesWriter
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.internal.utils.PathUtils
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper
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
    private val textAndInputPrivacy: TextAndInputPrivacy
    private val imagePrivacy: ImagePrivacy
    private val customOptionSelectorDetectors: List<OptionSelectorDetector>
    private val windowInspector: WindowInspector
    private val windowCallbackInterceptor: WindowCallbackInterceptor
    private val sessionReplayLifecycleCallback: LifecycleCallback
    private val recordedDataQueueHandler: RecordedDataQueueHandler
    private val viewOnDrawInterceptor: ViewOnDrawInterceptor
    private val internalLogger: InternalLogger
    private val uiHandler: Handler
    private val resourceResolver: ResourceResolver
    private val pixelCopyCapture: PixelCopyCapture?
    private var shouldRecord = false

    @Suppress("LongParameterList")
    constructor(
        appContext: Application,
        resourcesWriter: ResourcesWriter,
        rumContextProvider: RumContextProvider,
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy,
        touchPrivacyManager: TouchPrivacyManager,
        recordWriter: RecordWriter,
        timeProvider: TimeProvider,
        mappers: List<MapperTypeWrapper<*>> = emptyList(),
        customOptionSelectorDetectors: List<OptionSelectorDetector> = emptyList(),
        customDrawableMappers: List<DrawableToColorMapper>,
        windowInspector: WindowInspector = WindowInspector,
        sdkCore: FeatureSdkCore,
        resourceDataStoreManager: ResourceDataStoreManager,
        dynamicOptimizationEnabled: Boolean,
        internalCallback: SessionReplayInternalCallback,
        heatmapIdentifierRegistry: HeatmapIdentifierRegistry? = null,
        pixelCopyCaptureEnabled: Boolean = false
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
            MutationResolver(internalLogger),
            timeProvider
        )

        this.appContext = appContext
        this.textAndInputPrivacy = textAndInputPrivacy
        this.imagePrivacy = imagePrivacy
        this.customOptionSelectorDetectors = customOptionSelectorDetectors
        this.windowInspector = windowInspector
        this.recordedDataQueueHandler = RecordedDataQueueHandler(
            processor = processor,
            rumContextDataHandler = rumContextDataHandler,
            internalLogger = internalLogger,
            executorService = sdkCore.createSingleThreadExecutorService(
                "sr-event-processing"
            ),
            recordedDataQueue = ConcurrentLinkedQueue(),
            timeProvider = timeProvider
        )

        val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
        val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter
        val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver
        val drawableToColorMapper: DrawableToColorMapper =
            DrawableToColorMapper.getDefault(customDrawableMappers)

        val defaultVWM = ViewWireframeMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )

        val bitmapCachesManager = BitmapCachesManager(
            bitmapPool = BitmapPool(),
            resourcesLRUCache = ResourcesLRUCache(),
            logger = internalLogger,
            keyGenerator = ResourceDrawableKeyGenerator()
        )

        this.resourceResolver = ResourceResolver(
            applicationContext = appContext,
            recordedDataQueueHandler = recordedDataQueueHandler,
            pathUtils = PathUtils(internalLogger, bitmapCachesManager),
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

        // Named so it can be reused by the composition-tree pipeline below, in addition to the
        // default pipeline's SnapshotProducer.
        val imageWireframeHelper = DefaultImageWireframeHelper(
            logger = internalLogger,
            resourceResolver = resourceResolver,
            viewIdentifierResolver = viewIdentifierResolver,
            viewUtilsInternal = ViewUtilsInternal(),
            imageTypeResolver = ImageTypeResolver()
        )

        // Mixed PixelCopy + isolation capture, gated by pixelCopyCaptureEnabled.
        // PixelCopy requires API 26+; the isolation (View.draw) fallback works on any API level.
        this.pixelCopyCapture = if (pixelCopyCaptureEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PixelCopyCapture(resourceResolver)
        } else {
            null
        }

        // Captures unmapped leaf views via View.draw when the pipeline is enabled.
        val pixelCopyFallbackMapper = if (pixelCopyCaptureEnabled) {
            PixelCopyFallbackMapper(
                fallbackMapper = defaultVWM,
                viewIdentifierResolver = viewIdentifierResolver,
                colorStringFormatter = colorStringFormatter,
                viewBoundsResolver = viewBoundsResolver,
                drawableToColorMapper = drawableToColorMapper
            )
        } else {
            null
        }

        // Maps TextViews directly (crisp/selectable text) instead of falling back to a pixel
        // capture — used by CompositionTreeBuilder in the composition-tree pipeline.
        val textViewMapper = if (pixelCopyCaptureEnabled) {
            TextViewMapper<TextView>(
                viewIdentifierResolver,
                colorStringFormatter,
                viewBoundsResolver,
                drawableToColorMapper
            )
        } else {
            null
        }

        // Only the composition-tree pipeline below reads pixelCopyCapture/pixelCopyFallbackMapper —
        // the default pipeline (SnapshotProducer/TreeViewTraversal) never does, so its behavior
        // is unchanged whether pixelCopyCaptureEnabled is on or off.
        val compositionTreeBuilder = pixelCopyFallbackMapper?.let { fallbackMapper ->
            textViewMapper?.let { textMapper ->
                CompositionTreeBuilder(
                    viewIdentifierResolver = viewIdentifierResolver,
                    viewBoundsResolver = viewBoundsResolver,
                    textViewMapper = textMapper,
                    pixelCopyFallbackMapper = fallbackMapper,
                    touchPrivacyManager = touchPrivacyManager,
                    imageWireframeHelper = imageWireframeHelper,
                    pixelCropCallback = pixelCopyCapture
                )
            }
        }

        this.viewOnDrawInterceptor = ViewOnDrawInterceptor(
            internalLogger = internalLogger,
            onDrawListenerProducer = DefaultOnDrawListenerProducer(
                snapshotProducer = SnapshotProducer(
                    imageWireframeHelper = imageWireframeHelper,
                    treeViewTraversal = TreeViewTraversal(
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
                    optionSelectorDetector = ComposedOptionSelectorDetector(
                        customOptionSelectorDetectors + DefaultOptionSelectorDetector()
                    ),
                    touchPrivacyManager = touchPrivacyManager,
                    internalLogger = internalLogger,
                    heatmapResolver = heatmapIdentifierRegistry?.let {
                        HeatmapIdentifierResolver(
                            appPackageName = appContext.packageName,
                            registry = it,
                            internalLogger = internalLogger
                        )
                    }
                ),
                recordedDataQueueHandler = recordedDataQueueHandler,
                sdkCore = sdkCore,
                dynamicOptimizationEnabled = dynamicOptimizationEnabled,
                rumContextProvider = rumContextProvider,
                pixelCopyCapture = pixelCopyCapture,
                compositionTreeBuilder = compositionTreeBuilder
            ),
            touchPrivacyManager = touchPrivacyManager
        )
        this.windowCallbackInterceptor = WindowCallbackInterceptor(
            recordedDataQueueHandler,
            viewOnDrawInterceptor,
            timeProvider,
            rumContextProvider,
            internalLogger,
            imagePrivacy,
            textAndInputPrivacy,
            touchPrivacyManager
        )
        this.sessionReplayLifecycleCallback = SessionReplayLifecycleCallback(this)

        // Register fragment lifecycle callbacks for clients initialized after the Application.onCreate phase
        internalCallback.getCurrentActivity()?.let {
            sessionReplayLifecycleCallback.setCurrentWindow(it)
            sessionReplayLifecycleCallback.registerFragmentLifecycleCallbacks(it)
        }

        // Expose this object so it can be used to dynamically add resources
        internalCallback.setResourceQueue(ResourceQueueImpl(this.recordedDataQueueHandler))

        this.uiHandler = Handler(Looper.getMainLooper())
        this.internalLogger = internalLogger
    }

    @VisibleForTesting
    @Suppress("LongParameterList")
    constructor(
        appContext: Application,
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy,
        customOptionSelectorDetectors: List<OptionSelectorDetector>,
        windowInspector: WindowInspector = WindowInspector,
        windowCallbackInterceptor: WindowCallbackInterceptor,
        sessionReplayLifecycleCallback: LifecycleCallback,
        viewOnDrawInterceptor: ViewOnDrawInterceptor,
        recordedDataQueueHandler: RecordedDataQueueHandler,
        resourceResolver: ResourceResolver,
        uiHandler: Handler,
        internalLogger: InternalLogger
    ) {
        this.appContext = appContext
        this.textAndInputPrivacy = textAndInputPrivacy
        this.imagePrivacy = imagePrivacy
        this.customOptionSelectorDetectors = customOptionSelectorDetectors
        this.windowInspector = windowInspector
        this.recordedDataQueueHandler = recordedDataQueueHandler
        this.viewOnDrawInterceptor = viewOnDrawInterceptor
        this.windowCallbackInterceptor = windowCallbackInterceptor
        this.sessionReplayLifecycleCallback = sessionReplayLifecycleCallback
        this.resourceResolver = resourceResolver
        this.uiHandler = uiHandler
        this.internalLogger = internalLogger
        this.pixelCopyCapture = null
    }

    override fun stopProcessingRecords() {
        recordedDataQueueHandler.clearAndStopProcessingQueue()
    }

    override fun registerCallbacks() {
        appContext.registerActivityLifecycleCallbacks(sessionReplayLifecycleCallback)
        resourceResolver.registerCallbacks()
    }

    override fun unregisterCallbacks() {
        appContext.unregisterActivityLifecycleCallbacks(sessionReplayLifecycleCallback)
        resourceResolver.unregisterCallbacks()
    }

    override fun resumeRecorders() {
        uiHandler.post {
            shouldRecord = true
            val windows = sessionReplayLifecycleCallback.getCurrentWindows()
            val decorViews = windowInspector.getGlobalWindowViews(internalLogger)
            pixelCopyCapture?.setCurrentWindow(windows.firstOrNull())
            windowCallbackInterceptor.intercept(windows, appContext)
            viewOnDrawInterceptor.intercept(decorViews, textAndInputPrivacy, imagePrivacy)
        }
    }

    override fun stopRecorders() {
        uiHandler.post {
            viewOnDrawInterceptor.stopIntercepting()
            windowCallbackInterceptor.stopIntercepting()
            shouldRecord = false
            pixelCopyCapture?.setCurrentWindow(null)
            pixelCopyCapture?.release()
        }
    }

    @MainThread
    override fun onWindowsAdded(windows: List<Window>) {
        if (shouldRecord) {
            val allWindows = sessionReplayLifecycleCallback.getCurrentWindows()
            val decorViews = windowInspector.getGlobalWindowViews(internalLogger)
            pixelCopyCapture?.setCurrentWindow(allWindows.firstOrNull())
            windowCallbackInterceptor.intercept(windows, appContext)
            viewOnDrawInterceptor.intercept(decorViews, textAndInputPrivacy, imagePrivacy)
        }
    }

    @MainThread
    override fun onWindowsRemoved(windows: List<Window>) {
        if (shouldRecord) {
            val allWindows = sessionReplayLifecycleCallback.getCurrentWindows()
            val decorViews = windowInspector.getGlobalWindowViews(internalLogger)
            pixelCopyCapture?.setCurrentWindow(allWindows.firstOrNull())
            windowCallbackInterceptor.stopIntercepting(windows)
            viewOnDrawInterceptor.intercept(decorViews, textAndInputPrivacy, imagePrivacy)
        }
    }
}
