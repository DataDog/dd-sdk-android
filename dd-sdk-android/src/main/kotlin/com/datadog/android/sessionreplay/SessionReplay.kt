/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.rum.GlobalRum
import com.datadog.android.sessionreplay.model.CreationReason
import com.datadog.android.sessionreplay.model.FullSnapshotRecord
import com.datadog.android.sessionreplay.model.Offset
import com.datadog.android.sessionreplay.model.RecordData
import com.datadog.android.sessionreplay.model.Segment
import io.opentracing.util.GlobalTracer
import okhttp3.OkHttpClient
import java.io.File
import java.lang.ref.WeakReference

object SessionReplay {

    private const val SCREEN_CAPTURE_FREQUENCY = 1000L

    internal enum class RecordStrategy {
        SCREEN_SHOTS,
        HYBRID
    }

    internal enum class RecordFrequencyStrategy {
        ON_SCREEN_CHANGE,
        TIME_BASED
    }

    lateinit var root: ComposeNode
    val rootNodes: MutableMap<Int, ComposeNode> = mutableMapOf()
    val nodes: MutableMap<Int, ComposeNode> = mutableMapOf()
    private val viewTreeStorage by lazy {
        File(
            CoreFeature.contextRef.get()!!.cacheDir,
            "view-tree"
        )
    }
    private val sessionReplayVitals by lazy {
        SessionReplayVitals(viewTreeStorage)
    }
    private val handler = Handler(Looper.getMainLooper())
    private val httpclient: OkHttpClient = OkHttpClient.Builder().build()

    private val uploader: SessionReplayUploader by lazy {
        SessionReplayUploader(httpclient, viewTreeStorage)
    }

    private val persister: SessionReplayPersister by lazy {
        SessionReplayPersister(viewTreeStorage, sessionReplayVitals)
    }

    private val scale by lazy {
        CoreFeature.contextRef.get()!!.getResources().getDisplayMetrics().density;
    }
    var windowReference: WeakReference<Window> = WeakReference(null)

    private var screenRecordStrategy = RecordStrategy.HYBRID

    internal fun record(
        window: Window,
        strategy: RecordStrategy = RecordStrategy.HYBRID,
        frequencyStrategy: RecordFrequencyStrategy = RecordFrequencyStrategy.ON_SCREEN_CHANGE
    ) {
        screenRecordStrategy = strategy
        windowReference = WeakReference(window)

        if (frequencyStrategy == RecordFrequencyStrategy.ON_SCREEN_CHANGE) {
            handler.removeCallbacksAndMessages(null)
            window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({ captureScreen(strategy) }, SCREEN_CAPTURE_FREQUENCY)
            }
            handler.removeCallbacksAndMessages(null)
            handler.post { captureScreen(strategy) }
        } else {
            handler.post(captureScreenRunnable)
        }
        uploader.startUploading()
    }

    private val captureScreenRunnable: Runnable = object : Runnable {

        override fun run() {
            captureScreen(screenRecordStrategy)
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed(this, SCREEN_CAPTURE_FREQUENCY)
        }
    }

    internal fun stop(window: Window) {
        if (windowReference.get() == window) {
            handler.removeCallbacksAndMessages(null)
            windowReference.clear()
        }
    }

    private fun captureScreen(
        strategy: RecordStrategy
    ) {
        val window = windowReference.get() ?: return
        if (strategy == RecordStrategy.HYBRID &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        ) {
            scheduleViewTreeCapture(window)
        } else {
            scheduleScreenCapture(window)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun scheduleViewTreeCapture(window: Window) {
        val span = GlobalTracer.get().buildSpan("view tree capture").start()
        val context = GlobalRum.getRumContext()

        val node = window.decorView.rootView.toNode(scale)
        sessionReplayVitals.logVitals()
        span.finish()
        val start = System.currentTimeMillis()
        node?.let { tree ->
            val segment = Segment(
                applicationId = context.applicationId,
                sessionId = context.sessionId,
                viewId = context.viewId ?: "",
                start = start,
                end = start,
                hasFullSnapshot = true,
                recordsCount = 1,
                creationReason = CreationReason.VIEW_CHANGE,
                indexInView = 0,
                records = listOf(
                    FullSnapshotRecord(
                        timestamp = start,
                        data = RecordData(
                            tree,
                            initialOffset = Offset(0, 0)
                        )
                    )
                )
            )
            persister.persist(segment)
        }
    }

    private fun scheduleScreenCapture(window: Window) {
        val span = GlobalTracer.get().buildSpan("view screenshot capture").start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getBitmapFromWindow(window) { bitmap ->
                persister.persist(bitmap, System.nanoTime().toString())
            }
        }
        span.finish()
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    fun getBitmapFromWindow(window: Window, callback: (bitmap: Bitmap) -> Any) {
        val view = window.decorView
        val bitmap: Bitmap =
            Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888)
        val locations = IntArray(2)
        val rect = Rect(
            locations[0],
            locations[1],
            locations[0] + view.getWidth(),
            locations[1] + view.getHeight()
        )
        PixelCopy.request(window, rect, bitmap, { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
                callback(bitmap)
            }
        }, Handler(Looper.getMainLooper()))
    }

    @JvmStatic
    fun addNode(view: View, node: ComposeNode) {
        if (node.parentId == 0) {
            root = node
            rootNodes[view.hashCode()] = node
            nodes.clear()
        }
        nodes[node.id] = node
        nodes[node.parentId]?.let {
            it.children[node.id] = node
        }
    }

    @JvmStatic
    fun detachNode(view: View, node: ComposeNode) {
        nodes[node.parentId]?.children?.remove(node.id)
        nodes.remove(node.id)
        if (node.parentId == 0) {
            rootNodes.remove(view.hashCode())
        }
    }

    fun persistBitmap(bitmap: Bitmap, id: String) {
        persister.persist(bitmap, id)
    }
}