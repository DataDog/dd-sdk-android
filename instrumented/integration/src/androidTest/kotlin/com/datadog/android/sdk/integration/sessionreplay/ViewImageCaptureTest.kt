/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Debug
import android.view.View
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.recorder.resources.DefaultDrawableCopier
import com.datadog.android.sessionreplay.testing.createImageWireframeHelper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
internal class ViewImageCaptureTest {
    @Test
    fun boundNativeMemoryWhenDrawableWorkerStalls() {
        // Given
        val fakeFixture = CaptureFixture()
        val fakeDraws = AtomicInteger()
        val fakeRelease = fakeFixture.blockDrawing()
        val fakeBefore = Debug.getNativeHeapAllocatedSize()
        var fakeBlocked = fakeBefore
        var fakeDrained = fakeBefore

        // When
        try {
            repeat(CAPTURE_COUNT) { fakeFixture.capture(CountingDrawable(fakeDraws)) }
            fakeFixture.awaitRequestsQueued()
            fakeBlocked = Debug.getNativeHeapAllocatedSize()
        } finally {
            fakeFixture.finish(fakeRelease)
            fakeDrained = Debug.getNativeHeapAllocatedSize()
        }

        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply {
                putLong("viewNativeBefore", fakeBefore)
                putLong("viewNativeBlocked", fakeBlocked)
                putLong("viewNativeDrained", fakeDrained)
            }
        )

