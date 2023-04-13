/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.utils

import com.datadog.android.Datadog
import com.datadog.android.v2.api.SdkCore
import java.util.concurrent.atomic.AtomicReference

internal class SdkReference(
    private val sdkInstanceName: String? = null,
    private val onSdkInstanceCaptured: (SdkCore) -> Unit = {}
) {

    private val reference = AtomicReference<SdkCore>(null)

    fun get(): SdkCore? {
        val current = reference.get()
        // TODO RUMM-3195 Check if instance is stopped and remove it?
        @Suppress("IfThenToElvis") // less readable
        return if (current == null) {
            if (Datadog.isInitialized(sdkInstanceName)) {
                val sdkCore = Datadog.getInstance(sdkInstanceName)
                if (reference.compareAndSet(null, sdkCore)) {
                    onSdkInstanceCaptured(sdkCore)
                }
                sdkCore
            } else {
                null
            }
        } else {
            current
        }
    }
}
