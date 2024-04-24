/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.app.Application
import com.datadog.android.sessionreplay.Recorder
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.storage.ResourcesWriter

internal fun interface RecorderProvider {
    fun provideSessionReplayRecorder(
        resourceWriter: ResourcesWriter,
        recordWriter: RecordWriter,
        application: Application
    ): Recorder
}
