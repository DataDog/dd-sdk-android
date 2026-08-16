/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.model.MobileSegment

internal fun MobileSegment.Wireframe.copy(clip: MobileSegment.WireframeClip?): MobileSegment.Wireframe {
    return when (this) {
        is MobileSegment.Wireframe.ShapeWireframe -> this.copy(clip = clip)
        is MobileSegment.Wireframe.TextWireframe -> this.copy(clip = clip)
        is MobileSegment.Wireframe.ImageWireframe -> this.copy(clip = clip)
        is MobileSegment.Wireframe.PlaceholderWireframe -> this.copy(clip = clip)
        is MobileSegment.Wireframe.WebviewWireframe -> this.copy(clip = clip)
    }
}

internal fun MobileSegment.Source.Companion.tryFromSource(
    source: String,
    internalLogger: InternalLogger
): MobileSegment.Source {
    return try {
        fromJson(source)
    } catch (e: NoSuchElementException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { UNKNOWN_MOBILE_SEGMENT_SOURCE_WARNING_MESSAGE_FORMAT.format(java.util.Locale.US, source) },
            e
        )
        MobileSegment.Source.ANDROID
    }
}

internal const val UNKNOWN_MOBILE_SEGMENT_SOURCE_WARNING_MESSAGE_FORMAT = "You are using an unknown " +
    "source %s for MobileSegment.Source enum."
