/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.testing

import android.content.Context
import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
import com.datadog.android.sessionreplay.internal.async.ResourceRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.async.SnapshotRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.async.TouchEventRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapCachesManager
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapPool
import com.datadog.android.sessionreplay.internal.recorder.resources.DefaultImageWireframeHelper
import com.datadog.android.sessionreplay.internal.recorder.resources.ImageTypeResolver
import com.datadog.android.sessionreplay.internal.recorder.resources.MD5HashGenerator
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceDrawableKeyGenerator
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourcesLRUCache
import com.datadog.android.sessionreplay.internal.recorder.resources.WebPImageCompression
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.internal.utils.PathUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import com.datadog.android.utils.createNoOpInternalLogger
import java.util.concurrent.ExecutorService

fun createImageWireframeHelper(
    context: Context,
    resourceExecutor: ExecutorService,
    drawableExecutor: ExecutorService,
    onResource: (ByteArray) -> Unit = {}
): ImageWireframeHelper {
    val logger = createNoOpInternalLogger()
    val caches = BitmapCachesManager(ResourcesLRUCache(), BitmapPool(), logger, ResourceDrawableKeyGenerator())
    val resolver = ResourceResolver(
        applicationContext = context.applicationContext,
        bitmapCachesManager = caches,
        pathUtils = PathUtils(logger, caches),
        threadPoolExecutor = resourceExecutor,
        drawableUtils = DrawableUtils(logger, caches),
        drawableExecutor = drawableExecutor,
        webPImageCompression = WebPImageCompression(logger),
        logger = logger,
        md5HashGenerator = MD5HashGenerator(logger),
        recordedDataQueueHandler = CapturedResources(onResource)
    )
    return DefaultImageWireframeHelper(
        logger,
        resolver,
        DefaultViewIdentifierResolver,
        ViewUtilsInternal(),
        ImageTypeResolver()
    )
}

private class CapturedResources(private val onResource: (ByteArray) -> Unit) : DataQueueHandler {
    override fun addResourceItem(identifier: String, resourceData: ByteArray, mimeType: String?):
        ResourceRecordedDataQueueItem? {
        onResource(resourceData)
        return null
    }

    override fun addTouchEventItem(pointerInteractions: List<MobileSegment.MobileRecord>):
        TouchEventRecordedDataQueueItem? = null

    override fun addSnapshotItem(systemInformation: SystemInformation): SnapshotRecordedDataQueueItem? = null

    override fun tryToConsumeItems() = Unit

    override fun clearAndStopProcessingQueue() = Unit
}
