/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk.tracer

import android.util.Log
import java.lang.NullPointerException

object NdkTracerBootstrap {
    internal var librariesLoaded = false

    init {
        loadNdkLibraries()
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
}