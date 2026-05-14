/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.tests.utils

import com.datadog.android.api.SdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumMonitor

/**
 * Removes [sdkCore] from [GlobalRumMonitor]'s static registry.
 *
 * Required because `Datadog.stopInstance(name)` only triggers `RumFeature.onStop` (and
 * therefore `GlobalRumMonitor.unregister`) for real `DatadogCore` instances; with a
 * `StubSDKCore` the cast fails silently and the monitor entry leaks for the rest of the
 * JVM.
 *
 * Implemented by mutating the private `registeredMonitors` map directly via reflection.
 * `GlobalRumMonitor.unregister` is `internal` and its JVM signature is therefore mangled
 * with the SDK module name; reaching for the underlying field is the more stable contract.
 */
internal fun unregisterGlobalRumMonitor(sdkCore: SdkCore) {
    val field = GlobalRumMonitor::class.java.getDeclaredField("registeredMonitors")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val monitors = field.get(GlobalRumMonitor) as MutableMap<SdkCore, RumMonitor>
    synchronized(monitors) {
        monitors.remove(sdkCore)
    }
}
