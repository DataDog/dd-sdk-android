/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.storage

import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord

/**
 * Will persists the serialized EnrichedRecord in the allocated Session Replay caching location.
 */
internal interface RecordWriter {
    /**
     * Writes the record to disk.
     * @param record to write
     */
    fun write(record: EnrichedRecord)
}
