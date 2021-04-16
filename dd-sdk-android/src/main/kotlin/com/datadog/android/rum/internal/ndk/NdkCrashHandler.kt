/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.log.model.LogEvent
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface NdkCrashHandler {
    fun prepareData()

    fun handleNdkCrash(
        logWriter: DataWriter<LogEvent>,
        rumWriter: DataWriter<RumEvent>
    )
}
