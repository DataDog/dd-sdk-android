/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.sessionReplayImagePrivacy
import com.datadog.android.sessionreplay.compose.sessionReplayTextAndInputPrivacy
import android.graphics.Paint as NativePaint

/**
 * Compose counterpart to the native `PrivacyMatrixFragment` (legacy View sample flow) — same 12
 * `TextAndInputPrivacy` × `ImagePrivacy` permutations, same expected-outcome labels, but drawn as
 * pure Canvas composables with no semantic content (the same "dark spot" pattern as
 * [PixelCaptureSample] — see its doc for why that's what makes PixelCapture's fallback the path
 * exercised here) rather than real `Text`/`Image` composables like
 * [FineGrainedMaskingSample] uses. That distinction matters: [FineGrainedMaskingSample] verifies
 * privacy overrides on the *semantic* mapper chain, a completely different code path from the
 * pixel-capture-specific heuristics ([com.datadog.android.sessionreplay.internal.recorder.InputFieldDetector],
 * [com.datadog.android.sessionreplay.internal.recorder.ImageContentDetector]) this screen is
 * actually meant to exercise.
 *
 * `Modifier.sessionReplayTextAndInputPrivacy`/`sessionReplayImagePrivacy` apply directly to each
 * Canvas composable's own modifier chain, overriding just that one instance independently of the
 * app's actual [com.datadog.android.sessionreplay.SessionReplayConfiguration].
 */
@Composable
internal fun PrivacyMatrixSample() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(DefaultPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(DefaultPadding)
    ) {
        Text(text = "TEXT MASKING — TextAndInputPrivacy", style = MaterialTheme.typography.h6)

        TextRow(
            privacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            styledAsInputField = false,
            expectation = "Expected: visible, unmasked (not an input)"
        )
        TextRow(
            privacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            styledAsInputField = true,
            expectation = "Expected: masked (black box over the text)"
        )
        TextRow(
            privacy = TextAndInputPrivacy.MASK_ALL_INPUTS,
            styledAsInputField = false,
            expectation = "Expected: visible, unmasked (pixel capture can't tell a sensitive " +
                "input apart from any other, so this behaves the same as MASK_SENSITIVE_INPUTS here)"
        )
        TextRow(
            privacy = TextAndInputPrivacy.MASK_ALL_INPUTS,
            styledAsInputField = true,
            expectation = "Expected: masked (black box over the text)"
        )
        TextRow(
            privacy = TextAndInputPrivacy.MASK_ALL,
            styledAsInputField = false,
            expectation = "Expected: masked (this level masks ALL text, not just inputs)"
        )
        TextRow(
            privacy = TextAndInputPrivacy.MASK_ALL,
            styledAsInputField = true,
            expectation = "Expected: masked (black box over the text)"
        )

        Text(text = "IMAGE MASKING — ImagePrivacy", style = MaterialTheme.typography.h6)

        ImageRow(
            privacy = ImagePrivacy.MASK_NONE,
            includeText = false,
            expectation = "Expected: visible, unmasked"
        )
        ImageRow(
            privacy = ImagePrivacy.MASK_NONE,
            includeText = true,
            expectation = "Expected: visible, unmasked (image and caption both)"
        )
        ImageRow(
            privacy = ImagePrivacy.MASK_LARGE_ONLY,
            includeText = false,
            expectation = "Expected: \"Content Image\"-labeled placeholder (this composable is " +
                "fillMaxWidth, so it always exceeds the 100dp size threshold — a pure size " +
                "check, made before any capture is attempted, so content never matters here)"
        )
        ImageRow(
            privacy = ImagePrivacy.MASK_LARGE_ONLY,
            includeText = true,
            expectation = "Expected: \"Content Image\"-labeled placeholder — identical outcome " +
                "to the no-text case above, since this gate never looks at content"
        )
        ImageRow(
            privacy = ImagePrivacy.MASK_ALL,
            includeText = false,
            expectation = "Expected: \"Image\"-labeled placeholder (no OCR'd text at all, so " +
                "this is conservatively treated as image content)"
        )
        ImageRow(
            privacy = ImagePrivacy.MASK_ALL,
            includeText = true,
            expectation = "Expected: \"Image\"-labeled placeholder — the caption alone doesn't " +
                "exempt it, since the icon itself is still non-text ink"
        )
    }
}

@Composable
private fun TextRow(privacy: TextAndInputPrivacy, styledAsInputField: Boolean, expectation: String) {
    val title = if (styledAsInputField) "input-styled text" else "plain text"
    Column {
        Text(text = "$privacy — $title", style = MaterialTheme.typography.subtitle2)
        Text(text = expectation, style = MaterialTheme.typography.caption)
        PrivacyMatrixTextCanvas(
            styledAsInputField = styledAsInputField,
            modifier = Modifier
                .fillMaxWidth()
                .height(TextRowHeight)
                .sessionReplayTextAndInputPrivacy(textAndInputPrivacy = privacy)
        )
    }
}