        // Then
        assertThat(fakeFixture.completed.get()).isEqualTo(CAPTURE_COUNT)
        assertThat(fakeBlocked - fakeBefore).isLessThan(80 * MIB)
        assertThat(fakeDrained - fakeBefore).isLessThan(16 * MIB)
        fakeFixture.assertRecordedImage()
    }

    @Test
    fun preserveApplicationBitmapWhenScalingDrawable() {
        // Given
        val fakeFixture = CaptureFixture()
        val fakeSource = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888)
        fakeSource.eraseColor(Color.RED)
        val fakeDrawable =
            BitmapDrawable(InstrumentationRegistry.getInstrumentation().targetContext.resources, fakeSource)
        val fakeRelease = CountDownLatch(0)

        // When
        try {
            fakeFixture.capture(fakeDrawable)
            fakeFixture.finish(fakeRelease)

            // Then
            assertThat(fakeFixture.completed.get()).isEqualTo(1)
            assertThat(fakeSource.isRecycled).isFalse()
            assertThat(fakeSource.getPixel(0, 0)).isEqualTo(Color.RED)
            fakeFixture.assertRecordedImage(expectedEdge = null)
        } finally {
            fakeFixture.finish(fakeRelease)
            fakeSource.recycle()
        }
    }

    @Test
    fun releaseLargeDrawablePixelsAfterEachCapture() {
        // Given
        val fakeFixture = CaptureFixture()
        val fakeDraws = AtomicInteger()
        val fakeBefore = Debug.getNativeHeapAllocatedSize()
        var fakePeakGrowth = 0L
        val fakeRelease = CountDownLatch(0)

        // When
        try {
            repeat(32) {
                fakeFixture.capture(CountingDrawable(fakeDraws, 2048), edge = 2048)
                fakeFixture.awaitRequestsQueued()
                fakeFixture.awaitDrawing()
                fakePeakGrowth = maxOf(fakePeakGrowth, Debug.getNativeHeapAllocatedSize() - fakeBefore)
            }
        } finally {
            fakeFixture.finish(fakeRelease)
        }

        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putLong("viewCompletedNativePeakGrowth", fakePeakGrowth) }
        )

        // Then
        assertThat(fakeFixture.completed.get()).isEqualTo(32)
        assertThat(fakeDraws.get()).isEqualTo(32)
        assertThat(fakePeakGrowth).isLessThan(16 * MIB)
    }

    private class CaptureFixture {
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        private val context = instrumentation.targetContext
        private val capturedImages = CopyOnWriteArrayList<ByteArray>()
        private val resourceExecutor = Executors.newSingleThreadExecutor()
        private val drawingExecutor = Executors.newSingleThreadExecutor()
        private lateinit var view: View
        private val helper: ImageWireframeHelper
        val completed = AtomicInteger()
        private val drawableSources = mutableListOf<Drawable>()

        init {
            instrumentation.runOnMainSync { view = ImageView(context) }
            helper = createImageWireframeHelper(
                context = context,
                resourceExecutor = resourceExecutor,
                drawableExecutor = drawingExecutor,
                onResource = { capturedImages.add(it) }
            )
        }

        fun blockDrawing(): CountDownLatch {
            val fakeStarted = CountDownLatch(1)
            val fakeRelease = CountDownLatch(1)
            drawingExecutor.execute {
                fakeStarted.countDown()
                check(fakeRelease.await(60, TimeUnit.SECONDS))
            }
            check(fakeStarted.await(5, TimeUnit.SECONDS))
            return fakeRelease
        }

        fun capture(
            drawable: Drawable,
            callback: AsyncJobStatusCallback = object : AsyncJobStatusCallback {
                override fun jobStarted() = Unit
                override fun jobFinished() { completed.incrementAndGet() }
            },
            edge: Int = EDGE
        ) {
            instrumentation.runOnMainSync {
                drawableSources.add(drawable)
                helper.createImageWireframeByDrawable(
                    view = view,
                    imagePrivacy = ImagePrivacy.MASK_NONE,
                    currentWireframeIndex = 0,
                    x = 0,
                    y = 0,
                    width = edge,
                    height = edge,
                    usePIIPlaceholder = true,
                    drawable = drawable,
                    drawableCopier = DefaultDrawableCopier(),
                    asyncJobStatusCallback = callback,
                    customResourceIdCacheKey = null
                )
            }
        }

        fun awaitRequestsQueued() {
            resourceExecutor.submit {}.get(10, TimeUnit.SECONDS)
        }

        fun awaitDrawing() {
            drawingExecutor.submit {}.get(15, TimeUnit.SECONDS)
        }

        fun finish(release: CountDownLatch) {
            release.countDown()
            resourceExecutor.shutdown()
            check(resourceExecutor.awaitTermination(30, TimeUnit.SECONDS))
            drawingExecutor.shutdown()
            check(drawingExecutor.awaitTermination(30, TimeUnit.SECONDS))
            // Borrowed fixture inputs must remain alive until drawing has completed.
            drawableSources.forEach { check(it.intrinsicWidth > 0) }
        }

        fun assertRecordedImage(expectedEdge: Int? = EDGE) {
            assertThat(capturedImages).isNotEmpty()
            val bytes = capturedImages.first()
            val bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            try {
                assertThat(bitmap.width).isPositive()
                assertThat(bitmap.height).isPositive()
                if (expectedEdge != null) {
                    assertThat(bitmap.width).isEqualTo(expectedEdge)
                    assertThat(bitmap.height).isEqualTo(expectedEdge)
                }
                assertThat(Color.red(bitmap.getPixel(0, 0))).isGreaterThan(200)
                assertThat(Color.alpha(bitmap.getPixel(0, 0))).isEqualTo(255)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private class CountingDrawable(private val draws: AtomicInteger, private val edge: Int = EDGE) : Drawable() {
        override fun draw(canvas: Canvas) {
            draws.incrementAndGet()
            canvas.drawColor(Color.RED)
        }
        override fun getIntrinsicWidth(): Int = edge
        override fun getIntrinsicHeight(): Int = edge
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
        override fun getConstantState(): ConstantState = object : ConstantState() {
            override fun newDrawable(): Drawable = CountingDrawable(draws, edge)
            override fun newDrawable(res: Resources?): Drawable = CountingDrawable(draws, edge)
            override fun getChangingConfigurations(): Int = 0
        }
    }

    private companion object {
        const val EDGE = 512
        const val CAPTURE_COUNT = 120
        const val MIB = 1024L * 1024
    }
}
