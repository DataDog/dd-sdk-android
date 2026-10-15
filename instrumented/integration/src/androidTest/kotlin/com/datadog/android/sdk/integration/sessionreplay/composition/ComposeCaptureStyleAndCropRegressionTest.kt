/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.sdk.integration.sessionreplay.composition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.compose.internal.granular.DefaultComposeHostCaptureRasterizer
import com.datadog.android.sessionreplay.compose.internal.granular.GranularComposeCompatibilityGate
import com.datadog.android.sessionreplay.compose.internal.granular.GranularComposeDecomposer
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeRequest
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.ByteBuffer

internal class ComposeCaptureStyleAndCropRegressionTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before fun keepAwake() {
        composeRule.runOnIdle {
            composeRule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    private class DrawingView(context: Context) : View(context) {
        var blue = false
        override fun onDraw(canvas: Canvas) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            for (alpha in 0..255) {
                paint.color = android.graphics.Color.argb(alpha, 117, 193, if (blue) 239 else 53)
                canvas.drawRect(
                    (alpha % 16) * 3f,
                    (alpha / 16) * 3f,
                    (alpha % 16 + 1) * 3f,
                    (alpha / 16 + 1) * 3f,
                    paint
                )
            }
            canvas.save(); canvas.translate(3f, 7f); canvas.clipRect(0, 0, canvas.width - 7, canvas.height - 9)
            paint.color = android.graphics.Color.argb(83, 77, 201, 133)
            canvas.drawCircle(canvas.width / 2f, canvas.height / 2f, canvas.width / 3f, paint)
            canvas.restore()
        }
    }
    private fun bytes(bitmap: Bitmap): List<Byte> {
        val buffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        return buffer.array().toList()
    }

    @Test fun preservePixelBytesAcrossAlphaClipsResizingAndNestedCapture() {
        composeRule.runOnIdle {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val view = DrawingView(context).apply { layout(0, 0, 48, 64) }
            val candidate = DefaultComposeHostCaptureRasterizer()
            val regions = listOf(Rect(0, 0, 48, 64), Rect(3, 4, 21, 37), Rect(-5, -7, 16, 22), Rect(99, 99, 100, 100))
            repeat(4) { iteration ->
                view.blue = iteration % 2 == 1
                if (iteration > 1) view.layout(0, 0, 60, 80)
                val expected = originalCapture(view, regions)
                val captured = candidate.captureRegions(view, regions)
                assertThat(captured.map { it?.let(::bytes) }).isEqualTo(expected.map { it?.let(::bytes) })
                captured.zip(expected).forEach { (actual, baseline) ->
                    if (actual != null && baseline != null) {
                        assertThat(actual.density).isEqualTo(baseline.density)
                        assertThat(actual.config).isEqualTo(baseline.config)
                        assertThat(actual.isPremultiplied).isEqualTo(baseline.isPremultiplied)
                    }
                }
                val preserved = captured.map { it?.let(::bytes) }
                var calls = 0
                val nested = candidate.captureRegions(view, regions) {
                    if (++calls == 3) candidate.captureRegions(view, listOf(Rect(0, 0, 8, 8)))
                    true
                }
                assertThat(nested.map { it?.let(::bytes) }).isEqualTo(expected.map { it?.let(::bytes) })
                assertThat(captured.map { it?.let(::bytes) }).isEqualTo(preserved)
            }
        }
    }

    @Test fun preserveStylesAcrossDistinctColorsSizesAndCaptureChanges() {
        val version = mutableStateOf(0)
        composeRule.setContent {
            Box {
                repeat(180) { item ->
                    val index = item / 2
                    val color = Color(0xFF000000.toInt() or ((index * 153177 + version.value * 7919) and 0xFFFFFF))
                    Text(
                        "Item $item",
                        fontSize = (9 + index % 4 + version.value).sp,
                        color = color,
                        modifier = Modifier.size(150.dp, 60.dp).background(color)
                    )
                }
            }
        }
        val activity = composeRule.activity
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope("style-regression"))
        val identity = factory.composeHost(factory.window("window"), "host")
        val candidate = GranularComposeDecomposer(compatibilityGate = GranularComposeCompatibilityGate())
        repeat(3) { value ->
            composeRule.runOnIdle { version.value = value }
            composeRule.runOnIdle {
                val host = checkNotNull(findHost(activity.window.decorView))
                val request =
                    CompositionHostDecomposeRequest(factory, identity, host.resources.displayMetrics.density, { _, _ ->
                        null
                    })
                val actual = checkNotNull(candidate.decompose(host, request))
                val texts = actual.wireframes.filterIsInstance<CapturedWireframe.Text>()
                assertThat(texts.map { it.text }).isEqualTo((0 until 180).map { "Item $it" })
                assertThat(
                    texts.map {
                        it.textStyle.size
                    }
                ).isEqualTo((0 until 180).map { (9 + (it / 2) % 4 + value).toLong() })
                assertThat(texts.map { it.textStyle.color }).isEqualTo(
                    (0 until 180).map { item ->
                        val color = Color(0xFF000000.toInt() or (((item / 2) * 153177 + value * 7919) and 0xFFFFFF))
                        DefaultColorStringFormatter.formatColorAsHexString(color.toArgb())
                    }
                )
            }
        }
    }
    private fun originalCapture(view: View, regions: List<Rect>): List<Bitmap?> {
        val source = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        return try {
            view.draw(Canvas(source))
            regions.map { bounds ->
                val clipped = Rect(bounds)
                if (clipped.intersect(0, 0, source.width, source.height)) {
                    Bitmap.createBitmap(source, clipped.left, clipped.top, clipped.width(), clipped.height())
                } else {
                    null
                }
            }
        } finally { source.recycle() }
    }
    private fun findHost(view: View): ComposeView? {
        if (view is ComposeView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findHost(view.getChildAt(i))?.let { return it }
        return null
    }
}
