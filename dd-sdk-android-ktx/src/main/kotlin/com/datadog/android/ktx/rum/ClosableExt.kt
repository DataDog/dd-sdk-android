/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ktx.rum

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import java.io.Closeable

internal const val CLOSABLE_ERROR_NESSAGE = "Error while using the closeable"

/**
 * Executes the given [block] function on this [Closeable] instance
 * and then closes it down correctly whether an exception
 * is thrown or not.
 * This extension works exactly as the [Closeable.use] extension and in case the [block] will throw
 * any exception this will be intercepted and propagated as a Rum error event.
 * @param block a function to process this [Closeable] resource.
 * @return the result of [block] function invoked on this resource.
 */
@Suppress("TooGenericExceptionCaught")
fun <T : Closeable, R> T.useMonitored(block: (T) -> R): R {
    try {
        return block(this)
    } catch (e: Throwable) {
        handleError(e)
        throw e
    } finally {
        try {
            close()
        } catch (closeException: Throwable) {
            handleError(closeException)
        }
    }
}

private fun handleError(throwable: Throwable) {
    GlobalRum.get().addError(CLOSABLE_ERROR_NESSAGE, RumErrorSource.SOURCE, throwable, emptyMap())
}
