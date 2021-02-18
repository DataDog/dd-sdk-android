/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface NdkCrashHandler {
    fun prepareData()

    fun handleNdkCrash(
        asyncLogWriter: Writer<Log>,
        asyncRumWriter: Writer<RumEvent>
    )
}
