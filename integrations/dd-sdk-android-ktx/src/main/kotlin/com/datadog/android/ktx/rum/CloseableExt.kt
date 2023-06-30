/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ktx.rum

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumErrorSource
import java.io.Closeable

internal const val CLOSABLE_ERROR_MESSAGE = "Error while using the closeable"

/**
 * Executes the given [block] function on this [Closeable] instance
 * and then closes it down correctly whether an exception
 * is thrown or not.
 * This extension works exactly as the [Closeable.use] extension and in case the [block] will throw
 * any exception this will be intercepted and propagated as a Rum error event.
 * @param T a [Closeable] type
 * @param R the type returned by the block operation
 * @param sdkCore the SDK instance to use. If not provided, default instance will be used.
 * @param block a function to process this [Closeable] resource.
 * @return the result of [block] function invoked on this resource.
 */
@Suppress("TooGenericExceptionCaught")
fun <T : Closeable, R> T.useMonitored(sdkCore: SdkCore = Datadog.getInstance(), block: (T) -> R): R {
    try {
        return block(this)
    } catch (e: Throwable) {
        handleError(e, sdkCore)
        throw e
    } finally {
        try {
            close()
        } catch (closeException: Throwable) {
            handleError(closeException, sdkCore)
        }
    }
}

private fun handleError(throwable: Throwable, sdkCore: SdkCore) {
    GlobalRumMonitor.get(sdkCore).addError(CLOSABLE_ERROR_MESSAGE, RumErrorSource.SOURCE, throwable, emptyMap())
}
