/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.sdk.integration.sessionreplay.composition

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.granular.GranularComposeCompatibilityGate
import com.datadog.android.sessionreplay.compose.internal.granular.GranularComposeDecomposer
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCapture
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCaptureSink
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeRequest
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class ComposeHomeCaptureRegressionTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before fun keepAwake() {
        composeRule.runOnIdle {
            composeRule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @Test fun preserveVectorTransparencyTintAndPainterAlpha() {
        val vector = ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); close()
            }
        }.build()
        composeRule.setContent {
            val painter = rememberVectorPainter(vector)
            Row {
                for ((color, alpha) in listOf(Color.Black to 1f, Color.Red to 1f, Color.Blue to 0.5f)) {
                    Image(
                        painter,
                        "tinted-icon",
                        Modifier.size(24.dp),
                        alpha = alpha,
                        colorFilter = ColorFilter.tint(color)
                    )
                }
            }
        }
        composeRule.runOnIdle {
            val (_, captures) = capture()
            try {
                val images = captures.filter { it.isSourceImage }.map { it.bitmap }
                assertThat(images).hasSize(3)
                images.forEach {
                    assertThat(it.config).isEqualTo(Bitmap.Config.ARGB_8888)
                    assertThat(it.getPixel(0, 0)).isZero()
                }
                assertThat(images.map { it.getPixel(it.width / 2, it.height / 2) })
                    .containsExactly(0xFF000000.toInt(), 0xFFFF0000.toInt(), 0x800000FF.toInt())
            } finally { captures.forEach { it.bitmap.recycle() } }
        }
    }

    @Test fun preserveBitmapTintAlphaAndDensityIndependentPixels() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            density = 160
            Canvas(this).drawRect(16f, 16f, 48f, 48f, Paint().apply { color = android.graphics.Color.RED })
        }
        composeRule.setContent {
            Image(
                source.asImageBitmap(),
                "bitmap-icon",
                Modifier.size(64.dp),
                alpha = 0.5f,
                colorFilter = ColorFilter.tint(Color.Blue)
            )
        }
        composeRule.runOnIdle {
            val (_, captures) = capture()
            try {
                val bitmap = captures.single { it.isSourceImage }.bitmap
                assertThat(bitmap.width).isEqualTo(64)
                assertThat(bitmap.height).isEqualTo(64)
                assertThat(bitmap.getPixel(0, 0)).isZero()
                assertThat(bitmap.getPixel(32, 32)).isEqualTo(0x800000FF.toInt())
                assertThat(bitmap.getPixel(50, 50)).isZero()
            } finally { captures.forEach { it.bitmap.recycle() } }
        }
    }

    private fun capture(): Pair<CompositionHostDecomposeResult, List<PendingPixelCapture>> {
        val host = checkNotNull(findHost(composeRule.activity.window.decorView))
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope("home-capture-regression"))
        val captures = mutableListOf<PendingPixelCapture>()
        val request = CompositionHostDecomposeRequest(
            factory,
            factory.composeHost(factory.window("window"), "host"),
            host.resources.displayMetrics.density,
            hostBounds = null,
            nativeViewHandoff = { _, _ -> null },
            imagePrivacy = ImagePrivacy.MASK_NONE,
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            pendingPixelCaptureSink = PendingPixelCaptureSink(captures::add)
        )
        return checkNotNull(
            GranularComposeDecomposer(compatibilityGate = GranularComposeCompatibilityGate()).decompose(host, request)
        ) to captures
    }

    private fun findHost(view: View): ComposeView? {
        if (view is ComposeView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findHost(view.getChildAt(index))?.let {
                    return it
                }
            }
        }
        return null
    }
}
