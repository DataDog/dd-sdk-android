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
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.compose.internal.granular.DefaultComposeHostCaptureRasterizer
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeTextCaptureCache
import com.datadog.android.sessionreplay.compose.internal.utils.ReflectionUtils
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import kotlinx.coroutines.CompletableDeferred
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.ref.WeakReference

internal class ComposeCaptureCacheRegressionTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before fun keepAwake() {
        val activity = composeRule.activity
        composeRule.runOnIdle { activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    private data class Config(
        val text: String = "A long line of text for clipping and layout changes, followed by more words.",
        val width: Int = 120,
        val height: Int = 28,
        val size: Int = 18,
        val family: FontFamily = FontFamily.Default,
        val overflow: TextOverflow = TextOverflow.Clip,
        val maxLines: Int = 4,
        val wrap: Boolean = true,
        val density: Float = 1f,
        val fontScale: Float = 1f,
        val direction: LayoutDirection = LayoutDirection.Ltr
    )
    private val state = mutableStateOf(Config())
    private val drawColor = mutableStateOf(Color.Red)
    private val producer = ColorProducer { drawColor.value }
    private val cache = ComposeTextCaptureCache()
    private val cachedUtils = SemanticsUtils(textCaptureCache = cache)
    private val originalUtils = SemanticsUtils()

    private fun content() {
        composeRule.setContent {
            val c = state.value
            CompositionLocalProvider(
                LocalDensity provides Density(c.density, c.fontScale),
                LocalLayoutDirection provides c.direction
            ) {
                BasicText(
                    c.text,
                    Modifier.requiredSize(c.width.dp, c.height.dp),
                    style = TextStyle(fontSize = c.size.sp, fontFamily = c.family),
                    maxLines = c.maxLines,
                    softWrap = c.wrap,
                    overflow = c.overflow,
                    color = producer
                )
            }
        }
    }
    private fun textNode(): SemanticsNode {
        val host = checkNotNull(findComposeView(composeRule.activity.window.decorView))
        val root = checkNotNull(originalUtils.findRootSemanticsNode(host))
        return generateSequence(listOf(root)) { level -> level.flatMap { it.children }.takeIf { it.isNotEmpty() } }
            .flatten().first { it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action != null }
    }
    private fun checkCurrent(): Any {
        val node = textNode()
        val expected = originalUtils.resolveTextLayoutInfo(node, InternalLogger.UNBOUND)
        assertThat(cachedUtils.resolveTextLayoutInfo(node, InternalLogger.UNBOUND)).isEqualTo(expected)
        val action = checkNotNull(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action)
        val modifier = node.layoutInfo.getModifierInfo().first {
            ReflectionUtils().isTextStringSimpleElement(it.modifier)
        }.modifier
        assertThat(cache.get(node.id, cache.state(node, action), modifier)).isNotNull()
        assertThat(cachedUtils.resolveTextLayoutInfo(node, InternalLogger.UNBOUND)).isEqualTo(expected)
        return action
    }

    @Test fun invalidateTextCacheForLayoutChanges() {
        content()
        var firstAction: Any? = null
        composeRule.runOnIdle { firstAction = checkCurrent() }
        var reusedAction = false
        val changes = listOf<(Config) -> Config>(
            { it.copy(width = 74) }, { it.copy(height = 12) },
            { it.copy(text = "Different text with a different shape and longer line.") },
            { it.copy(size = 28) }, { it.copy(family = FontFamily.Monospace) },
            { it.copy(overflow = TextOverflow.Ellipsis, maxLines = 1) },
            { it.copy(wrap = false) }, { it.copy(direction = LayoutDirection.Rtl) },
            { it.copy(density = 1.5f) }, { it.copy(fontScale = 1.3f) },
            { it.copy(height = 80, width = 180, wrap = true, maxLines = 4) }
        )
        changes.forEach { update ->
            composeRule.runOnIdle { state.value = update(state.value) }
            composeRule.runOnIdle { if (checkCurrent() === firstAction) reusedAction = true }
        }
        // Action identity alone does not express paragraph invalidation.
        composeRule.runOnIdle {
            assertThat(reusedAction).isTrue()
        }
    }

    @Test fun refreshDrawOnlyColorAndRejectUnknownAction() {
        content()
        composeRule.runOnIdle { checkCurrent() }
        composeRule.runOnIdle { drawColor.value = Color.Blue }
        composeRule.runOnIdle {
            checkCurrent()
            val node = textNode()
            assertThat(cache.state(node, Any())).isNull()
            assertThat(
                cachedUtils.resolveTextLayoutInfo(node, InternalLogger.UNBOUND)!!.color
            ).isEqualTo(Color.Blue.value)
        }
    }

    @Test fun invalidateWhenAsyncFontResolves() {
        val deferred = CompletableDeferred<Typeface>()
        val loaded = java.util.concurrent.atomic.AtomicBoolean(false)
        val loader = object : AndroidFont.TypefaceLoader {
            override fun loadBlocking(context: Context, font: AndroidFont): Typeface? = null
            override suspend fun awaitLoad(
                context: Context,
                font: AndroidFont
            ): Typeface? = deferred.await().also { loaded.set(true) }
        }
        val font = object : AndroidFont(FontLoadingStrategy.Async, loader) {
            override val weight = FontWeight.Normal
            override val style = FontStyle.Normal
        }
        state.value = Config(family = FontFamily(font), width = 80, height = 15)
        content()
        var paragraph: Any? = null
        composeRule.runOnIdle {
            checkCurrent()
            val node = textNode()
            paragraph =
                cache.state(
                    node,
                    checkNotNull(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action)
                )?.paragraph
            assertThat(paragraph).isNotNull()
        }
        deferred.complete(Typeface.MONOSPACE)
        composeRule.waitUntil(5000) { loaded.get() }
        composeRule.waitUntil(5000) {
            var changed = false
            composeRule.runOnIdle {
                val node = textNode()
                val current = cache.state(
                    node,
                    checkNotNull(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action)
                )?.paragraph
                changed = current == null || current !== paragraph
            }
            changed
        }
        composeRule.runOnIdle {
            val node = textNode()
            assertThat(cachedUtils.resolveTextLayoutInfo(node, InternalLogger.UNBOUND))
                .isEqualTo(originalUtils.resolveTextLayoutInfo(node, InternalLogger.UNBOUND))
            state.value = state.value.copy(width = 81)
        }
        composeRule.runOnIdle { checkCurrent() }
    }
    private class DrawingView(context: Context) : View(context) {
        var color = android.graphics.Color.RED
        var fail = false
        var fatal = false
        override fun onDraw(canvas: Canvas) {
            val paint = Paint().apply { color = this@DrawingView.color }
            canvas.save()
            canvas.translate(2f, 3f)
            canvas.clipRect(0, 0, canvas.width - 5, canvas.height - 6)
            canvas.drawRect(0f, 0f, canvas.width * 0.75f, canvas.height * 0.6f, paint)
            canvas.restore()
            if (fail) error("intentional drawing failure")
            if (fatal) throw AssertionError("intentional fatal draw failure")
        }
    }
    private fun pixels(bitmap: Bitmap): List<Int> {
        val values = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(values, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return values.toList()
    }

    @Test fun keepCropsIndependentAcrossReuseResizeFailureAndExpiry() {
        composeRule.runOnIdle {
            val view = DrawingView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 40, 60)
            }
            val candidate = DefaultComposeHostCaptureRasterizer()
            val regions = listOf(Rect(0, 0, 40, 60), Rect(5, 6, 30, 40), Rect(-5, -7, 15, 20), Rect(100, 100, 120, 120))
            val first = candidate.captureRegions(view, regions)
            assertThat(first.map { it?.let(::pixels) }).isEqualTo(freshPixels(view, regions))
            val preserved = first.map { it?.let(::pixels) }
            view.color = android.graphics.Color.BLUE
            val second = candidate.captureRegions(view, regions)
            assertThat(second.map { it?.let(::pixels) }).isEqualTo(freshPixels(view, regions))
            assertThat(first.map { it?.let(::pixels) }).isEqualTo(preserved)
            view.layout(0, 0, 50, 80)
            assertThat(candidate.captureRegions(view, regions).map { it?.let(::pixels) })
                .isEqualTo(freshPixels(view, regions))
            view.fail = true
            assertThat(candidate.captureRegions(view, regions)).containsOnlyNulls()
            view.fail = false
            view.fatal = true
            assertThatThrownBy { candidate.captureRegions(view, regions) }.isInstanceOf(AssertionError::class.java)
            view.fatal = false
            var calls = 0
            assertThat(candidate.captureRegions(view, regions) { ++calls < 2 }).containsOnlyNulls()
            assertThat(candidate.captureRegions(view, regions).map { it?.let(::pixels) })
                .isEqualTo(freshPixels(view, regions))
            assertThat(first.map { it?.let(::pixels) }).isEqualTo(preserved)
            first.filterNotNull().forEach { it.recycle() }
            second.filterNotNull().forEach { it.recycle() }
        }
    }

