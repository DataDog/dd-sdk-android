/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import com.datadog.android.sessionreplay.internal.processor.RecordedQueuedItemContext

internal abstract class RecordedDataQueueItem(
    internal val recordedQueuedItemContext: RecordedQueuedItemContext,
    internal val creationTimeStampInNs: Long = System.nanoTime()
) {
    internal abstract fun isValid(): Boolean

    internal abstract fun isReady(): Boolean
}
