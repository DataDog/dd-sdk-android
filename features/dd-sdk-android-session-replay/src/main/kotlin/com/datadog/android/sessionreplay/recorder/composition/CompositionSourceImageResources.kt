/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.composition

import android.graphics.Bitmap
import com.datadog.android.lint.InternalApi

/**
 * Pipeline-scoped reuse of already-resolved, unmodified source-image resources. This stores no pixels.
 * The caller must evaluate current image privacy before lookup and use the current image geometry.
 * Only immutable sources copied directly to ARGB_8888 qualify; tinted, drawn or cropped output does not.
 */
interface CompositionSourceImageResources {
    /** Returns an existing resource for the current source state, or prepares bookkeeping for a copy. */
    @InternalApi
    fun findResourceId(source: Bitmap): String?

    /**
     * Associates a direct source copy with the immediately preceding lookup, before it is submitted as
     * a source-image pending capture. Changed source metadata invalidates the association. A resource
     * becomes reusable only after normal pixel processing successfully resolves this exact copy.
     */
    @InternalApi
    fun registerSourceCopy(source: Bitmap, copy: Bitmap)
}
