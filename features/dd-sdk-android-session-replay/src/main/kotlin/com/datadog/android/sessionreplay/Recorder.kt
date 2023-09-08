/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface Recorder {
    fun registerCallbacks()

    fun unregisterCallbacks()

    fun stopProcessingRecords()

    fun resumeRecorders()

    fun stopRecorders()
}
