/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain.batching

import com.datadog.android.core.internal.domain.batching.processors.DataProcessor
import com.datadog.android.privacy.TrackingConsent

internal class DataProcessorFactory<T : Any>(
    private val permissionGrantedProcessorFactory:
        () -> DataProcessor<T>,
    private val permissionPendingProcessorFactory:
        () -> DataProcessor<T>,
    private val noOpProcessorFactory: () -> DataProcessor<T>
) {

    fun resolveProcessor(
        consent: TrackingConsent
    ): DataProcessor<T> {
        return when (consent) {
            TrackingConsent.GRANTED -> permissionGrantedProcessorFactory()
            TrackingConsent.PENDING -> permissionPendingProcessorFactory()
            else -> {
                noOpProcessorFactory()
            }
        }
    }
}
