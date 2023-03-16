/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk

import com.datadog.android.v2.api.SdkCore
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface NdkCrashHandler {
    fun prepareData()

    fun handleNdkCrash(sdkCore: SdkCore, reportTarget: ReportTarget)

    enum class ReportTarget {
        RUM,
        LOGS
    }
}
