/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk.tracer

class NdkSpan(
    private val tracerPointer:Long,
    private val tracer: NdkTracer,
    internal val spanId:String,
    private val parentId:String?
) {

    fun finish() {
        if (tracerPointer == 0L) {
            return
        }
        tracer.finishSpan(this)
    }

}