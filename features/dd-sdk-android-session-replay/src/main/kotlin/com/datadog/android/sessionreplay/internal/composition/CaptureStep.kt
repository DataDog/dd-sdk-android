/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

/**
 * The outcome of one bounded main-thread slice of a [CapturedSnapshotProducer]. A producer that
 * can't finish within one slice - see [CaptureGenerationContext.shouldYield] - returns [Yielded]
 * instead of blocking through to completion, so the orchestrator can hand the main thread back to
 * the Looper between slices rather than holding it for the whole generation in one continuous run.
 */
internal sealed interface CaptureStep<out T> {
    data class Done<T>(val value: T) : CaptureStep<T>

    /** [resume] continues this same unit of work from exactly where it left off. */
    data class Yielded<T>(val resume: () -> CaptureStep<T>) : CaptureStep<T>
}
