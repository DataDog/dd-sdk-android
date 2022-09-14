/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.sessionreplay.SerializedRecordWriter

internal class SessionReplaySerializedRecordWriter(private val dataWriter: DataWriter<String>) :
    SerializedRecordWriter {
    // This method is being called from the `SnapshotProcessor` in Session Replay module which
    // runs on a WorkerThread already.
    @Suppress("ThreadSafety")
    override fun write(serializedRecord: String) {
        dataWriter.write(serializedRecord)
    }
}
