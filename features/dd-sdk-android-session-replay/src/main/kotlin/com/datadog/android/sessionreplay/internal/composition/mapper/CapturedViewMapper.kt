/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.view.View
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
    val screenDensity: Float
)

internal sealed interface CapturedViewMapperResult {
    data class Wireframes(val wireframes: List<CapturedWireframe>) : CapturedViewMapperResult
    object None : CapturedViewMapperResult
}

internal fun interface CapturedViewMapper<T : View> {
    fun map(view: T, mappingContext: CapturedMappingContext): CapturedViewMapperResult
}
