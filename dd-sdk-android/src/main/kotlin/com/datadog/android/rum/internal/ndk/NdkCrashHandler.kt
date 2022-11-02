/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface NdkCrashHandler {
    fun prepareData()

    fun handleNdkCrash(
        sdkCore: SdkCore,
        rumWriter: DataWriter<Any>
    )
}
