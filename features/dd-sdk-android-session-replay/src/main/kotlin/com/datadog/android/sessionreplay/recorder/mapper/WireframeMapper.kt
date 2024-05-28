/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback

/**
 * Maps a View to a [List] of [MobileSegment.Wireframe].
 * This is mainly used internally by the SDK but if you want to provide a different
 * Session Replay representation for a specific View type you can implement this on your end.
 */
interface WireframeMapper<in T : View> {

    /**
     * Maps a [View] to a [List<Wireframe>] in order to be rendered in the Session Replay player.
     * @param view as the [View] instance that will be mapped
     * @param mappingContext in which we provide useful information regarding the current
     * @param asyncJobStatusCallback a callback that can be called when the mapper starts or
     * finishes processing an async job. By calling this whenever a job started
     * (in the caller thread) and finished (in the background thread)
     * will make sure that the `List<Wireframe>` will not be consumed until all the wireframes
     * are updated by the async jobs. It can be used to
     * offload heavy work from the calling thread (main) to a background thread while mapping
     * some view properties.
     * @param internalLogger the logger to log internal warnings
     * @see MobileSegment.Wireframe
     * @see SystemInformation
     */
    fun map(
        view: T,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe>
}