@Composable
private fun ImageRow(privacy: ImagePrivacy, includeText: Boolean, expectation: String) {
    val title = if (includeText) "image with a text caption" else "image only, no text"
    Column {
        Text(text = "$privacy — $title", style = MaterialTheme.typography.subtitle2)
        Text(text = expectation, style = MaterialTheme.typography.caption)
        PrivacyMatrixImageCanvas(
            includeText = includeText,
            modifier = Modifier
                .fillMaxWidth()
                .height(ImageRowHeight)
                .sessionReplayImagePrivacy(imagePrivacy = privacy)
        )
    }
}

/**
 * Pure Canvas drawing, no semantic text content — mirrors `PrivacyMatrixTextView`'s native
 * drawing so the same [com.datadog.android.sessionreplay.internal.recorder.InputFieldDetector]
 * heuristic recognizes the input-styled variant identically in both.
 */
@Composable
private fun PrivacyMatrixTextCanvas(styledAsInputField: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.semantics { contentDescription = "Privacy matrix text sample" }) {
        drawRect(color = Color.White, size = size)

        val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
            style = NativePaint.Style.FILL
            color = android.graphics.Color.BLACK
            textAlign = NativePaint.Align.LEFT
            textSize = size.height * TEXT_HEIGHT_FRACTION
        }

        if (styledAsInputField) {
            val textPadding = paint.textSize * TEXT_PADDING_FRACTION_OF_TEXT_SIZE
            val boxLeft = size.width * MARGIN_FRACTION
            val boxRight = size.width * (1f - MARGIN_FRACTION)
            val textBaseline = size.height * TEXT_BASELINE_FRACTION
            drawContext.canvas.nativeCanvas.drawText("Cardholder name", boxLeft + textPadding, textBaseline, paint)

            val boxTop = textBaseline - paint.textSize * ASCENT_FRACTION_OF_TEXT_SIZE - textPadding
            val boxBottom = textBaseline + textPadding
            drawRoundRect(
                color = Color.DarkGray,
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxRight - boxLeft, boxBottom - boxTop),
                cornerRadius = CornerRadius(CORNER_RADIUS_PX, CORNER_RADIUS_PX),
                style = Stroke(width = STROKE_WIDTH_PX)
            )
        } else {
            drawContext.canvas.nativeCanvas.drawText(
                "Plain informational text",
                size.width * MARGIN_FRACTION,
                size.height * TEXT_BASELINE_FRACTION,
                paint
            )
        }
    }
}

/**
 * Pure Canvas drawing, no semantic image content — mirrors `PrivacyMatrixImageView`'s native
 * drawing so the same [com.datadog.android.sessionreplay.internal.recorder.ImageContentDetector]
 * heuristic sees an identical bitmap shape in both.
 */
@Composable
private fun PrivacyMatrixImageCanvas(includeText: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.semantics { contentDescription = "Privacy matrix image sample" }) {
        drawRect(color = Color.White, size = size)

        val cx = size.width / 2f
        val cy = size.height * if (includeText) ICON_CENTER_Y_WITH_TEXT_FRACTION else ICON_CENTER_Y_NO_TEXT_FRACTION
        val radius = minOf(size.width, size.height) * ICON_RADIUS_FRACTION

        drawCircle(color = ICON_COLOR, radius = radius, center = Offset(cx, cy))
        drawCircle(color = Color.White, radius = radius * ICON_INNER_RADIUS_FRACTION, center = Offset(cx, cy))

        if (includeText) {
            val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                textAlign = NativePaint.Align.CENTER
                textSize = size.height * CAPTION_TEXT_HEIGHT_FRACTION
            }
            drawContext.canvas.nativeCanvas.drawText(
                "Profile photo",
                cx,
                size.height * CAPTION_BASELINE_FRACTION,
                paint
            )
        }
    }
}

private val TextRowHeight = 100.dp
private val ImageRowHeight = 150.dp

private const val MARGIN_FRACTION = 0.08f
private const val TEXT_HEIGHT_FRACTION = 0.3f
private const val TEXT_BASELINE_FRACTION = 0.55f
private const val TEXT_PADDING_FRACTION_OF_TEXT_SIZE = 0.5f
private const val ASCENT_FRACTION_OF_TEXT_SIZE = 0.8f
private const val STROKE_WIDTH_PX = 4f
private const val CORNER_RADIUS_PX = 12f

private val ICON_COLOR = Color(0xFF1E88E5)
private const val ICON_RADIUS_FRACTION = 0.28f
private const val ICON_INNER_RADIUS_FRACTION = 0.4f
private const val ICON_CENTER_Y_NO_TEXT_FRACTION = 0.5f
private const val ICON_CENTER_Y_WITH_TEXT_FRACTION = 0.4f
private const val CAPTION_TEXT_HEIGHT_FRACTION = 0.12f
private const val CAPTION_BASELINE_FRACTION = 0.9f

@Preview(showBackground = true)
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewPrivacyMatrixSample() {
    PrivacyMatrixSample()
}
