/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.textdetection.internal

import com.google.mlkit.vision.text.TextRecognizer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService

@ExtendWith(MockitoExtension::class)
internal class MlKitTextDetectorTest {

    private val mockCallbackExecutor: Executor = Executor { it.run() }
    private val mockTimeoutScheduler: ScheduledExecutorService = mock()

    @Test
    fun `M not construct the recognizer W initialized`() {
        // Given: an eager default parameter (the pre-fix shape) evaluates at construction time -
        // TextRecognition.getClient() triggers Google Play Services' Dynamite module loading,
        // which blocked the main thread for a second-plus during Datadog.initialize() and
        // disrupted RUM/Session Replay's own session-sampling timing when this ran eagerly.
        // detectTextRegions() itself can't be exercised in a plain JVM unit test - ML Kit's
        // InputImage.fromBitmap() requires a real Android MlKitContext, unavailable without
        // instrumentation - so this only verifies the fix's actual guarantee: no construction
        // work happens before the first real use.
        var recognizerConstructed = false
        val lazyRecognizer = lazy {
            recognizerConstructed = true
            mock<TextRecognizer>()
        }

        // When
        MlKitTextDetector(
            callbackExecutor = mockCallbackExecutor,
            timeoutScheduler = mockTimeoutScheduler,
            recognizer = lazyRecognizer
        )

        // Then
        assertThat(recognizerConstructed).isFalse()
    }
}
