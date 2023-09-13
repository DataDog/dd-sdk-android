/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.internal.AsyncImageProcessingCallback
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.model.MobileSegment

internal class QueueableViewMapper(
    private val mapper: WireframeMapper<View, *>,
    private val recordedDataQueueRefs: RecordedDataQueueRefs
) : BaseWireframeMapper<View, MobileSegment.Wireframe>(), AsyncImageProcessingCallback {
    override fun map(view: View, mappingContext: MappingContext):
        List<MobileSegment.Wireframe> {
        (mapper as? BaseWireframeMapper<View, *>)?.registerAsyncImageProcessingCallback(this)
        return mapper.map(view, mappingContext)
    }

    override fun startProcessingImage() = recordedDataQueueRefs.incrementPendingImages()

    override fun finishProcessingImage() {
        recordedDataQueueRefs.decrementPendingImages()
        recordedDataQueueRefs.tryToConsumeItem()
    }
}
