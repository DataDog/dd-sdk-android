/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.fixtures

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
import com.datadog.android.sessionreplay.internal.async.ResourceRecordedDataQueueItem
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
import com.datadog.android.sessionreplay.recorder.InteropViewCallback
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.GlobalBounds
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/** Captures production image resources synchronously for recorder integration tests. */
class SessionReplayRecordingTestFixture(context: Context) {
    val resources = mutableMapOf<String, ByteArray>()
    var pendingJobs = 0
        private set

    private val logger = InternalLogger.UNBOUND
    private val executor = object : AbstractExecutorService() {
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() = Unit
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown(): Boolean = false
        override fun isTerminated(): Boolean = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
    }
    private val queue = object : DataQueueHandler {
        override fun addResourceItem(
            identifier: String,
            resourceData: ByteArray,
            mimeType: String?
        ): ResourceRecordedDataQueueItem? {
            resources[identifier] = resourceData
            return null
        }
        override fun addTouchEventItem(pointerInteractions: List<MobileSegment.MobileRecord>) = null
        override fun addSnapshotItem(systemInformation: SystemInformation) = null
        override fun tryToConsumeItems() = Unit
        override fun clearAndStopProcessingQueue() = Unit
    }
    private val caches = BitmapCachesManager(
        ResourcesLRUCache(),
        BitmapPool(),
        logger,
        ResourceDrawableKeyGenerator()
    )
    private val resolver = ResourceResolver(
        applicationContext = context.applicationContext,
        bitmapCachesManager = caches,
        pathUtils = PathUtils(bitmapCachesManager = caches),
        threadPoolExecutor = executor,
        drawableUtils = DrawableUtils(logger, caches, executor),
        webPImageCompression = WebPImageCompression(logger),
        logger = logger,
        md5HashGenerator = MD5HashGenerator(logger),
        recordedDataQueueHandler = queue
    )
    private val helper = DefaultImageWireframeHelper(
        logger,
        resolver,
        DefaultViewIdentifierResolver,
        ViewUtilsInternal(),
        ImageTypeResolver()
    )
    private val displayMetrics = context.resources.displayMetrics
    val mappingContext = MappingContext(
        systemInformation = SystemInformation(
            GlobalBounds(
                0,
                0,
                (displayMetrics.widthPixels / displayMetrics.density).toLong(),
                (displayMetrics.heightPixels / displayMetrics.density).toLong()
            ),
            screenDensity = displayMetrics.density
        ),
        imageWireframeHelper = helper,
        imagePrivacy = ImagePrivacy.MASK_NONE,
        textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
        touchPrivacyManager = TouchPrivacyManager(TouchPrivacy.SHOW),
        interopViewCallback = object : InteropViewCallback {
            override fun map(view: View, mappingContext: MappingContext): List<MobileSegment.Wireframe> =
                error("Unexpected AndroidView inside the recorded Compose root")
        }
    )
    val callback = object : AsyncJobStatusCallback {
        override fun jobStarted() { pendingJobs++ }
        override fun jobFinished() { pendingJobs-- }
    }
    fun decode(wireframe: MobileSegment.Wireframe.ImageWireframe): Bitmap {
        val bytes = checkNotNull(resources[wireframe.resourceId]) { "No payload for image ${wireframe.id}" }
        return checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }
}
