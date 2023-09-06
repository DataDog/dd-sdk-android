/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import com.datadog.android.api.InternalLogger

@Suppress("TooGenericExceptionCaught")
internal fun LayerDrawable.safeGetDrawable(index: Int, logger: InternalLogger = InternalLogger.UNBOUND): Drawable? {
    return if (index < 0 || index >= this.numberOfLayers) {
        logger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            { "Failed to get drawable from layer - invalid index passed: $index" }
        )
        null
    } else {
        this.getDrawable(index)
    }
}
