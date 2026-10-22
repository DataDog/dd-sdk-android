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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import com.datadog.android.compose.datadog
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.granular.DefaultComposeDrawBehindColorResolver
import com.datadog.android.sessionreplay.compose.internal.granular.GranularComposeCompatibilityGate
import com.datadog.android.sessionreplay.compose.internal.granular.GranularComposeDecomposer
import com.datadog.android.sessionreplay.compose.sessionReplayHide
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

    @Test fun uniformDrawBehindWithImageChild() {
        val icon = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawRect(6f, 6f, 18f, 18f, Paint().apply { color = android.graphics.Color.BLACK })
        }
        composeRule.setContent {
            Box(Modifier.size(300.dp, 200.dp).background(Color.White).testTag(ROOT)) {
                Box(
                    Modifier.size(300.dp, 80.dp).drawBehind {
                        drawRect(Color(0xFFF2ECF6))
                    }.semantics {
                        isTraversalGroup =
                            true
                    }
                ) {
                    Image(icon.asImageBitmap(), "toolbar-action", Modifier.offset(264.dp, 28.dp).size(24.dp))
                }
            }
        }
        compare("toolbar") { result ->
            assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Shape>())
                .anyMatch { it.style?.backgroundColor == "#f2ecf6ff" }
        }
    }

    @Test fun titleRespectsAllocatedWidth() {
        composeRule.setContent {
            Box(Modifier.size(300.dp, 200.dp).background(Color.White).testTag(ROOT)) {
                Box(Modifier.offset(16.dp, 24.dp).size(152.dp, 44.dp).clipToBounds()) {
                    BasicText(
                        "Datadog title that extends beyond the action",
                        style = TextStyle(color = Color.Black, fontSize = 24.sp, lineHeight = 32.sp),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
        compare("title") { result ->
            assertThat(result.nodes.flatMap { it.modifiers }.filterIsInstance<CapturedModifier.Clip>()).isNotEmpty()
            assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Text>()).isEmpty()
        }
    }

    @Test fun scrolledContentRespectsRectangularViewport() {
        composeRule.setContent {
            Box(Modifier.size(300.dp, 200.dp).background(Color.White).testTag(ROOT)) {
                Box(Modifier.offset(0.dp, 56.dp).size(300.dp, 100.dp).clipToBounds()) {
                    Column(Modifier.offset(0.dp, (-40).dp)) {
                        BasicText(
                            "This row is above the viewport",
                            style = TextStyle(color = Color.Black, fontSize = 16.sp, lineHeight = 24.sp)
                        )
                        Box(Modifier.size(300.dp, 140.dp).background(Color.Red))
                    }
                }
            }
        }
        compare("viewport") { result ->
            assertThat(result.nodes.flatMap { it.modifiers }.filterIsInstance<CapturedModifier.Clip>()).isNotEmpty()
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

    @Test fun nonuniformDrawBehindIsNotExposedAsUniformFill() {
        composeRule.setContent {
            Box(
                Modifier.size(80.dp).drawBehind {
                    drawRect(Color.Blue, size = androidx.compose.ui.geometry.Size(size.width / 2, size.height))
                }.testTag(ROOT)
            )
        }
        composeRule.runOnIdle {
            val utils = com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils()
            val reflection = com.datadog.android.sessionreplay.compose.internal.utils.ReflectionUtils()
            val resolver = DefaultComposeDrawBehindColorResolver()
            val root =
                checkNotNull(utils.findRootSemanticsNode(checkNotNull(findHost(composeRule.activity.window.decorView))))
            var checked = false
            fun visit(node: androidx.compose.ui.semantics.SemanticsNode) {
                for (modifier in node.layoutInfo.getModifierInfo()) {
                    if (reflection.isDrawBehindElementClass(modifier.modifier)) {
                        assertThat(resolver.resolve(node, modifier)).isNull()
                        checked = true
                    }
                }
                node.children.forEach(::visit)
            }
            visit(root)
            assertThat(checked).isTrue()
        }
    }

    @Test fun preserveIconWithoutContentDescriptionInsideSemanticButton() {
        val icon = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawRect(6f, 6f, 18f, 18f, Paint().apply { color = android.graphics.Color.BLACK })
        }
        composeRule.setContent {
            Box(Modifier.size(300.dp, 200.dp).background(Color.White).testTag(ROOT)) {
                Box(
                    Modifier.offset(16.dp, 24.dp).size(80.dp).semantics {
                        contentDescription = "tab"
                        role = androidx.compose.ui.semantics.Role.Tab
                    }
                ) {
                    Image(icon.asImageBitmap(), null, Modifier.offset(24.dp, 24.dp).size(24.dp))
                }
            }
        }
        compare("decorative-icon") { result ->
            assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()).isNotEmpty()
        }
    }

    @Test fun preserveLayoutOnlyDividerAndRoundedContainer() {
        composeRule.setContent {
            Box(Modifier.size(300.dp, 200.dp).background(Color.White).testTag(ROOT)) {
                Box(
                    Modifier.offset(16.dp, 24.dp).size(240.dp, 80.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(Color.Gray)
                ) {
                    Box(Modifier.offset(0.dp, 40.dp).size(240.dp, 1.dp).drawBehind { drawRect(Color.Blue) })
                }
            }
        }
        compare("decorative-divider") { result ->
            assertThat(result.wireframes).isNotEmpty()
            assertThat(result.nodes.flatMap { it.modifiers }.filterIsInstance<CapturedModifier.Clip>()).isNotEmpty()
        }
    }

    @Test fun preserveZeroHeightHairlineDivider() {
        composeRule.setContent {
            Box(Modifier.size(300.dp, 200.dp).background(Color.White).testTag(ROOT)) {
                Box(
                    Modifier.offset(16.dp, 64.dp).size(240.dp, 0.dp).drawBehind {
                        drawLine(
                            Color.Blue,
                            androidx.compose.ui.geometry.Offset.Zero,
                            androidx.compose.ui.geometry.Offset(size.width, 0f),
                            strokeWidth = 0f
                        )
                    }
                )
            }
        }
        compare("hairline-divider") { result ->
            assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()).isNotEmpty()
        }
    }

    @Test fun preserveInstrumentedHairlineDivider() {
        composeRule.setContent {
            Box(Modifier.size(300.dp, 200.dp).background(Color.White).testTag(ROOT)) {
                Box(Modifier.datadog("Spacer").size(0.dp))
                Box(
                    Modifier.offset(16.dp, 64.dp).datadog("HorizontalDivider")
                        .padding(horizontal = 16.dp).size(240.dp, 0.dp).drawBehind {
                            drawLine(
                                Color.Blue,
                                androidx.compose.ui.geometry.Offset.Zero,
                                androidx.compose.ui.geometry.Offset(size.width, 0f),
                                strokeWidth = 0f
                            )
                        }
                )
            }
        }
        compare("instrumented-hairline") { result ->
            assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()).hasSize(1)
        }
    }

    @Test fun preserveDividerWhenSourceImageIsOutsideViewport() {
        val image = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
        }
        composeRule.setContent {
            Box(Modifier.size(300.dp, 200.dp).background(Color.White).clipToBounds().testTag(ROOT)) {
                Image(image.asImageBitmap(), "offscreen-image", Modifier.offset(16.dp, (-48).dp).size(24.dp))
                Box(
                    Modifier.offset(16.dp, 64.dp).datadog("HorizontalDivider").size(240.dp, 0.dp).drawBehind {
                        drawLine(
                            Color.Blue,
                            androidx.compose.ui.geometry.Offset.Zero,
                            androidx.compose.ui.geometry.Offset(size.width, 0f),
                            strokeWidth = 0f
                        )
                    }
                )
            }
        }
        compare("offscreen-image-divider") { result ->
            assertThat(result.wireframes.filterIsInstance<CapturedWireframe.PrivacyPlaceholder>()).isEmpty()
        }
    }

    @Test fun preserveVectorIconInPartiallyVisibleCard() {
        val vector = androidx.compose.ui.graphics.vector.ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = androidx.compose.ui.graphics.SolidColor(Color.White)) {
                moveTo(12f, 4f)
                lineTo(20f, 12f)
                lineTo(12f, 20f)
                lineTo(4f, 12f)
                close()
            }
        }.build()
        composeRule.setContent {
            Box(Modifier.size(300.dp, 200.dp).background(Color.White).testTag(ROOT)) {
                androidx.compose.foundation.layout.Row(
                    Modifier.offset(16.dp, 24.dp).size(264.dp, 80.dp).clipToBounds()
                ) {
                    repeat(3) {
                        Box(
                            Modifier.size(112.dp, 77.dp)
                                .border(1.dp, Color(0xFFCBC4D4), RoundedCornerShape(8.dp))
                        ) {
                            Image(
                                androidx.compose.ui.graphics.vector.rememberVectorPainter(vector),
                                "severity-icon-$it",
                                Modifier.offset(16.dp, 16.dp).size(20.dp).clipToBounds(),
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFFE52975))
                            )
                        }
                    }
                }
            }
        }
        compare("clipped-severity-icons") { result ->
            assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()).hasSize(3)
        }
    }

    @Test fun protectHiddenZeroHeightSiblingFromHairlineCrop() {
        composeRule.setContent {
            Box(Modifier.size(300.dp, 200.dp).background(Color.White).testTag(ROOT)) {
                Box(
                    Modifier.offset(16.dp, 64.dp).size(240.dp, 0.dp).sessionReplayHide(true).drawBehind {
                        drawLine(
                            Color.Red,
                            androidx.compose.ui.geometry.Offset.Zero,
                            androidx.compose.ui.geometry.Offset(size.width, 0f),
                            strokeWidth = 0f
                        )
                    }
                )
                Box(
                    Modifier.offset(16.dp, 64.dp).datadog("HorizontalDivider").size(240.dp, 0.dp).drawBehind {
                        drawLine(
                            Color.Transparent,
                            androidx.compose.ui.geometry.Offset.Zero,
                            androidx.compose.ui.geometry.Offset(size.width, 0f),
                            strokeWidth = 0f
                        )
                    }
                )
            }
        }
        composeRule.runOnIdle {
            val host = checkNotNull(findHost(composeRule.activity.window.decorView))
            val captures = mutableListOf<PendingPixelCapture>()
            val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope("hairline-privacy"))
            val result = GranularComposeDecomposer(compatibilityGate = GranularComposeCompatibilityGate()).decompose(
                host,
                CompositionHostDecomposeRequest(
                    factory,
                    factory.composeHost(factory.window("window"), "host"),
                    host.resources.displayMetrics.density,
                    hostBounds = null,
                    nativeViewHandoff = { _, _ -> null },
                    imagePrivacy = ImagePrivacy.MASK_NONE,
                    textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
                    pendingPixelCaptureSink = PendingPixelCaptureSink(captures::add)
                )
            )
            try {
                assertThat(result).isNotNull()
                assertThat(captures).isEmpty()
            } finally { captures.forEach { it.bitmap.recycle() } }
        }
    }

    @Test fun preservePartiallyClippedSeverityBorders() {
        composeRule.setContent {
            Box(Modifier.size(300.dp, 200.dp).background(Color.White).testTag(ROOT)) {
                androidx.compose.foundation.layout.Row(
                    Modifier.offset(16.dp, 24.dp).size(240.dp, 80.dp).clipToBounds()
                ) {
                    repeat(3) {
                        Box(
                            Modifier.semantics { role = androidx.compose.ui.semantics.Role.Button }
                                .size(112.dp, 77.dp).border(1.dp, Color(0xFFCBC4D4), RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        }
        compare("clipped-borders") { result ->
            assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Shape>().filter { it.border != null })
                .hasSize(3)
        }
    }

    @Test fun preserveImageMaskForUnlabeledIcon() = verifyDecorativePrivacy(hideParent = false)

    @Test fun preserveHiddenParentForUnlabeledIcon() = verifyDecorativePrivacy(hideParent = true)

    private fun verifyDecorativePrivacy(hideParent: Boolean) {
        val icon = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        composeRule.setContent {
            Box(
                Modifier.size(80.dp).testTag(ROOT).then(
                    if (hideParent) Modifier.sessionReplayHide(true) else Modifier
                )
            ) { Image(icon.asImageBitmap(), null, Modifier.size(24.dp)) }
        }
        composeRule.runOnIdle {
            val host = checkNotNull(findHost(composeRule.activity.window.decorView))
            val captures = mutableListOf<PendingPixelCapture>()
            val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope("decorative-privacy"))
            val request = CompositionHostDecomposeRequest(
                factory,
                factory.composeHost(factory.window("window"), "host"),
                host.resources.displayMetrics.density,
                hostBounds = null,
                nativeViewHandoff = { _, _ -> null },
                imagePrivacy = if (hideParent) ImagePrivacy.MASK_NONE else ImagePrivacy.MASK_ALL,
                pendingPixelCaptureSink = PendingPixelCaptureSink(captures::add)
            )
            val result = checkNotNull(
                GranularComposeDecomposer(
                    compatibilityGate = GranularComposeCompatibilityGate()
                ).decompose(host, request)
            )
            try {
                assertThat(captures).isEmpty()
                assertThat(result.wireframes.filterIsInstance<CapturedWireframe.PrivacyPlaceholder>()).isNotEmpty()
            } finally { captures.forEach { it.bitmap.recycle() } }
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
