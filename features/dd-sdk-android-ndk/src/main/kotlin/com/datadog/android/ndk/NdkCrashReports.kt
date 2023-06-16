/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk

import com.datadog.android.Datadog
import com.datadog.android.ndk.internal.NdkCrashReportsFeature
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.SdkCore

/**
 * An entry point to Datadog NDK Crash Reports feature.
 */
object NdkCrashReports {

    /**
     * Enables a NDK Crash Reports feature.
     *
     * @param sdkCore SDK instance to register feature in. If not provided, default SDK instance
     * will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun enable(sdkCore: SdkCore = Datadog.getInstance()) {
        val ndkCrashReportsFeature = NdkCrashReportsFeature()

        (sdkCore as FeatureSdkCore).registerFeature(ndkCrashReportsFeature)
    }
}
