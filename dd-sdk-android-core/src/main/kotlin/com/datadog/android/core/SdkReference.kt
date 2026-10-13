/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.core.internal.DatadogCore

/**
 * Class establishing the reference to the particular SDK instance by using its name.
 *
 * Calling [get] starts observing the named SDK instance. If it is already initialized,
 * [onSdkInstanceCaptured] is called immediately; otherwise it is called when the instance is registered.
 * The reference also observes replacement instances after the SDK is stopped and initialized again.
 *
 * Once SDK instance with given name becomes inactive (it is stopped), reference will be
 * automatically cleaned up.
 *
 * @param sdkInstanceName Name of the SDK instance to capture. If no name is provided, default
 * SDK instance will be checked.
 * @param onSdkInstanceCaptured Callback which will be fired once SDK instance is acquired.
 */
class SdkReference
@JvmOverloads
constructor(
    private val sdkInstanceName: String? = null,
    private val onSdkInstanceCaptured: (SdkCore) -> Unit = {}
) {

    @Volatile
    private var reference: CapturedSdkCore? = null

    /**
     * Returns SDK instance if it is acquired, null otherwise.
     */
    fun get(): SdkCore? {
        val current = reference
        return if (current == null) {
            Datadog.registry.registerReference(sdkInstanceName, this)
            reference?.sdkCore
        } else {
            val isActive = (current.sdkCore as? DatadogCore)?.isActive
            if (isActive != null && !isActive) {
                synchronized(Datadog.registry) {
                    if (reference === current) reference = null
                }
                null
            } else {
                current.sdkCore
            }
        }
    }

    // Bind under the registry lock; invoke the returned notification after releasing it.
    internal fun capture(sdkCore: SdkCore): (() -> Unit)? {
        if (reference?.sdkCore === sdkCore) return null
        val captured = CapturedSdkCore(sdkCore)
        reference = captured
        return {
            // Skip notifications invalidated before delivery by shutdown or replacement.
            if (reference === captured) onSdkInstanceCaptured(sdkCore)
        }
    }

    internal fun release() {
        reference = null
    }

    private class CapturedSdkCore(val sdkCore: SdkCore)
}
