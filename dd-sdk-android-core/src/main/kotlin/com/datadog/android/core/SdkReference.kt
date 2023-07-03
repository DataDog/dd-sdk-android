/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import java.util.concurrent.atomic.AtomicReference

/**
 * Class establishing the reference to the particular SDK instance by using its name.
 *
 * Once SDK instance with given name is available (during the [SdkReference.get] call), it will be
 * kept in this class and callback [onSdkInstanceCaptured] will be fired.
 *
 * Once SDK instance with given name becomes inactive (it is stopped), reference will be
 * automatically cleaned up.
 *
 * @param sdkInstanceName Name of the SDK instance to capture. If no name is provided, default
 * SDK instance will be checked.
 * @param onSdkInstanceCaptured Callback which will be fired once SDK instance is acquired.
 */
class SdkReference @JvmOverloads constructor(
    private val sdkInstanceName: String? = null,
    private val onSdkInstanceCaptured: (SdkCore) -> Unit = {}
) {

    private val reference = AtomicReference<SdkCore>(null)

    /**
     * Returns SDK instance if it is acquired, null otherwise.
     */
    fun get(): SdkCore? {
        val current = reference.get()
        return if (current == null) {
            tryAcquire()
        } else {
            val isActive = (current as? DatadogCore)?.isActive
            if (isActive != null && !isActive) {
                reference.compareAndSet(current, null)
                null
            } else {
                current
            }
        }
    }

    private fun tryAcquire(): SdkCore? {
        return synchronized(reference) {
            val current = reference.get()
            @Suppress("IfThenToElvis") // Less readable
            if (current != null) {
                current
            } else if (Datadog.isInitialized(sdkInstanceName)) {
                val sdkCore = Datadog.getInstance(sdkInstanceName)
                reference.set(sdkCore)
                onSdkInstanceCaptured(sdkCore)
                sdkCore
            } else {
                null
            }
        }
    }
}
