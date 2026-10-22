/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.sdk.integration.sessionreplay.composition

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.granular.GranularComposeCompatibilityGate
import com.datadog.android.sessionreplay.compose.internal.granular.GranularComposeDecomposer
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeRequest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class ComposeImageCaptureRegressionTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before fun keepAwake() {
        composeRule.runOnIdle {
            composeRule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @Test fun preserveSourceImageSizeWhileScrolling() {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.GREEN)
        }
        lateinit var scrollState: androidx.compose.foundation.ScrollState
        composeRule.setContent {
            scrollState = rememberScrollState()
            Column(Modifier.height(160.dp).verticalScroll(scrollState)) {
                Spacer(Modifier.height(48.dp))
                Image(
                    bitmap.asImageBitmap(),
                    "scrolling-image",
                    // Coil places semantics outside its padding; Image normally puts them inside.
                    Modifier.semantics { contentDescription = "outer-image" }.padding(16.dp).size(96.dp)
                )
                Spacer(Modifier.height(200.dp))
            }
        }
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope("image-scroll-regression"))
        val identity = factory.composeHost(factory.window("window"), "host")
        val candidate = GranularComposeDecomposer(compatibilityGate = GranularComposeCompatibilityGate())
        val clips = mutableListOf<Long>()
        for (offsetDp in listOf(0, 96, 0)) {
            val density = composeRule.activity.resources.displayMetrics.density
            runBlocking { scrollState.scrollTo((offsetDp * density).toInt()) }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                val host = checkNotNull(findHost(composeRule.activity.window.decorView))
                val request = CompositionHostDecomposeRequest(
                    factory,
                    identity,
                    density,
                    hostBounds = null,
                    nativeViewHandoff = { _, _ -> null },
                    imagePrivacy = ImagePrivacy.MASK_NONE,
                    textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
                )
                val result = checkNotNull(candidate.decompose(host, request))
                val image = result.wireframes.filterIsInstance<CapturedWireframe.Pixel>().single()
                assertThat(image.bounds.width).isEqualTo(96L)
                assertThat(image.bounds.height).isEqualTo(96L)
                assertThat(image.bounds.x).isEqualTo(16L)
                clips += image.clip?.top ?: 0L
            }
        }
        assertThat(clips.first()).isZero()
        assertThat(clips[1]).isGreaterThan(0L)
        assertThat(clips.last()).isZero()
    }

    @Test fun preserveNestedImageBackgroundsAndRoundedClips() {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            Image(
                bitmap.asImageBitmap(),
                "rounded-image",
                Modifier.semantics { contentDescription = "outer-icon" }
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(Color.Green)
                    .padding(32.dp)
                    .size(128.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
                    .padding(4.dp)
            )
        }
        composeRule.runOnIdle {
            val host = checkNotNull(findHost(composeRule.activity.window.decorView))
            val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope("rounded-image-regression"))
            val identity = factory.composeHost(factory.window("window"), "host")
            val request = CompositionHostDecomposeRequest(
                factory,
                identity,
                host.resources.displayMetrics.density,
                hostBounds = null,
                nativeViewHandoff = { _, _ -> null },
                imagePrivacy = ImagePrivacy.MASK_NONE
            )
            val result = checkNotNull(
                GranularComposeDecomposer(
                    compatibilityGate = GranularComposeCompatibilityGate()
                ).decompose(host, request)
            )
            val backgrounds = result.wireframes.filterIsInstance<CapturedWireframe.Shape>()
                .sortedByDescending { it.bounds.width }
            assertThat(backgrounds.map { it.bounds.width }).containsExactly(192L, 128L)
            assertThat(backgrounds.map { it.bounds.x }).containsExactly(16L, 48L)
            assertThat(backgrounds.map { it.style?.cornerRadius }).containsExactly(96.0, 8.0)
            val roundedLayers = result.nodes.filter {
                it.modifiers.any { modifier -> modifier is CapturedModifier.Clip && " A " in modifier.path }
            }
                .sortedByDescending { it.bounds.width }
            assertThat(roundedLayers.map { it.bounds.width }).containsExactly(192L, 128L)
            assertThat(roundedLayers.map { it.bounds.x }).containsExactly(16L, 48L)
            assertThat(roundedLayers.first().children.map { it.identity })
                .containsExactly(backgrounds.first().identity, roundedLayers.last().identity)
            val image = result.wireframes.filterIsInstance<CapturedWireframe.Pixel>().single()
            // Pixel-to-dp conversion and Fit scaling may truncate a fractional pixel.
            assertThat(image.bounds.width).isBetween(119L, 120L)
            assertThat(image.bounds.x).isEqualTo(52L)
        }
    }

    @Test fun preserveCoilImagePaddingWhileScrolling() {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.GREEN)
        }
        val coilLoaded = mutableStateOf(false)
        val coil3Loaded = mutableStateOf(false)
        lateinit var scrollState: androidx.compose.foundation.ScrollState
        composeRule.setContent {
            scrollState = rememberScrollState()
            Column(Modifier.height(160.dp).verticalScroll(scrollState)) {
                Spacer(Modifier.height(48.dp))
                Row {
                    coil.compose.AsyncImage(
                        model = bitmap,
                        contentDescription = "coil-image",
                        modifier = Modifier.padding(16.dp).size(96.dp),
                        onSuccess = { coilLoaded.value = true }
                    )
                    coil3.compose.AsyncImage(
                        model = bitmap,
                        contentDescription = "coil3-image",
                        modifier = Modifier.padding(16.dp).size(96.dp),
                        onSuccess = { coil3Loaded.value = true }
                    )
                }
                Spacer(Modifier.height(200.dp))
            }
        }
        composeRule.waitUntil(10_000) { coilLoaded.value && coil3Loaded.value }
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope("coil-padding-regression"))
        val identity = factory.composeHost(factory.window("window"), "host")
        val candidate = GranularComposeDecomposer(compatibilityGate = GranularComposeCompatibilityGate())
        for (offsetDp in listOf(0, 96, 0)) {
            val density = composeRule.activity.resources.displayMetrics.density
            runBlocking { scrollState.scrollTo((offsetDp * density).toInt()) }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                val host = checkNotNull(findHost(composeRule.activity.window.decorView))
                val request = CompositionHostDecomposeRequest(
                    factory,
                    identity,
                    density,
                    hostBounds = null,
                    nativeViewHandoff = { _, _ -> null },
                    imagePrivacy = ImagePrivacy.MASK_NONE
                )
                val images = checkNotNull(candidate.decompose(host, request))
                    .wireframes.filterIsInstance<CapturedWireframe.Pixel>()
                assertThat(images.map { it.bounds.x }).containsExactly(16L, 144L)
                assertThat(images.map { it.bounds.width }).containsOnly(96L)
                assertThat(images.map { it.bounds.height }).containsOnly(96L)
                assertThat(images.map { it.clip?.top ?: 0L })
                    .containsOnly(if (offsetDp == 0) 0L else 32L)
            }
        }
    }
    private fun findHost(view: View): ComposeView? {
        if (view is ComposeView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findHost(view.getChildAt(i))?.let { return it }
        return null
    }
}
