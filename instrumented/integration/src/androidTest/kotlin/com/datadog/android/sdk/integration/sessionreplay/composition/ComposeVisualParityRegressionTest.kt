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
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
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
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/** Screenshot round trips through a deliberately small reference renderer, not the dashboard player. */
internal class ComposeVisualParityRegressionTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before fun keepAwake() {
        composeRule.runOnIdle {
            composeRule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @Test fun ellipsizedDescriptionUsesMeasuredGlyphs() {
        composeRule.setContent {
            Box(Modifier.size(300.dp, 200.dp).background(Color.White).testTag(ROOT)) {
                BasicText(
                    "Select Monitors saved view to add to your home screen for easy access",
                    Modifier.offset(16.dp, 32.dp).size(240.dp, 48.dp),
                    style = TextStyle(
                        color = Color.Black,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        compare("ellipsis") { result ->
            assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Text>()).isEmpty()
            assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()).isNotEmpty()
        }
    }

    private fun compare(name: String, verify: (CompositionHostDecomposeResult) -> Unit) {
        val actual = composeRule.onNodeWithTag(ROOT).captureToImage().asAndroidBitmap()
        lateinit var rendered: Bitmap
        val captures = mutableListOf<PendingPixelCapture>()
        try {
            composeRule.runOnIdle {
                val host = checkNotNull(findHost(composeRule.activity.window.decorView))
                val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope("visual-parity-$name"))
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
                val result = checkNotNull(
                    GranularComposeDecomposer(compatibilityGate = GranularComposeCompatibilityGate())
                        .decompose(host, request)
                )
                val evidence =
                    File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "visual-parity")
                evidence.mkdirs()
                File(evidence, "$name-model.txt").writeText("${result.wireframes}\n${result.nodes}")
                if (name == "decorative-icon") assertThat(captures.count { it.isSourceImage }).isEqualTo(1)
                verify(result)
                rendered = render(result, captures, host, actual.width, actual.height)
            }
            save(name, "device", actual)
            save(name, "capture", rendered)
            val expected = IntArray(actual.width * actual.height)
            val observed = IntArray(expected.size)
            actual.getPixels(expected, 0, actual.width, 0, 0, actual.width, actual.height)
            rendered.getPixels(observed, 0, actual.width, 0, 0, actual.width, actual.height)
            // DP coordinates are integral; allow two physical pixels of edge rounding, in both directions.
            val missing = unmatched(expected, observed, actual.width, actual.height)
            val extra = unmatched(observed, expected, actual.width, actual.height)
            assertThat(max(missing, extra).toDouble() / expected.size)
                .describedAs("%s screenshot mismatch: missing=%s extra=%s", name, missing, extra)
                .isLessThan(0.001)
            rendered.recycle()
        } finally {
            captures.forEach { it.bitmap.recycle() }
            actual.recycle()
        }
    }

    private fun unmatched(source: IntArray, target: IntArray, width: Int, height: Int): Int {
        fun close(a: Int, b: Int): Boolean {
            val grayscale = listOf(a, b).all {
                (it and 255) == (it ushr 8 and 255) &&
                    (it and 255) == (it ushr 16 and 255)
            }
            if (grayscale) return (a and 255 < 128) == (b and 255 < 128)
            return listOf(0, 8, 16, 24).all { abs((a ushr it and 255) - (b ushr it and 255)) <= 24 }
        }
        return source.indices.count { index ->
            if (close(source[index], target[index])) {
                false
            } else {
                val x = index % width
                val y = index / width
                !((y - 2).coerceAtLeast(0)..(y + 2).coerceAtMost(height - 1)).any { yy ->
                    ((x - 2).coerceAtLeast(0)..(x + 2).coerceAtMost(width - 1)).any { xx ->
                        close(source[index], target[yy * width + xx])
                    }
                }
            }
        }
    }

    private fun render(
        result: CompositionHostDecomposeResult,
        captures: List<PendingPixelCapture>,
        host: View,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val origin = IntArray(2).also(host::getLocationOnScreen)
        val density = host.resources.displayMetrics.density
        canvas.translate(-origin[0].toFloat(), -origin[1].toFloat())
        canvas.scale(density, density)
        val nodes = result.nodes.associateBy { it.identity }
        val wireframes = result.wireframes.associateBy { it.identity }
        val pixels = captures.associate { it.wireframeIdentity to it.bitmap }
        fun draw(child: CapturedChild) {
            canvas.save()
            when (child) {
                is CapturedChild.Layer -> {
                    val layer = checkNotNull(nodes[child.identity])
                    for (modifier in layer.modifiers) {
                        check(modifier is CapturedModifier.Clip) { "Unsupported reference effect: $modifier" }
                        val path = checkNotNull(PathParser.createPathFromPathData(modifier.path))
                        path.offset(layer.bounds.x.toFloat(), layer.bounds.y.toFloat())
                        canvas.clipPath(path)
                    }
                    layer.children.forEach(::draw)
                }
                is CapturedChild.Wireframe -> {
                    val wireframe = checkNotNull(wireframes[child.identity])
                    val bounds = wireframe.bounds
                    val rect = RectF(
                        bounds.x.toFloat(),
                        bounds.y.toFloat(),
                        (bounds.x + bounds.width).toFloat(),
                        (bounds.y + bounds.height).toFloat()
                    )
                    wireframe.clip?.let {
                        canvas.clipRect(
                            rect.left + (it.left ?: 0),
                            rect.top + (it.top ?: 0),
                            rect.right - (it.right ?: 0),
                            rect.bottom - (it.bottom ?: 0)
                        )
                    }
                    when (wireframe) {
                        is CapturedWireframe.Shape -> {
                            val color = wireframe.style?.backgroundColor
                            if (color != null) {
                                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    this.color =
                                        android.graphics.Color.parseColor(
                                            "#" + color.takeLast(2) + color.substring(1, 7)
                                        )
                                }
                                val radius = wireframe.style?.cornerRadius?.toFloat() ?: 0f
                                canvas.drawRoundRect(rect, radius, radius, paint)
                            }
                            wireframe.border?.let { stroke ->
                                val width = stroke.width.toFloat()
                                val outerRadius = wireframe.style?.cornerRadius?.toFloat() ?: 0f
                                val radius = (outerRadius - width / 2).coerceAtLeast(0f)
                                rect.inset(width / 2, width / 2)
                                canvas.drawRoundRect(
                                    rect,
                                    radius,
                                    radius,
                                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        this.color =
                                            android.graphics.Color.parseColor(
                                                "#" + stroke.color.takeLast(2) + stroke.color.substring(1, 7)
                                            )
                                        style = Paint.Style.STROKE
                                        strokeWidth = width
                                    }
                                )
                            }
                        }
                        is CapturedWireframe.Pixel -> canvas.drawBitmap(
                            checkNotNull(pixels[wireframe.identity]),
                            null,
                            rect,
                            Paint(Paint.FILTER_BITMAP_FLAG)
                        )
                        else -> error("Expected captured glyph/image pixels, got $wireframe")
                    }
                }
            }
            canvas.restore()
        }
        result.rootChildren.forEach(::draw)
        return bitmap
    }

    private fun save(name: String, kind: String, bitmap: Bitmap) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "visual-parity")
        directory.mkdirs()
        File(directory, "$name-$kind.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun findHost(view: View): ComposeView? {
        if (view is ComposeView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findHost(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private companion object { const val ROOT = "parity-root" }
}
