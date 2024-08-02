/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.ndk.internal.NdkCrashReportsFeature

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
        val ndkCrashReportsFeature = NdkCrashReportsFeature(sdkCore as FeatureSdkCore)

        sdkCore.registerFeature(ndkCrashReportsFeature)
    }
}
