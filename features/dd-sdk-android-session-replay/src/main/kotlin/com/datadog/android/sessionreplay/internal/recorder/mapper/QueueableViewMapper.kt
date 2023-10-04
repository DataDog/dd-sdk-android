/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.internal.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.model.MobileSegment

internal class QueueableViewMapper(
    private val mapper: WireframeMapper<View, *>,
    private val recordedDataQueueRefs: RecordedDataQueueRefs
) : AsyncJobStatusCallback {
    fun map(view: View, mappingContext: MappingContext):
        List<MobileSegment.Wireframe> {
        return mapper.map(view, mappingContext, this)
    }

    override fun jobStarted() {
        recordedDataQueueRefs.incrementPendingJobs()
    }

    override fun jobFinished() {
        recordedDataQueueRefs.decrementPendingJobs()
        recordedDataQueueRefs.tryToConsumeItem()
    }
}
