/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * A screen with purely Canvas-drawn composables that carry no semantic annotations.
 *
 * Purpose: demonstrate the PixelCopy fallback in Session Replay. Without the PixelCopy
 * mechanism, SR sees nothing for these composables (no semantic node → no wireframe).
 * With it, SR produces a pixel-accurate ImageWireframe for each Canvas region.
 *
 * To verify in SR playback: navigate here with SR recording active and confirm the
 * activity rings and waveform appear in the session rather than blank space.
 */
@Composable
internal fun PixelCopySample() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(DefaultPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DefaultPadding)
    ) {
        Text(
            text = "PixelCopy SR Demo",
            style = MaterialTheme.typography.h6
        )
        Text(
            text = "The visuals below are drawn entirely with Canvas — no semantic annotations. " +
                "Session Replay should capture them via the PixelCopy fallback.",
            style = MaterialTheme.typography.body2
        )

        Spacer(modifier = Modifier.height(DefaultPadding))

        Text(
            text = "Activity rings (custom arc drawing)",
            style = MaterialTheme.typography.caption
        )

        // Three concentric arcs with no semantic content — a canonical dark-spot composable.
        ActivityRings(
            modifier = Modifier.size(200.dp),
            ringData = listOf(
                RingData(sweepAngle = 270f, color = Color(0xFFE53935)), // red, 75%
                RingData(sweepAngle = 216f, color = Color(0xFF43A047)), // green, 60%
                RingData(sweepAngle = 144f, color = Color(0xFF1E88E5))  // blue, 40%
            )
        )

        Spacer(modifier = Modifier.height(DefaultPadding))

        Text(
            text = "Sine wave (custom path drawing)",
            style = MaterialTheme.typography.caption
        )

        // Sine wave — also pure Canvas, no semantics.
        SineWave(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )
    }
}

private data class RingData(val sweepAngle: Float, val color: Color)

/**
 * Draws concentric activity-style arcs. Each ring occupies a progressively smaller radius
 * to produce a clear, visually distinct pattern. No Semantics block — intentionally invisible
 * to the SR semantic mapper, making it a reliable PixelCopy test target.
 */
@Composable
private fun ActivityRings(
    modifier: Modifier = Modifier,
    ringData: List<RingData>
) {
    val strokeWidth = 28.dp
    val ringGap = 8.dp

    // semantics {} makes this node visible in Compose's unmerged semantics tree so that
    // RootSemanticsNodeMapper visits it. Without this, Canvas composables are completely
    // invisible to the traversal — they produce no semantic node — and the PixelCopy
    // dark-spot detection never fires.
    Canvas(modifier = modifier.semantics { contentDescription = "Activity rings chart" }) {
        val strokePx = strokeWidth.toPx()
        val gapPx = ringGap.toPx()
        val startAngle = -90f // 12 o'clock

        ringData.forEachIndexed { index, ring ->
            val inset = (strokePx + gapPx) * index + strokePx / 2f
            val arcSize = Size(
                width = size.width - inset * 2f,
                height = size.height - inset * 2f
            )
            // Dim track
            drawArc(
                color = ring.color.copy(alpha = 0.2f),
                startAngle = startAngle,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            // Filled arc
            drawArc(
                color = ring.color,
                startAngle = startAngle,
                sweepAngle = ring.sweepAngle,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Draws a multi-colour sine wave. Again, pure Canvas — no semantics.
 */
@Composable
private fun SineWave(modifier: Modifier = Modifier) {
    val waveColors = listOf(
        Color(0xFFE53935),
        Color(0xFF43A047),
        Color(0xFF1E88E5)
    )

    Canvas(modifier = modifier.semantics { contentDescription = "Sine wave chart" }) {
        val midY = size.height / 2f
        val amplitude = size.height / 3f
        val strokePx = 4.dp.toPx()
        val pointCount = 512

        waveColors.forEachIndexed { waveIndex, color ->
            val phaseShift = waveIndex * (2.0 * PI / waveColors.size)
            var prevX = 0f
            var prevY = midY

            for (i in 1..pointCount) {
                val x = i * size.width / pointCount
                val angle = (i.toDouble() / pointCount) * 4.0 * PI + phaseShift
                val y = (midY - amplitude * sin(angle)).toFloat()
                drawLine(
                    color = color,
                    start = Offset(prevX, prevY),
                    end = Offset(x, y),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
                prevX = x
                prevY = y
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewPixelCopySample() {
    PixelCopySample()
}
