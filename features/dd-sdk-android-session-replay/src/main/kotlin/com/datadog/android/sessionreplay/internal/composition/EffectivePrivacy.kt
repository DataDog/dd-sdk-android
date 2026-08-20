/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TextAndInputPrivacy

/** The privacy levels in effect for one node, after applying any per-view tag override. */
internal data class EffectivePrivacy(
    val imagePrivacy: ImagePrivacy,
    val textAndInputPrivacy: TextAndInputPrivacy
)

/**
 * Resolves [view]'s own effective privacy, starting from [inherited] (its parent's effective
 * privacy) and applying a per-view override tagged via `setSessionReplayImagePrivacy`/
 * `setSessionReplayTextAndInputPrivacy` if present. An invalid or unrecognized tag value falls
 * back to [inherited] rather than failing the whole traversal.
 */
internal fun resolveEffectivePrivacy(
    view: View,
    inherited: EffectivePrivacy,
    internalLogger: InternalLogger
): EffectivePrivacy {
    val imagePrivacy = resolveOverride(
        tag = view.getTag(R.id.datadog_image_privacy),
        inherited = inherited.imagePrivacy,
        valueOf = ImagePrivacy::valueOf,
        internalLogger = internalLogger
    )
    val textAndInputPrivacy = resolveOverride(
        tag = view.getTag(R.id.datadog_text_and_input_privacy),
        inherited = inherited.textAndInputPrivacy,
        valueOf = TextAndInputPrivacy::valueOf,
        internalLogger = internalLogger
    )
    return if (imagePrivacy == inherited.imagePrivacy && textAndInputPrivacy == inherited.textAndInputPrivacy) {
        inherited
    } else {
        EffectivePrivacy(imagePrivacy, textAndInputPrivacy)
    }
}

private fun <T> resolveOverride(
    tag: Any?,
    inherited: T,
    valueOf: (String) -> T,
    internalLogger: InternalLogger
): T {
    val tagValue = tag as? String ?: return inherited
    return try {
        valueOf(tagValue)
    } catch (e: IllegalArgumentException) {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { "Invalid privacy override tag value: $tagValue" },
            e
        )
        inherited
    }
}
