/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.textdetection.internal

import android.graphics.Bitmap
import com.datadog.android.sessionreplay.recorder.privacy.TextDetectionOutcome
import com.datadog.android.sessionreplay.recorder.privacy.TextDetector
import com.datadog.android.sessionreplay.textdetection.TextDetectionExtensionSupport
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
 *
 * [recognizer] is deliberately a [Lazy], not constructed eagerly: [TextRecognition.getClient]
 * triggers Google Play Services' Dynamite module loading (extracting/loading the on-device OCR
 * native libraries and models), which can block for a second or more. An eager default parameter
 * value is evaluated at construction time - since [TextDetectionExtensionSupport] constructs its
 * [MlKitTextDetector] in a property initializer, that used to mean this cost was paid
 * synchronously on the main thread during `Datadog.initialize()`, delaying SDK startup enough to
 * disrupt RUM/Session Replay's own session-sampling timing. Deferring it to first real use (via
 * `by lazy`) moves that cost off the startup path entirely, onto whichever background thread
 * first calls [detectTextRegions] - already how this pipeline's pixel captures are processed.
 */
internal class MlKitTextDetector(
    private val callbackExecutor: Executor,
    private val timeoutScheduler: ScheduledExecutorService,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    @Suppress("UnsafeThirdPartyFunctionCall") // getClient's failure modes are Play Services setup
    // issues (missing/outdated Play Services), which are outside this class's control and are
    // surfaced as a normal recognition failure via the try/catch and timeout below, not thrown here.
    private val recognizer: Lazy<TextRecognizer> =
        lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
) : TextDetector {

    @Suppress("UnsafeThirdPartyFunctionCall", "SwallowedException")
    // schedule/fromBitmap/process/mapNotNull/addOnSuccessListener/addOnFailureListener can only
    // throw for programmer errors (bad arguments, shutdown executor) that would be bugs here, not
    // recoverable conditions; the IllegalArgumentException from fromBitmap is deliberately
    // swallowed since it already resolves to TextDetectionOutcome.Unavailable, same as a timeout.
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

        recognizer.value.process(image)
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