    @Test fun boundScratchMemoryForOversizedHosts() {
        composeRule.runOnIdle {
            val candidate = DefaultComposeHostCaptureRasterizer()
            val view = DrawingView(InstrumentationRegistry.getInstrumentation().targetContext)
            view.layout(0, 0, 40, 60)
            candidate.captureRegions(view, listOf(Rect(0, 0, 10, 10))).filterNotNull().forEach { it.recycle() }
            val retained = checkNotNull(retainedScratch(candidate))
            view.layout(0, 0, 2048, 2049)
            candidate.captureRegions(view, listOf(Rect(0, 0, 10, 10))).filterNotNull().forEach { it.recycle() }
            assertThat(retainedScratch(candidate)).isSameAs(retained)
            assertThat(retained.byteCount).isLessThanOrEqualTo(16 * 1024 * 1024)
            assertThat(pixels(retained)).containsOnly(android.graphics.Color.TRANSPARENT)
        }
    }

    @Test fun allowReentrantCaptureWithoutSharingTheActiveScratch() {
        composeRule.runOnIdle {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val candidate = DefaultComposeHostCaptureRasterizer()
            val inner = DrawingView(context).apply { layout(0, 0, 20, 20) }
            var nested: Bitmap? = null
            val outer = object : View(context) {
                override fun onDraw(canvas: Canvas) {
                    nested = candidate.captureRegions(inner, listOf(Rect(0, 0, 20, 20))).single()
                    canvas.drawColor(android.graphics.Color.BLUE)
                }
            }.apply { layout(0, 0, 30, 30) }
            val captured = checkNotNull(candidate.captureRegions(outer, listOf(Rect(0, 0, 30, 30))).single())
            assertThat(captured.getPixel(10, 10)).isEqualTo(android.graphics.Color.BLUE)
            assertThat(checkNotNull(nested).getPixel(5, 5)).isEqualTo(android.graphics.Color.RED)
            val before = pixels(captured)
            candidate.captureRegions(inner, listOf(Rect(0, 0, 20, 20)))
            assertThat(pixels(captured)).isEqualTo(before)
        }
    }
    private fun freshPixels(view: View, regions: List<Rect>): List<List<Int>?> {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        return try {
            view.draw(Canvas(bitmap))
            regions.map { region ->
                val clipped = Rect(region)
                if (clipped.intersect(0, 0, bitmap.width, bitmap.height)) {
                    val crop = Bitmap.createBitmap(bitmap, clipped.left, clipped.top, clipped.width(), clipped.height())
                    pixels(crop).also { if (crop !== bitmap) crop.recycle() }
                } else {
                    null
                }
            }
        } finally {
            bitmap.recycle()
        }
    }
    private fun retainedScratch(rasterizer: DefaultComposeHostCaptureRasterizer): Bitmap? {
        val field = DefaultComposeHostCaptureRasterizer::class.java.getDeclaredField("scratch")
        field.isAccessible = true
        return ((field.get(rasterizer) as? WeakReference<*>)?.get() as? Bitmap)
    }
    private fun findComposeView(view: View): ComposeView? {
        if (view is ComposeView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findComposeView(view.getChildAt(index))?.let {
                    return it
                }
            }
        }
        return null
    }
}
