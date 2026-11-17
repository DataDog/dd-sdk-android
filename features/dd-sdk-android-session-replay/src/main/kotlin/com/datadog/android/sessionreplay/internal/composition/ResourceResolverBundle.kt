/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.app.Application
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistry
import com.datadog.android.sessionreplay.internal.processor.MutationResolver
import com.datadog.android.sessionreplay.internal.processor.RecordedDataProcessor
import com.datadog.android.sessionreplay.internal.processor.RumContextDataHandler
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapCachesManager
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapPool
import com.datadog.android.sessionreplay.internal.recorder.resources.MD5HashGenerator
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceDrawableKeyGenerator
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceItemCreationHandler
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourcesLRUCache
import com.datadog.android.sessionreplay.internal.recorder.resources.WebPImageCompression
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.storage.ResourcesWriter
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.internal.utils.PathUtils
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import java.util.concurrent.ConcurrentLinkedQueue

/** A [ResourceResolver] and the queue that persists the image resources it registers. */
internal class ResourceResolverBundle(
    val resourceResolver: ResourceResolver,
    val dataQueueHandler: RecordedDataQueueHandler
)

/**
 * Builds a [ResourceResolver] wired to its own resource-registration queue, which hashes, encodes
 * and deduplicates images before writing them. Ported from the legacy `SessionReplayRecorder`,
 * which builds the same objects for its own pipeline, except that registered resources drain
 * eagerly: see [ResourceItemCreationHandler] for why.
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
    timeProvider: TimeProvider,
    eventProcessingExecutorName: String,
    drawablesExecutorName: String
): ResourceResolverBundle {
    val internalLogger = sdkCore.internalLogger

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
            eagerDrain = true
        )
    )

    return ResourceResolverBundle(resourceResolver, recordedDataQueueHandler)
}
