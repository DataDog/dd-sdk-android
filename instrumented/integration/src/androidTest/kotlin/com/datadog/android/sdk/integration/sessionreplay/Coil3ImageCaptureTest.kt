/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.datadog.android.sessionreplay.compose.testing.SemanticsPainterCapture
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
internal class Coil3ImageCaptureTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureLoadedImageFromCoil3Crossfade() {
        // When / Then
        assertCrossfadeCapture(useRememberedPainter = false)
    }

    @Test
    fun captureLoadedImageFromRememberedCoil3Crossfade() {
        // When / Then
        assertCrossfadeCapture(useRememberedPainter = true)
    }

    private fun assertCrossfadeCapture(useRememberedPainter: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fakeBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        fakeBitmap.eraseColor(0xff123456.toInt())
        val fakeLoader = ImageLoader.Builder(context).build()
        val fakeRequest = ImageRequest.Builder(context)
            .data(fakeBitmap)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .crossfade(1000)
            .build()
        val fakeLoaded = AtomicBoolean(false)
        val fakeFailure = AtomicReference<Throwable>()
        val fakeVisible = mutableStateOf(true)
        composeRule.setContent {
            if (fakeVisible.value) {
                val modifier = Modifier.size(64.dp).testTag(IMAGE_TAG)
                if (useRememberedPainter) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = fakeRequest,
                            imageLoader = fakeLoader,
                            onSuccess = { fakeLoaded.set(true) },
                            onError = { fakeFailure.set(it.result.throwable) }
                        ),
                        contentDescription = "Coil3 image",
                        modifier = modifier
                    )
                } else {
                    AsyncImage(
                        model = fakeRequest,
                        imageLoader = fakeLoader,
                        contentDescription = "Coil3 image",
                        modifier = modifier,
                        onSuccess = { fakeLoaded.set(true) },
                        onError = { fakeFailure.set(it.result.throwable) }
                    )
                }
            }
        }
        try {
            composeRule.waitUntil(10_000) { fakeLoaded.get() || fakeFailure.get() != null }
            assertThat(fakeFailure.get()).isNull()
            val fakeNode = composeRule.onNodeWithTag(IMAGE_TAG).fetchSemanticsNode()
            composeRule.runOnIdle {
                val result = SemanticsPainterCapture().resolve(fakeNode)
                assertThat(result).isNotNull()
                assertThat(checkNotNull(result).isContextualImage).isTrue()
                assertThat(result.bitmap.isRecycled).isFalse()
                assertThat(result.bitmap.width).isPositive()
                assertThat(result.bitmap.height).isPositive()
            }
        } finally {
            composeRule.runOnIdle { fakeVisible.value = false }
            composeRule.waitForIdle()
            fakeLoader.shutdown()
            fakeBitmap.recycle()
        }
    }

    private companion object {
        const val IMAGE_TAG = "coil3-image"
    }
}
