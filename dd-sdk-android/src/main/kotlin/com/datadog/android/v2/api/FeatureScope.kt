/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

import com.datadog.android.v2.api.context.DatadogContext

/**
 * Represents a Datadog feature.
 */
interface FeatureScope {
    /**
     * Utility to write an event, asynchronously.
     * @param callback an operation called with an up-to-date [DatadogContext]
     * and an [EventBatchWriter]
     */
    fun withWriteContext(callback: (DatadogContext, EventBatchWriter) -> Unit)
}
