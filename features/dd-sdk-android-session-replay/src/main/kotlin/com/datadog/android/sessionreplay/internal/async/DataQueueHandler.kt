/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.SystemInformation

internal interface DataQueueHandler {
    fun addResourceItem(
        identifier: String,
        applicationId: String,
        resourceData: ByteArray
    ): ResourceRecordedDataQueueItem?
    fun addTouchEventItem(
        pointerInteractions: List<MobileSegment.MobileRecord>
    ): TouchEventRecordedDataQueueItem?
    fun addSnapshotItem(systemInformation: SystemInformation): SnapshotRecordedDataQueueItem?
    fun tryToConsumeItems()
    fun clearAndStopProcessingQueue()
}
