/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.data

import android.graphics.Bitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale

internal data class BitmapInfo(
    val bitmap: Bitmap,
    val isContextualImage: Boolean,
    val contentScale: ContentScale? = null,
    val alignment: Alignment? = null,
    /** Recorder-owned read-only pixels may be reused by later composition captures. */
    val isShared: Boolean = false,
    /** Already-resolved raw source pixels. The bitmap supplies dimensions only and must not be drawn. */
    val sourceResourceId: String? = null
) {
    @Suppress("UnsafeThirdPartyFunctionCall") // Only uncached recorder-owned pixels can be released by a caller.
    fun recycleIfOwned() {
        if (!isShared) bitmap.recycle()
    }
}
