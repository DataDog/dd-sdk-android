/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import com.datadog.android.sessionreplay.internal.processor.RumContextData
import com.datadog.android.sessionreplay.model.MobileSegment

internal class TouchEventRecordedDataQueueItem(
    rumContextData: RumContextData,
    internal val touchData: List<MobileSegment.MobileRecord> = emptyList()
) : RecordedDataQueueItem(rumContextData) {

    override fun isValid(): Boolean {
        return touchData.isNotEmpty()
    }

    override fun isReady(): Boolean {
        return true
    }
}
