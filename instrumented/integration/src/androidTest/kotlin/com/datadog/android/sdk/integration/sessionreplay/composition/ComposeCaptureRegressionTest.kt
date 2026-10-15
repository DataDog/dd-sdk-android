/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.sdk.integration.sessionreplay.composition

import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EdgeEffect
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.compose.internal.granular.DefaultComposeHostCaptureRasterizer
import com.datadog.android.sessionreplay.compose.internal.granular.GranularComposeCompatibilityGate
import com.datadog.android.sessionreplay.compose.internal.granular.GranularComposeDecomposer
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class ComposeCaptureRegressionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun keepScreenOn() {
        val activity = composeRule.activity
        composeRule.runOnIdle { activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @Test
    fun skipUnneededRasterWork() {
        composeRule.runOnIdle {
            var draws = 0
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val view = object : View(context) {
                override fun onDraw(canvas: Canvas) {
                    draws++
                    canvas.drawColor(android.graphics.Color.RED)
                }
            }.apply { layout(0, 0, 10, 10) }
            val rasterizer = DefaultComposeHostCaptureRasterizer()
            val regions = listOf(Rect(0, 0, 5, 5), Rect(5, 5, 10, 10))

            assertThat(rasterizer.captureRegions(view, regions) { false }).containsOnlyNulls()
            assertThat(rasterizer.captureRegions(view, listOf(Rect(20, 20, 30, 30))) { true })
                .containsOnlyNulls()
            assertThat(draws).isZero()

            var checkpoints = 0
            val crops = rasterizer.captureRegions(view, regions) { ++checkpoints <= 2 }
            assertThat(draws).isEqualTo(1)
            assertThat(crops[0]).isNotNull()
            assertThat(crops[1]).isNull()
        }
    }

    @Test
    fun captureDrawingContainers() {
        composeRule.setContent {
            Row {
                val base = Modifier.size(48.dp).semantics { contentDescription = "drawing-container" }
                Box(base.drawBehind { drawCircle(Color.Red) }) { Text("Behind") }
                Box(base.drawWithContent { drawCircle(Color.Red); drawContent() }) { Text("Content") }
                Box(base.drawWithCache { onDrawBehind { drawCircle(Color.Red) } }) { Text("Cached") }
            }
        }
        val activity = composeRule.activity
        composeRule.runOnIdle {
            val host = checkNotNull(findComposeView(activity.window.decorView))
            check(host.width > 0 && host.height > 0) {
                "Unlock the physical device and leave the test activity visible."
            }
            val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope("drawing-containers"))
            val hostIdentity = factory.composeHost(factory.window("window"), "host")
            val result = GranularComposeDecomposer(compatibilityGate = GranularComposeCompatibilityGate()).decompose(
                host,
                CompositionHostDecomposeRequest(factory, hostIdentity, host.resources.displayMetrics.density, { _, _ ->
                    null
                })
            )
            assertThat(result).isNotNull()
            assertThat(result!!.wireframes).hasSize(3).allMatch { it is CapturedWireframe.Pixel }
        }
    }

    @Test
    fun captureOverscrollOnlyWhileActive() {
        composeRule.setContent {
            LazyColumn(Modifier.size(200.dp)) {
                items(30) { Text("Row $it", Modifier.size(40.dp)) }
            }
        }
        val activity = composeRule.activity
        composeRule.runOnIdle {
            val host = checkNotNull(findComposeView(activity.window.decorView))
            val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope("overscroll"))
            val hostIdentity = factory.composeHost(factory.window("window"), "host")
            val request =
                CompositionHostDecomposeRequest(factory, hostIdentity, host.resources.displayMetrics.density, { _, _ ->
                    null
                })
            val decomposer = GranularComposeDecomposer(compatibilityGate = GranularComposeCompatibilityGate())
            val root = checkNotNull(SemanticsUtils().findRootSemanticsNode(host))
            val nodes = generateSequence(listOf(root)) { level ->
                level.flatMap { it.children }.takeIf { it.isNotEmpty() }
            }.flatten()
            val modifier = nodes.flatMap { it.layoutInfo.getModifierInfo() }
                .map { it.modifier }
                .first { ComposeReflection.DrawOverscrollModifierClass?.isInstance(it) == true }
            val effect = checkNotNull(ComposeReflection.OverscrollEffectField).get(modifier)
            val edges = ComposeReflection.OverscrollEdgeFields.map { checkNotNull(it).get(effect) as EdgeEffect }

            assertThat(decomposer.decompose(host, request)!!.wireframes)
                .isNotEmpty().noneMatch { it is CapturedWireframe.Pixel }
            for (edge in edges) {
                edge.onPull(0.25f, 0.5f)
                assertThat(decomposer.decompose(host, request)!!.wireframes)
                    .hasSize(1).allMatch { it is CapturedWireframe.Pixel }
                edges.forEach { it.finish() }
            }
            assertThat(decomposer.decompose(host, request)!!.wireframes)
                .isNotEmpty().noneMatch { it is CapturedWireframe.Pixel }
        }
    }

    private fun findComposeView(view: View): ComposeView? {
        if (view is ComposeView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findComposeView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
