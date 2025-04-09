/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk.tracer

import android.util.Log
import java.lang.NullPointerException
import kotlin.system.measureNanoTime

class NdkTracer {

    private val tracerPointer: Long?
    private var librariesLoaded = false

    init {
        loadNdkLibraries()
        tracerPointer = createTracer()
    }

    fun startSpan(spanName: String, parentId: String? = null): NdkSpan? {
        if (tracerPointer == null) {
            Log.v("NdkTracer", "Tracer instance is null")
            return null
        }
        val spanId: String
        measureNanoTime {
            spanId = nativeStartSpan(tracerPointer, spanName, parentId)
        }.let {
            Log.v("NdkTracer", "Span was started in ${it.toMilliseconds()} ms")
        }
        return NdkSpan(tracerPointer, this, spanId, parentId)
    }

    fun finishSpan(span: NdkSpan): Boolean {
        if (tracerPointer == null) {
            Log.v("NdkTracer", "Tracer instance is null")
            return false
        }
        val toReturn: Boolean
        measureNanoTime {
            toReturn = nativeFinishSpan(tracerPointer, span.spanId)
        }.let {
            Log.v("NdkTracer", "Span was finished in ${it.toMilliseconds()} ms")
        }
        return toReturn
    }

    fun consumeSpan(span: String) {
        Log.v("NdkTracer", "Span consumed: $span")
    }

    private fun loadNdkLibraries() {
        var exception: Throwable? = null
        try {
            System.loadLibrary("tracer_lib")
            System.loadLibrary("datadog-tracer-native-lib")
            librariesLoaded = true
        } catch (e: SecurityException) {
            exception = e
        } catch (@SuppressWarnings("TooGenericExceptionCaught") e: NullPointerException) {
            exception = e
        } catch (e: UnsatisfiedLinkError) {
            exception = e
        }
        exception?.let {
            Log.v("NdkTracer", "Failed to load native library", it)
        }
    }

    private fun createTracer(): Long? {
        if (!librariesLoaded) {
            Log.v("NdkTracer", "Native libraries not loaded")
            return null
        }
        val tracerPointer: Long
        measureNanoTime {
            tracerPointer = nativeCreateTracer()
        }.let {
            Log.v("NdkTracer", "Tracer instance was created in ${it.toMilliseconds()} ms")
        }
        return tracerPointer
    }


    private fun Long.toMilliseconds(): Double {
        return this / 1_000_000.0
    }

    // region Native methods
    private external fun nativeCreateTracer(): Long

    private external fun nativeStartSpan(
        tracerPointer: Long,
        operationName: String,
        parentId: String?
    ): String

    private external fun nativeFinishSpan(
        tracerPointer: Long,
        spanId: String
    ): Boolean

}