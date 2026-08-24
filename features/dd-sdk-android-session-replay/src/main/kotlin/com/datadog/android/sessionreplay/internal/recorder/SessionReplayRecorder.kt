/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
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
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistry
import com.datadog.android.sessionreplay.internal.processor.MutationResolver
import com.datadog.android.sessionreplay.internal.processor.RecordedDataProcessor
import com.datadog.android.sessionreplay.internal.processor.ResourceQueueImpl
import com.datadog.android.sessionreplay.internal.processor.RumContextDataHandler
import com.datadog.android.sessionreplay.internal.recorder.callback.OnWindowRefreshedCallback
import com.datadog.android.sessionreplay.internal.recorder.mapper.DecorViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.EmbeddedContentViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.HiddenViewMapper
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
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

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
    private val windowFromDecorView: (View) -> Window?
    private var shouldRecord = false

    /** Whether a capture has been asked for and not yet honoured — see [requestCapture]. */
    private val captureRequested = AtomicBoolean(false)

    /**
     * The slots a standing capture request still owes a placeholder, guarded by itself.
     *
     * A request is satisfied by the placeholders reaching storage, not by a snapshot being taken:
     * the queue can drop the snapshot before it is processed, the write can fail, and the slot can
     * be absent from the tree the traversal walked. Each leaves the request outstanding.
     */
    private val slotsAwaitingPlaceholder = mutableSetOf<String>()

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
        embeddedContentSlotRegistry: EmbeddedContentSlotRegistry,
        heatmapIdentifierRegistry: HeatmapIdentifierRegistry? = null
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
            timeProvider,
            embeddedContentSlotRegistry
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
        val viewUtilsInternal = ViewUtilsInternal()
        val embeddedContentViewMapper = EmbeddedContentViewMapper(
            viewIdentifierResolver = viewIdentifierResolver,
            colorStringFormatter = colorStringFormatter,
            viewBoundsResolver = viewBoundsResolver,
            drawableToColorMapper = drawableToColorMapper,
            viewUtilsInternal = viewUtilsInternal,
            embeddedContentSlotRegistry = embeddedContentSlotRegistry
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

        this.viewOnDrawInterceptor = ViewOnDrawInterceptor(
            internalLogger = internalLogger,
            onDrawListenerProducer = DefaultOnDrawListenerProducer(
                snapshotProducer = SnapshotProducer(
                    imageWireframeHelper = DefaultImageWireframeHelper(
                        logger = internalLogger,
                        resourceResolver = resourceResolver,
                        viewIdentifierResolver = viewIdentifierResolver,
                        viewUtilsInternal = ViewUtilsInternal(),
                        imageTypeResolver = ImageTypeResolver()
                    ),
                    treeViewTraversal = TreeViewTraversal(
                        mappers = mappers,
                        defaultViewMapper = defaultVWM,
                        decorViewMapper = DecorViewMapper(defaultVWM, viewIdentifierResolver),
                        hiddenViewMapper = HiddenViewMapper(
                            viewBoundsResolver = viewBoundsResolver,
                            viewIdentifierResolver = viewIdentifierResolver
                        ),
                        viewUtilsInternal = viewUtilsInternal,
                        internalLogger = internalLogger,
                        embeddedContentViewMapper = embeddedContentViewMapper
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
                    },
                    embeddedContentViewMapper = embeddedContentViewMapper
                ),
                recordedDataQueueHandler = recordedDataQueueHandler,
                sdkCore = sdkCore,
                dynamicOptimizationEnabled = dynamicOptimizationEnabled,
                rumContextProvider = rumContextProvider
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
        this.windowFromDecorView = { WindowReflectionUtils.getWindowFromDecorView(it, internalLogger) }
        embeddedContentSlotRegistry.addPlaceholderListener(::onPlaceholderWritten)
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
        internalLogger: InternalLogger,
        embeddedContentSlotRegistry: EmbeddedContentSlotRegistry = EmbeddedContentSlotRegistry(),
        windowFromDecorView: (View) -> Window? = {
            WindowReflectionUtils.getWindowFromDecorView(it, internalLogger)
        }
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
        this.windowFromDecorView = windowFromDecorView
        this.internalLogger = internalLogger
        embeddedContentSlotRegistry.addPlaceholderListener(::onPlaceholderWritten)
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
            @Suppress("ThreadSafety") // handler posts to the main looper
            interceptCurrentWindows(sessionReplayLifecycleCallback.getCurrentWindows())
        }
    }

    /**
     * A capture request is a standing obligation rather than a single attempt: the placeholder
     * wireframe for each of [slotIds] has to reach the player before the embedded records that
     * composite into it, and records are replayed in timestamp order, not write order. Dropping the
     * request because the recorder cannot serve it yet would leave those records waiting on a
     * placeholder that never arrives, so it survives until the placeholders are actually written.
     */
    override fun requestCapture(slotIds: Set<String>) {
        synchronized(slotsAwaitingPlaceholder) {
            slotsAwaitingPlaceholder.addAll(slotIds)
        }
        captureRequested.set(true)
        uiHandler.post {
            @Suppress("ThreadSafety") // handler posts to the main looper
            performRequestedCapture(attempt = 0)
        }
    }

    /** Clears the request once every slot it was made for has its placeholder in storage. */
    private fun onPlaceholderWritten(slotId: String) {
        val isSatisfied = synchronized(slotsAwaitingPlaceholder) {
            slotsAwaitingPlaceholder.remove(slotId)
            slotsAwaitingPlaceholder.isEmpty()
        }
        if (isSatisfied) {
            captureRequested.set(false)
        }
    }

    @MainThread
    private fun performRequestedCapture(attempt: Int) {
        if (!shouldRecord || !captureRequested.get()) {
            return
        }
        when (viewOnDrawInterceptor.requestCapture()) {
            // Taking the snapshot does not discharge the request — only the placeholder reaching
            // storage does, which happens asynchronously and may not happen at all. Retrying until
            // then also drives recovery: each capture drains the processing queue, which is what
            // an expired snapshot left stuck behind.
            ViewOnDrawInterceptor.CaptureRequestResult.CAPTURED -> Unit

            ViewOnDrawInterceptor.CaptureRequestResult.NOT_INTERCEPTING -> {
                // The request arrived before this activity's window existed. Intercepting registers
                // the listeners and serves the request itself.
                interceptCurrentWindows(sessionReplayLifecycleCallback.getCurrentWindows())
            }

            // Either no window survived, or the queue refused the item. Neither clears within this
            // frame, so the request is given a few further chances rather than being left to
            // whatever draw or batch happens to come along next.
            ViewOnDrawInterceptor.CaptureRequestResult.NOT_CAPTURED -> Unit
        }
        if (!captureRequested.get()) {
            return
        }
        if (attempt < MAX_CAPTURE_ATTEMPTS) {
            uiHandler.postDelayed(
                {
                    @Suppress("ThreadSafety") // handler posts to the main looper
                    performRequestedCapture(attempt + 1)
                },
                // Doubling each time, so the last attempts fall outside the window in which the
                // processing queue discards an item it could not consume in time.
                CAPTURE_RETRY_DELAY_IN_MS shl attempt
            )
        } else {
            // Out of attempts. The slots are released rather than left pending so that a later
            // request is not immediately satisfied by a placeholder owed to this one; the batches
            // waiting on them are written by the receiver's own bounds.
            abandonCaptureRequest()
        }
    }

    private fun abandonCaptureRequest() {
        synchronized(slotsAwaitingPlaceholder) {
            slotsAwaitingPlaceholder.clear()
        }
        captureRequested.set(false)
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
            interceptCurrentWindows(windows)
        }
    }

    /**
     * Starts intercepting every window currently open, [windows] included. Any standing capture
     * request is then served explicitly: intercepting does take a snapshot of its own, but that one
     * goes through the debouncer, which is free to drop it. The request stays standing either way —
     * it is discharged by the placeholders being written, not by this snapshot being taken.
     */
    @MainThread
    private fun interceptCurrentWindows(windows: List<Window>) {
        val decorViews = windowInspector.getGlobalWindowViews(internalLogger)
        windowCallbackInterceptor.intercept(windows + resolveUntrackedWindows(decorViews, windows), appContext)
        viewOnDrawInterceptor.intercept(decorViews, textAndInputPrivacy, imagePrivacy)
        if (captureRequested.get()) {
            viewOnDrawInterceptor.requestCapture()
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

    // a window already open (e.g. a dialog) isn't reported through the Activity lifecycle
    private fun resolveUntrackedWindows(decorViews: List<View>, knownWindows: List<Window>): List<Window> {
        return decorViews
            .filterNot { it.width == 0 || it.height == 0 }
            .mapNotNull { windowFromDecorView(it) }
            .filterNot { it in knownWindows || windowCallbackInterceptor.isExcluded(it) }
            .distinct()
    }

    internal companion object {
        // A saturated queue, or a window in the middle of being replaced, clears within a frame or
        // two; past that, retrying is unlikely to be what makes the difference.
        internal const val MAX_CAPTURE_ATTEMPTS: Int = 4

        // one frame time
        internal const val CAPTURE_RETRY_DELAY_IN_MS: Long = 64
    }
}
