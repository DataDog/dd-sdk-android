/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImagePainter

/**
 * Holds every direct reference to Coil 3 types, which are compile-only. Create it only after
 * checking that Coil 3 is on the classpath, so apps without Coil 3 never load this class.
 */
internal class Coil3PainterResolver {

    /**
     * Returns the painter of the current [AsyncImagePainter] state. While a crossfade runs, this
     * is the loaded image rather than the crossfade painter that [AsyncImagePainter] draws.
     */
    fun resolveStatePainter(asyncImagePainter: Any?): Painter? {
        return (asyncImagePainter as? AsyncImagePainter)?.state?.value?.painter
    }
}
