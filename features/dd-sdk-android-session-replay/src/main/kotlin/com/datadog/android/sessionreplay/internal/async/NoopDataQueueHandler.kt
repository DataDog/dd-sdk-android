/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.model.MobileSegment

internal class NoopDataQueueHandler : DataQueueHandler {
    override fun addResourceItem(
        identifier: String,
        applicationId: String,
        resourceData: ByteArray
    ): ResourceRecordedDataQueueItem? = null

    override fun addTouchEventItem(
    pointerInteractions: List<MobileSegment.MobileRecord>
    ): TouchEventRecordedDataQueueItem? = null

    override fun addSnapshotItem(
    systemInformation: SystemInformation
    ): SnapshotRecordedDataQueueItem? = null

    override fun tryToConsumeItems() {
        // noop
    }

    override fun clearAndStopProcessingQueue() {
        // noop
    }
}
