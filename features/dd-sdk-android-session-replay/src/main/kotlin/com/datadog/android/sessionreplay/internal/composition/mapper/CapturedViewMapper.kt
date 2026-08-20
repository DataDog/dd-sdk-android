/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.view.View
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.composition.CapturedIdentityFactory
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCaptureSink

/**
 * Deliberately narrower than the legacy `MappingContext`: no image-helper fields, since
 * image-resource capture is owned by a later workstream, not by View decomposition. [imagePrivacy]
 * and [textAndInputPrivacy] carry the effective (already per-view-override-resolved) privacy level
 * so a mapper can apply masking itself where relevant (text, pixel-fallback eligibility).
 */
internal data class CapturedMappingContext(
    val identityFactory: CapturedIdentityFactory,
    val ownerIdentity: CapturedIdentity,
    val screenDensity: Float,
    val imagePrivacy: ImagePrivacy,
    val textAndInputPrivacy: TextAndInputPrivacy,
    val pendingPixelCaptureSink: PendingPixelCaptureSink = PendingPixelCaptureSink.NoOp
)

internal sealed interface CapturedViewMapperResult {
    data class Wireframes(val wireframes: List<CapturedWireframe>) : CapturedViewMapperResult
    object None : CapturedViewMapperResult
}

internal fun interface CapturedViewMapper<T : View> {
    fun map(view: T, mappingContext: CapturedMappingContext): CapturedViewMapperResult
}
