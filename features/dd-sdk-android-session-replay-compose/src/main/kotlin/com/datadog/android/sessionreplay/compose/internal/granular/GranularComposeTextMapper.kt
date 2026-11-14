/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import androidx.compose.ui.text.font.GenericFontFamily
import androidx.compose.ui.text.style.TextAlign
import com.datadog.android.internal.sessionreplay.composition.CapturedAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedHorizontalAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedTextPosition
import com.datadog.android.internal.sessionreplay.composition.CapturedTextStyle
import com.datadog.android.internal.sessionreplay.composition.CapturedTruncationMode
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.compose.internal.utils.transformCapturedText
import com.datadog.android.sessionreplay.model.MobileSegment

/** Applies the effective text policy before text enters a captured snapshot. */
internal class GranularComposeTextMapper(private val convertColor: (Long) -> String?) {
    fun map(
        identity: CapturedIdentity,
        bounds: CapturedBounds,
        layout: TextLayoutInfo,
        privacy: TextAndInputPrivacy,
        isInput: Boolean
    ): CapturedWireframe.Text = CapturedWireframe.Text(
        identity = identity,
        bounds = bounds,
        text = transformCapturedText(layout.text, privacy, isInput),
        textStyle = CapturedTextStyle(
            family = (layout.fontFamily as? GenericFontFamily)?.name ?: DEFAULT_FONT_FAMILY,
            size = layout.fontSize,
            color = convertColor(layout.color.toLong()) ?: DEFAULT_TEXT_COLOR,
            truncationMode = when (layout.textOverflow) {
                MobileSegment.TruncationMode.CLIP -> CapturedTruncationMode.CLIP
                MobileSegment.TruncationMode.HEAD -> CapturedTruncationMode.HEAD
                MobileSegment.TruncationMode.TAIL -> CapturedTruncationMode.TAIL
                MobileSegment.TruncationMode.MIDDLE -> CapturedTruncationMode.MIDDLE
                null -> null
            }
        ),
        textPosition = CapturedTextPosition(
            alignment = CapturedAlignment(
                horizontal = when (layout.textAlign) {
                    TextAlign.End, TextAlign.Right -> CapturedHorizontalAlignment.RIGHT
                    TextAlign.Center, TextAlign.Justify -> CapturedHorizontalAlignment.CENTER
                    else -> CapturedHorizontalAlignment.LEFT
                }
            )
        )
    )

    private companion object {
        const val DEFAULT_FONT_FAMILY = "roboto, sans-serif"
        const val DEFAULT_TEXT_COLOR = "#000000FF"
    }
}
