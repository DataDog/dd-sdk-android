/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.composition

import android.graphics.Bitmap
import com.datadog.android.lint.InternalApi

/** Identity of one immutable glyph-cache entry; a new rendering recipe requires a new key. */
@InternalApi
class CompositionTextResourceKey

/**
 * Pipeline-scoped reuse of already-resolved glyph resources, without retaining their bitmaps.
 * The caller must validate current measured text, immutable paint, privacy and placement before lookup.
 * Keys must never be shared between different rendering recipes or mutable shader output.
 */
interface CompositionTextResources {
    /** Returns a resource only while this pipeline's authoritative raw-content cache still contains it. */
    @InternalApi
    fun findResourceId(key: CompositionTextResourceKey): String?

    /**
     * Associates a fresh glyph bitmap with its immutable cache entry before submitting text-only pixels.
     * The bitmap must not be changed while processing it. Only successful ordinary pixel resolution
     * can make the resource reusable; registration alone does not publish a resource.
     */
    @InternalApi
    fun registerCapture(key: CompositionTextResourceKey, bitmap: Bitmap)
}
