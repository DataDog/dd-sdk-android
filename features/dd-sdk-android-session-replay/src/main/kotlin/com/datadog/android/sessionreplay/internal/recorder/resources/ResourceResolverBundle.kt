/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.app.Application
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistry
import com.datadog.android.sessionreplay.internal.processor.MutationResolver
import com.datadog.android.sessionreplay.internal.processor.RecordedDataProcessor
import com.datadog.android.sessionreplay.internal.processor.RumContextDataHandler
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.storage.ResourcesWriter
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.internal.utils.PathUtils
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import java.util.concurrent.ConcurrentLinkedQueue

/** Everything a pipeline needs to register image resources - shared by the legacy and composition pipelines. */
internal class ResourceResolverBundle(
    val resourceResolver: ResourceResolver,
    val dataQueueHandler: RecordedDataQueueHandler
)

/**
 * Builds a [ResourceResolver] wired to its own dedicated resource-registration queue, exactly as
 * `SessionReplayRecorder` does for the legacy pipeline - factored out so the composition pipeline's
 * pixel-fallback workstream can register/hash/encode/dedup images through the same, already-tested
 * pipeline instead of a second implementation. [eagerResourceDrain] defaults to `false`, preserving
 * the legacy pipeline's exact existing behavior - see [ResourceItemCreationHandler]'s own doc for
 * why the composition pipeline's call site opts in instead.
 */
@Suppress("LongParameterList")
internal fun buildResourceResolver(
    appContext: Application,
    sdkCore: FeatureSdkCore,
    resourceDataStoreManager: ResourceDataStoreManager,
    resourcesWriter: ResourcesWriter,
    recordWriter: RecordWriter,
    rumContextProvider: RumContextProvider,
    embeddedContentSlotRegistry: EmbeddedContentSlotRegistry,
    eventProcessingExecutorName: String = "sr-event-processing",
    drawablesExecutorName: String = "drawables",
    eagerResourceDrain: Boolean = false
): ResourceResolverBundle {
    val internalLogger = sdkCore.internalLogger
    val timeProvider = sdkCore.timeProvider

    val rumContextDataHandler = RumContextDataHandler(rumContextProvider, timeProvider, internalLogger)
    val processor = RecordedDataProcessor(
        resourceDataStoreManager,
        resourcesWriter,
        recordWriter,
        MutationResolver(internalLogger),
        timeProvider,
        embeddedContentSlotRegistry
    )
    val recordedDataQueueHandler = RecordedDataQueueHandler(
        processor = processor,
        rumContextDataHandler = rumContextDataHandler,
        internalLogger = internalLogger,
        executorService = sdkCore.createSingleThreadExecutorService(eventProcessingExecutorName),
        recordedDataQueue = ConcurrentLinkedQueue(),
        timeProvider = timeProvider
    )

    val bitmapCachesManager = BitmapCachesManager(
        bitmapPool = BitmapPool(),
        resourcesLRUCache = ResourcesLRUCache(),
        logger = internalLogger,
        keyGenerator = ResourceDrawableKeyGenerator()
    )

    val resourceResolver = ResourceResolver(
        applicationContext = appContext,
        recordedDataQueueHandler = recordedDataQueueHandler,
        pathUtils = PathUtils(internalLogger, bitmapCachesManager),
        bitmapCachesManager = bitmapCachesManager,
        drawableUtils = DrawableUtils(
            internalLogger,
            bitmapCachesManager,
            sdkCore.createSingleThreadExecutorService(drawablesExecutorName)
        ),
        logger = internalLogger,
        md5HashGenerator = MD5HashGenerator(internalLogger),
        webPImageCompression = WebPImageCompression(internalLogger),
        resourceItemCreationHandler = ResourceItemCreationHandler(
            recordedDataQueueHandler = recordedDataQueueHandler,
            eagerDrain = eagerResourceDrain
        )
    )

    return ResourceResolverBundle(resourceResolver, recordedDataQueueHandler)
}
