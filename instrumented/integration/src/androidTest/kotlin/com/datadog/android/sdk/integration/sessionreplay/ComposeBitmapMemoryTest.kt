/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Debug
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.compose.testing.SemanticsPainterCapture
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.testing.createImageWireframeHelper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
internal class ComposeBitmapMemoryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun boundNativeMemoryWhenCompressionStalls() {
        // Given
        val fakeBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val fakeStarted = CountDownLatch(1)
        val fakeRelease = CountDownLatch(1)
        val fakeExecutor = Executors.newSingleThreadExecutor()
        val testedHelper = createCaptureHelper(fakeExecutor)
        val testedSemantics = SemanticsPainterCapture()
        val fakeCompleted = AtomicInteger()
        val fakeCallback = countingCallback(fakeCompleted)
        val fakeVisible = mutableStateOf(true)
        composeRule.setContent {
            if (fakeVisible.value) Image(fakeBitmap.asImageBitmap(), "changing image", Modifier.testTag(IMAGE_TAG))
        }
        val fakeNode = composeRule.onNodeWithTag(IMAGE_TAG).fetchSemanticsNode()
        fakeExecutor.execute {
            fakeStarted.countDown()
            check(fakeRelease.await(60, TimeUnit.SECONDS))
        }
        assertThat(fakeStarted.await(5, TimeUnit.SECONDS)).isTrue()
        val fakeNativeBefore = Debug.getNativeHeapAllocatedSize()
        var fakeNativeWhileBlocked = fakeNativeBefore
        var fakeNativeAfter = fakeNativeBefore

