/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.textdetection.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import com.datadog.android.sessionreplay.recorder.privacy.TextDetectionOutcome
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal class MlKitSmallInputTest {
    @ParameterizedTest
    @CsvSource("100,5,100,32", "5,100,32,100", "100,100,100,100")
    fun `M pad only the detector input W detectTextRegions`(
        width: Int,
        height: Int,
        inputWidth: Int,
        inputHeight: Int
    ) {
        val fixture = Fixture(width, height)
        fixture.withFactories {
            // When
            detector.detectTextRegions(source, outcomes::add)

            // Then
            imageFactory.verify { InputImage.fromBitmap(input, 0) }
            if (paddedInput) {
                bitmapFactory.verify { Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888) }
                verify(padded, never()).recycle()
                verify(canvases.constructed().single()).drawColor(Color.TRANSPARENT)
                verify(canvases.constructed().single()).drawBitmap(source, 0f, 0f, null)
            } else {
                bitmapFactory.verifyNoInteractions()
            }
            finishRecognition()
            if (paddedInput) verify(padded).recycle()
            verify(source, never()).recycle()
        }
    }

    @Test
    fun `M keep input alive after timeout W ML Kit still processes`() {
        val fixture = Fixture(100, 5)
        fixture.withFactories {
            // Given
            detector.detectTextRegions(source, outcomes::add)
            val timeout = argumentCaptor<Runnable>()
            verify(scheduler).schedule(timeout.capture(), eq(2_000L), eq(TimeUnit.MILLISECONDS))

            // When
            timeout.firstValue.run()

            // Then
            assertThat(outcomes).containsExactly(TextDetectionOutcome.Unavailable)
            verify(padded, never()).recycle()
            deliverText(emptyList())
            assertThat(outcomes).containsExactly(TextDetectionOutcome.Unavailable)
            finishRecognition()
            verify(padded).recycle()
        }
    }

    @Test
    fun `M release padding W InputImage rejects the bitmap`() {
        val fixture = Fixture(100, 5)
        fixture.withFactories {
            // Given
            imageFactory.`when`<InputImage> { InputImage.fromBitmap(padded, 0) }
                .thenThrow(IllegalArgumentException("invalid image"))

            // When
            detector.detectTextRegions(source, outcomes::add)

            // Then
            assertThat(outcomes).containsExactly(TextDetectionOutcome.Unavailable)
            verify(padded).recycle()
            verify(recognizer, never()).process(any<InputImage>())
        }
    }

    @Test
    fun `M propagate allocation failure W padding { no OOM catch }`() {
        val fixture = Fixture(100, 5)
        fixture.withFactories {
            // Given
            val failure = OutOfMemoryError("simulated")
            bitmapFactory.`when`<Bitmap> { Bitmap.createBitmap(100, 32, Bitmap.Config.ARGB_8888) }.thenThrow(failure)

            // When + Then
            assertThatThrownBy { detector.detectTextRegions(source, outcomes::add) }.isSameAs(failure)
            verify(source, never()).recycle()
        }
    }

    @Test
    fun `M clip text boxes to source coordinates W OCR includes padded space`() {
        val fixture = Fixture(100, 5)
        fixture.withFactories {
            detector.detectTextRegions(source, outcomes::add)
            val overlapping = Rect().apply { left = 90; top = 0; right = 110; bottom = 10 }
            val paddingOnly = Rect().apply { left = 0; top = 8; right = 50; bottom = 20 }
            // Android's mockable jar has no Rect constructor implementation; preserve its real field semantics.
            mockConstruction(Rect::class.java) { rect, context ->
                rect.left = context.arguments()[0] as Int
                rect.top = context.arguments()[1] as Int
                rect.right = context.arguments()[2] as Int
                rect.bottom = context.arguments()[3] as Int
            }.use {
                // When
                deliverText(listOf(overlapping, paddingOnly))
            }

            // Then
            val detected = outcomes.single() as TextDetectionOutcome.Detected
            assertThat(detected.regions).hasSize(1)
            val region = detected.regions.single()
            assertThat(listOf(region.left, region.top, region.right, region.bottom)).containsExactly(90, 0, 100, 5)
            finishRecognition()
        }
    }

    private class Fixture(val width: Int, val height: Int) {
        val source: Bitmap = mock()
        val padded: Bitmap = mock()
        val image: InputImage = mock()
        val task: Task<Text> = mock()
        val recognizer: TextRecognizer = mock()
        val scheduler: ScheduledExecutorService = mock()
        val executor: Executor = Executor { it.run() }
        val outcomes = mutableListOf<TextDetectionOutcome>()
        val detector = MlKitTextDetector(executor, scheduler, recognizer = lazy { recognizer })
        val paddedInput = width < 32 || height < 32
        val input get() = if (paddedInput) padded else source
        lateinit var bitmapFactory: MockedStatic<Bitmap>
        lateinit var imageFactory: MockedStatic<InputImage>
        lateinit var canvases: MockedConstruction<Canvas>

        init {
            whenever(source.width).thenReturn(width)
            whenever(source.height).thenReturn(height)
            whenever(recognizer.process(image)).thenReturn(task)
            whenever(task.addOnCompleteListener(eq(executor), any())).thenReturn(task)
            whenever(task.addOnSuccessListener(eq(executor), any())).thenReturn(task)
            whenever(task.addOnFailureListener(eq(executor), any())).thenReturn(task)
        }

        fun withFactories(block: Fixture.() -> Unit) {
            mockStatic(Bitmap::class.java).use { bitmaps ->
                bitmapFactory = bitmaps
                bitmaps.`when`<Bitmap> {
                    Bitmap.createBitmap(width.coerceAtLeast(32), height.coerceAtLeast(32), Bitmap.Config.ARGB_8888)
                }.thenReturn(padded)
                mockStatic(InputImage::class.java).use { images ->
                    imageFactory = images
                    images.`when`<InputImage> { InputImage.fromBitmap(input, 0) }.thenReturn(image)
                    mockConstruction(Canvas::class.java).use { construction ->
                        canvases = construction
                        block()
                    }
                }
            }
        }

        fun finishRecognition() {
            val completion = argumentCaptor<OnCompleteListener<Text>>()
            verify(task).addOnCompleteListener(eq(executor), completion.capture())
            completion.firstValue.onComplete(task)
        }

        fun deliverText(boxes: List<Rect>) {
            val text: Text = mock()
            val blocks = boxes.map { box ->
                mock<Text.TextBlock>().also { whenever(it.boundingBox).thenReturn(box) }
            }
            whenever(text.textBlocks).thenReturn(blocks)
            val success = argumentCaptor<OnSuccessListener<Text>>()
            verify(task).addOnSuccessListener(eq(executor), success.capture())
            success.firstValue.onSuccess(text)
        }
    }
}
