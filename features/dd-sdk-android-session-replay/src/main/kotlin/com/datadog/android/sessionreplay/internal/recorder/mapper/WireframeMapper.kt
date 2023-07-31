/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.model.MobileSegment

/**
 * Maps a View to a [List] of [MobileSegment.Wireframe].
 * This is mainly used internally by the SDK but if you want to provide a different
 * Session Replay representation for a specific View type you can implement this on your end.
 */
interface WireframeMapper<in T : View, out S : MobileSegment.Wireframe> {

    /**
     * Maps a [View] to a [List<Wireframe>] in order to be rendered in the Session Replay player.
     * @param view as the [View] instance that will be mapped
     * @param mappingContext in which we provide useful information regarding the current
     * system state.
     * @see MobileSegment.Wireframe
     * @see SystemInformation
     */
    fun map(view: T, mappingContext: MappingContext): List<S>
}
