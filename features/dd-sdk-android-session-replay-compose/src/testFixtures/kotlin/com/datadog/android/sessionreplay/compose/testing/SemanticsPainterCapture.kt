/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.testing

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.utils.createNoOpInternalLogger

class SemanticsPainterCapture {
    private val semanticsUtils = SemanticsUtils()
    private val logger = createNoOpInternalLogger()

    fun resolve(node: SemanticsNode): ResolvedPainterBitmap? =
        semanticsUtils.resolveSemanticsPainter(node, logger)?.let {
            ResolvedPainterBitmap(it.bitmap, it.isContextualImage)
        }
}

class ResolvedPainterBitmap(val bitmap: Bitmap, val isContextualImage: Boolean)
