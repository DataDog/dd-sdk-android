/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ktx.coroutine

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

internal const val ERROR_FLOW: String = "Coroutine Flow error"

/**
 *  Returns a [Flow] that will send a RUM Error event if this [Flow] emits an error.
 *  Note that the error will also be emitted by the returned [Flow].
 */
@Suppress("TooGenericExceptionCaught")
fun <T> Flow<T>.sendErrorToDatadog(): Flow<T> {
    return flow {
        try {
            collect { value -> emit(value) }
        } catch (e: Throwable) {
            GlobalRum.get()
                .addError(
                    ERROR_FLOW,
                    RumErrorSource.SOURCE,
                    e,
                    emptyMap()
                )
            throw e
        }
    }
}