        // When
        try {
            repeat(CAPTURE_COUNT) { frame ->
                composeRule.runOnIdle {
                    fakeBitmap.eraseColor(0xff000000.toInt() or frame)
                    val bitmapInfo =
                        checkNotNull(testedSemantics.resolve(fakeNode))
                    testedHelper.createImageWireframeByBitmap(
                        id = frame.toLong(),
                        globalBounds = GlobalBounds(0, 0, 512, 512),
                        bitmap = bitmapInfo.bitmap,
                        density = 1f,
                        isContextualImage = true,
                        imagePrivacy = ImagePrivacy.MASK_NONE,
                        asyncJobStatusCallback = fakeCallback
                    )
                }
            }
            fakeNativeWhileBlocked = Debug.getNativeHeapAllocatedSize()
        } finally {
            fakeRelease.countDown()
            fakeExecutor.shutdown()
            check(fakeExecutor.awaitTermination(60, TimeUnit.SECONDS))
            fakeNativeAfter = Debug.getNativeHeapAllocatedSize()
            composeRule.runOnIdle { fakeVisible.value = false }
            composeRule.waitForIdle()
            fakeBitmap.recycle()
        }

        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply {
                putLong("nativeBefore", fakeNativeBefore)
                putLong("nativeBlocked", fakeNativeWhileBlocked)
                putLong("nativeDrained", fakeNativeAfter)
            }
        )

        // Then
        assertThat(fakeCompleted.get()).isEqualTo(CAPTURE_COUNT)
        assertThat(fakeNativeWhileBlocked - fakeNativeBefore).isLessThan(80 * MIB)
        assertThat(fakeNativeAfter - fakeNativeBefore).isLessThan(16 * MIB)
    }

    @Test
    fun reuseUnchangedBitmapWhileCompressionIsBlocked() {
        // Given
        val fakeBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val fakeVisible = mutableStateOf(true)
        val fakeExecutor = Executors.newSingleThreadExecutor()
        val testedHelper = createCaptureHelper(fakeExecutor)
        val testedSemantics = SemanticsPainterCapture()
        val fakeWarmCapture = CountDownLatch(1)
        val fakeStarted = CountDownLatch(1)
        val fakeRelease = CountDownLatch(1)
        val fakeCompleted = AtomicInteger()
        composeRule.setContent {
            if (fakeVisible.value) Image(fakeBitmap.asImageBitmap(), "unchanged image", Modifier.testTag(IMAGE_TAG))
        }
        val fakeNode = composeRule.onNodeWithTag(IMAGE_TAG).fetchSemanticsNode()
        composeRule.runOnIdle {
            val bitmapInfo = checkNotNull(testedSemantics.resolve(fakeNode))
            testedHelper.createImageWireframeByBitmap(
                id = 0,
                globalBounds = GlobalBounds(0, 0, 512, 512),
                bitmap = bitmapInfo.bitmap,
                density = 1f,
                isContextualImage = true,
                imagePrivacy = ImagePrivacy.MASK_NONE,
                asyncJobStatusCallback = object : AsyncJobStatusCallback {
                    override fun jobStarted() = Unit
                    override fun jobFinished() = fakeWarmCapture.countDown()
                }
            )
        }
        assertThat(fakeWarmCapture.await(10, TimeUnit.SECONDS)).isTrue()
        fakeExecutor.execute {
            fakeStarted.countDown()
            check(fakeRelease.await(60, TimeUnit.SECONDS))
        }
        assertThat(fakeStarted.await(5, TimeUnit.SECONDS)).isTrue()
        val fakeNativeBefore = Debug.getNativeHeapAllocatedSize()
        var fakeNativeWhileBlocked = fakeNativeBefore
        var fakeFinishedWhileBlocked = 0

        // When
        try {
            repeat(CAPTURE_COUNT) { frame ->
                composeRule.runOnIdle {
                    val bitmapInfo =
                        checkNotNull(testedSemantics.resolve(fakeNode))
                    testedHelper.createImageWireframeByBitmap(
                        id = frame.toLong(),
                        globalBounds = GlobalBounds(0, 0, 512, 512),
                        bitmap = bitmapInfo.bitmap,
                        density = 1f,
                        isContextualImage = true,
                        imagePrivacy = ImagePrivacy.MASK_NONE,
                        asyncJobStatusCallback = countingCallback(fakeCompleted)
                    )
                }
            }
            fakeNativeWhileBlocked = Debug.getNativeHeapAllocatedSize()
            fakeFinishedWhileBlocked = fakeCompleted.get()
        } finally {
            fakeRelease.countDown()
            fakeExecutor.shutdown()
            check(fakeExecutor.awaitTermination(60, TimeUnit.SECONDS))
            composeRule.runOnIdle { fakeVisible.value = false }
            composeRule.waitForIdle()
            fakeBitmap.recycle()
        }

        // Then
        assertThat(fakeFinishedWhileBlocked).isEqualTo(CAPTURE_COUNT)
        assertThat(fakeNativeWhileBlocked - fakeNativeBefore).isLessThan(16 * MIB)
    }

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun captureHardwareBitmapWithoutRecyclingSource() {
        // When / Then
        assertBitmapCapture(Bitmap.Config.HARDWARE)
    }

    @Test
    fun captureAlpha8BitmapWithoutRecyclingSource() {
        // When / Then
        assertBitmapCapture(Bitmap.Config.ALPHA_8)
    }

    private fun assertBitmapCapture(config: Bitmap.Config) {
        val fakeSource = if (config == Bitmap.Config.HARDWARE) {
            val software = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            checkNotNull(software.copy(config, false)).also { software.recycle() }
        } else {
            Bitmap.createBitmap(64, 64, config)
        }
        val fakeVisible = mutableStateOf(true)
        val fakeFinished = CountDownLatch(1)
        val fakeExecutor = Executors.newSingleThreadExecutor()
        val testedHelper = createCaptureHelper(fakeExecutor)
        var result: MobileSegment.Wireframe? = null
        composeRule.setContent {
            if (fakeVisible.value) Image(fakeSource.asImageBitmap(), "native image", Modifier.testTag(IMAGE_TAG))
        }
        try {
            val fakeNode = composeRule.onNodeWithTag(IMAGE_TAG).fetchSemanticsNode()
            composeRule.runOnIdle {
                val info = checkNotNull(SemanticsPainterCapture().resolve(fakeNode))
                result = testedHelper.createImageWireframeByBitmap(
                    id = 0,
                    globalBounds = GlobalBounds(0, 0, 64, 64),
                    bitmap = info.bitmap,
                    density = 1f,
                    isContextualImage = true,
                    imagePrivacy = ImagePrivacy.MASK_NONE,
                    asyncJobStatusCallback = object : AsyncJobStatusCallback {
                        override fun jobStarted() = Unit
                        override fun jobFinished() = fakeFinished.countDown()
                    }
                )
            }
            assertThat(fakeFinished.await(10, TimeUnit.SECONDS)).isTrue()
            val image = result as MobileSegment.Wireframe.ImageWireframe
            assertThat(image.resourceId).isNotNull()
            assertThat(image.isEmpty).isFalse()
            assertThat(fakeSource.isRecycled).isFalse()
            assertThat(fakeSource.config).isEqualTo(config)
        } finally {
            fakeExecutor.shutdown()
            check(fakeExecutor.awaitTermination(60, TimeUnit.SECONDS))
            composeRule.runOnIdle { fakeVisible.value = false }
            composeRule.waitForIdle()
            fakeSource.recycle()
        }
    }

    private fun createCaptureHelper(executor: ExecutorService): ImageWireframeHelper = createImageWireframeHelper(
        context = InstrumentationRegistry.getInstrumentation().targetContext,
        resourceExecutor = executor,
        drawableExecutor = executor
    )

    private fun countingCallback(counter: AtomicInteger): AsyncJobStatusCallback = object : AsyncJobStatusCallback {
        override fun jobStarted() = Unit

        override fun jobFinished() {
            counter.incrementAndGet()
        }
    }

    private companion object {
        const val IMAGE_TAG = "replay-image"
        const val CAPTURE_COUNT = 120
        const val MIB = 1024L * 1024
    }
}
