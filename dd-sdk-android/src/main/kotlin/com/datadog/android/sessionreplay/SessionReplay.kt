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
import io.opentracing.util.GlobalTracer
import okhttp3.OkHttpClient
import java.io.File
import java.lang.ref.WeakReference

object SessionReplay {

    enum class RecordStrategy {
        SCREEN_SHOTS,
        HYBRID
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


    fun record(window: Window, strategy: RecordStrategy = RecordStrategy.HYBRID) {
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
            captureScreen(strategy, window)
        }
        captureScreen(strategy, window)
        windowReference = WeakReference(window)
    }

    private fun captureScreen(
        strategy: RecordStrategy,
        window: Window
    ) {
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
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            val span = GlobalTracer.get().buildSpan("view tree capture").start()
            val treeView = window.decorView.rootView.toJson(CoreFeature.contextRef.get()!!, scale)
            sessionReplayVitals.logVitals()
            span.finish()
            treeView?.let { tree ->
                persister.persist(tree)
            }
        }, 500)
    }

    private fun scheduleScreenCapture(window: Window) {
        handler.removeCallbacksAndMessages(null)

        handler.postDelayed({
            val span = GlobalTracer.get().buildSpan("view screenshot capture").start()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getBitmapFromWindow(window) { bitmap ->
                    persister.persist(bitmap, System.nanoTime().toString())
                }
            }


            span.finish()

        }, 1000)
    }

    fun stop(window: Window) {
        if (windowReference.get() == window) {
            handler.removeCallbacksAndMessages(null)
            windowReference.clear()
        }
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