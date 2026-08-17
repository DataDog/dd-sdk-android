/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.view.View
import com.datadog.android.sessionreplay.internal.composition.CapturedWireframe
import com.datadog.android.sessionreplay.internal.composition.toCaptured
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver

/**
 * Emitted for views tagged `R.id.datadog_hidden`, in place of the normal mapper lookup, matching
 * legacy `HiddenViewMapper`. The traversal must not recurse into this view's children.
 */
internal class CapturedHiddenViewMapper(
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver
) : CapturedViewMapper<View> {

    override fun map(view: View, mappingContext: CapturedMappingContext): CapturedViewMapperResult {
        val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity)
        val identity = mappingContext.identityFactory.placeholderWireframe(mappingContext.ownerIdentity)
        return CapturedViewMapperResult.Wireframes(
            listOf(
                CapturedWireframe.PrivacyPlaceholder(
                    identity = identity,
                    bounds = bounds.toCaptured(),
                    label = HIDDEN_VIEW_PLACEHOLDER_TEXT
                )
            )
        )
    }

    companion object {
        internal const val HIDDEN_VIEW_PLACEHOLDER_TEXT = "Hidden"
    }
}
