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
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsProperties
import com.datadog.android.core.internal.CoreFeature
import io.opentracing.util.GlobalTracer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.Base64
import androidx.compose.ui.semantics.getOrNull


object SessionReplay {

    lateinit var root: ComposeNode
    val rootNodes: MutableMap<Int, ComposeNode> = mutableMapOf()
    val nodes: MutableMap<Int, ComposeNode> = mutableMapOf()

    // private val viewTreeStorage by lazy {  File(CoreFeature.contextRef.get()!!.cacheDir, "view-tree") }
    private val handler = Handler(Looper.getMainLooper())
    private val httpclient: OkHttpClient = OkHttpClient.Builder().build()
    private val workerThread = WorkerThread()

    private val scale by lazy {
       CoreFeature.contextRef.get()!!.getResources().getDisplayMetrics().density;
    }
    var windowReference: WeakReference<Window> = WeakReference(null)

    init {
        workerThread.start()
        // GlobalRum.get().setEventListener(object : RumMonitor.EventListenerCallback {
        //     override fun onEvent() {
        //         takeScrrenShot()
        //     }
        // })
    }

    fun record(window: Window) {
       window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
           if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
               scheduleViewTreeCapture(window)
           }
       }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            scheduleViewTreeCapture(window)
        }
        windowReference = WeakReference(window)
    }

    fun takeScrrenShot() {
        windowReference.get()?.let {
            // scheduleViewTreeCapture(it)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun scheduleViewTreeCapture(window: Window) {
        handler.removeCallbacksAndMessages(null)

        handler.postDelayed({
            val span = GlobalTracer.get().buildSpan("view tree capture").start()
            val treeView = window.decorView.rootView.toJson(CoreFeature.contextRef.get()!!, scale)
            span.finish()
            treeView?.let { tree ->
                workerThread.handler.post {
                    val request = Request.Builder()
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(null, tree.toString()))
                        .url("http://10.0.2.2:3000/save-view-tree")
                        .build()
                    httpclient.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            Log.e(SessionReplay.javaClass.simpleName, e.message?:"", e)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            Log.v(SessionReplay.javaClass.simpleName, response.message())
                        }
                    })

                }
            }
        }, 500)
    }

    private fun scheduleScreenCapture(window: Window) {
        handler.removeCallbacksAndMessages(null)

        handler.postDelayed({
            val span = GlobalTracer.get().buildSpan("view screenshot").start()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getBitmapFormView(window) { bitmap ->
                    workerThread.handler.post {
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        val bitMapData: ByteArray = stream.toByteArray()
                        val encodedImage: String = Base64.getEncoder().encodeToString(bitMapData)
                        val request = Request.Builder()
                            .addHeader("Content-Type", "text/plain")
                            .post(RequestBody.create(null, encodedImage))
                            .url("http://10.0.2.2:3000/save-screenshot")
                            .build()
                        httpclient.newCall(request).enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                            }

                            override fun onResponse(call: Call, response: Response) {
                            }
                        })

                    }
                }
            }

            span.finish()

        }, 1000)
    }

    fun stop(window: Window) {
        if(windowReference.get()==window){
            handler.removeCallbacksAndMessages(null)
            windowReference.clear()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    fun getBitmapFormView(window: Window, callback: (bitmap: Bitmap) -> Any) {
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
        // val drawModifier = node.layoutInfo.getModifierInfo().filter { it.modifier is DrawModifier }
        // Log.v("dd", semantics.toString())
    }

    // @JvmStatic
    // fun addNode(view: View, node: ComposeNodeView) {
    //
    // }

    @JvmStatic
    fun detachNode(view: View, node: ComposeNode) {
        nodes[node.parentId]?.children?.remove(node.id)
        nodes.remove(node.id)
        if (node.parentId == 0) {
            rootNodes.remove(view.hashCode())
        }
    }
}