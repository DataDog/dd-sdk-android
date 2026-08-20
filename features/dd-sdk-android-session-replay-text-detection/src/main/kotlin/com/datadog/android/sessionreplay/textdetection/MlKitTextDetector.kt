/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.textdetection

import android.graphics.Bitmap
import com.datadog.android.sessionreplay.recorder.privacy.TextDetectionOutcome
import com.datadog.android.sessionreplay.recorder.privacy.TextDetector
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [TextDetector] backed by ML Kit's on-device text recognizer. Only ever consumes bounding boxes
 * from the recognition result - [com.google.mlkit.vision.text.Text.TextBlock.getText] is never
 * read, so recognized text can never flow through this class. A hung or slow recognition is
 * bounded by [timeoutMs], after which the result resolves to
 * [TextDetectionOutcome.Unavailable] exactly as a thrown exception would.
 */
internal class MlKitTextDetector(
    private val callbackExecutor: Executor,
    private val timeoutScheduler: ScheduledExecutorService,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
) : TextDetector {

    override fun detectTextRegions(bitmap: Bitmap, onComplete: (TextDetectionOutcome) -> Unit) {
        val alreadyCompleted = AtomicBoolean(false)
        val complete = { outcome: TextDetectionOutcome ->
            if (alreadyCompleted.compareAndSet(false, true)) onComplete(outcome)
        }

        timeoutScheduler.schedule(
            { complete(TextDetectionOutcome.Unavailable) },
            timeoutMs,
            TimeUnit.MILLISECONDS
        )

        val noRotation = 0
        val image = try {
            InputImage.fromBitmap(bitmap, noRotation)
        } catch (e: IllegalArgumentException) {
            complete(TextDetectionOutcome.Unavailable)
            return
        }

        recognizer.process(image)
            .addOnSuccessListener(callbackExecutor) { visionText ->
                val regions = visionText.textBlocks.mapNotNull { it.boundingBox }
                complete(TextDetectionOutcome.Detected(regions))
            }
            .addOnFailureListener(callbackExecutor) {
                complete(TextDetectionOutcome.Unavailable)
            }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 2_000L
    }
}
