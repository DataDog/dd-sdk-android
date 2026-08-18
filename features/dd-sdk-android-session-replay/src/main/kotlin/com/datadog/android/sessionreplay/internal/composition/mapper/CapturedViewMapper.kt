/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.view.View
import androidx.annotation.UiThread
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.sessionreplay.internal.composition.CapturedIdentityFactory

/**
 * Deliberately narrower than the legacy `MappingContext`: no privacy/image-helper fields, since
 * masking and image-resource capture are owned by a later workstream, not by View decomposition.
 */
internal data class CapturedMappingContext(
    val identityFactory: CapturedIdentityFactory,
    val ownerIdentity: CapturedIdentity,
    val screenDensity: Float,
    // The native copier supplies geometry once for both the layer and its mapper.
    val bounds: CapturedBounds? = null
)

internal sealed interface CapturedViewMapperResult {
    /**
     * [isTerminal] is true when the result represents the whole native subtree (for example a
     * WebView slot). Traversal must not also capture that view's native children independently.
     */
    data class Wireframes(
        val wireframes: List<CapturedWireframe>,
        val isTerminal: Boolean = false
    ) : CapturedViewMapperResult
    object None : CapturedViewMapperResult
}

internal fun interface CapturedViewMapper<T : View> {
    fun map(view: T, mappingContext: CapturedMappingContext): CapturedViewMapperResult

    /** Runs on the UI thread. Returned values must not retain Views, Layouts or Drawables. */
    @UiThread
    fun capture(view: T, mappingContext: CapturedMappingContext): CapturedViewData =
        CapturedViewData.Mapped(map(view, mappingContext))
}

/** Builds wireframes exclusively from copied values; safe to invoke on the processing executor. */
internal sealed interface CapturedViewData {
    val isTerminal: Boolean get() = false

    /** Only built-in mappings with visual output confined to the copied view rectangle opt in. */
    val isOcclusionBounded: Boolean get() = false
    val hasOpaqueBackground: Boolean get() = false

    fun map(mappingContext: CapturedMappingContext): CapturedViewMapperResult

    data class Mapped(val result: CapturedViewMapperResult) : CapturedViewData {
        override val isTerminal: Boolean get() = (result as? CapturedViewMapperResult.Wireframes)?.isTerminal == true
        override fun map(mappingContext: CapturedMappingContext): CapturedViewMapperResult = result
    }
}
