/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

/**
 * Discovers roots and synchronously inspects Android/Compose state on the main thread. Concrete
 * walkers must use [CaptureGenerationContext.shouldContinue] between bounded operations. Native
 * View state must be copied in one UI-thread turn: resuming live traversal across turns can mix
 * different frames. Snapshot construction runs independently of live UI state.
 */
internal fun interface CapturedSnapshotProducer {
    fun capture(context: CaptureGenerationContext): CapturedSnapshotInput?
}
