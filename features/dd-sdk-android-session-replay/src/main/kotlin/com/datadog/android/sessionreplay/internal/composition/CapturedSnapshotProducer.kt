/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

/**
 * Discovers roots and synchronously inspects Android/Compose state on the main thread. Concrete
 * walkers must use [CaptureGenerationContext.shouldContinue] between bounded operations and may
 * return a contract-provided placeholder or cached resource before the deadline.
 */
internal fun interface CapturedSnapshotProducer {
    fun capture(context: CaptureGenerationContext): CapturedFullSnapshot?
}
