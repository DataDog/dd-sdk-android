/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

/**
 * Will persists the serialized EnrichedRecord in the allocated Session Replay caching location.
 */
interface SerializedRecordWriter {
    /**
     * Writes the serializedRecord to disk.
     * @param serializedRecord
     */
    fun write(serializedRecord: String)
}
