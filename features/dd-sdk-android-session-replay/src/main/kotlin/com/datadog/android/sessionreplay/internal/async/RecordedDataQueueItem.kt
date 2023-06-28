/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import com.datadog.android.sessionreplay.internal.processor.RumContextData

internal abstract class RecordedDataQueueItem(
    internal val rumContextData: RumContextData
) {
    internal abstract fun isValid(): Boolean
    internal abstract fun isReady(): Boolean
}
