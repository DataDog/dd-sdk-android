/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.stub

/**
 * Holds the data and metadata of an event written via the [StubSDKCore].
 * @param eventData the event data
 * @param eventMetadata the event metadata
 * @param batchMetadata the batch metadata
 */
data class StubEvent(
    val eventData: String,
    val eventMetadata: String,
    val batchMetadata: String
)
