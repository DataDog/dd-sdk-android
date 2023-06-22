/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rx

import com.datadog.android.Datadog
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.v2.api.SdkCore
import io.reactivex.rxjava3.functions.Consumer

/**
 * Provides an implementation of [Consumer<Throwable>] already set up to send relevant information
 * to Datadog.
 *
 * It will automatically send RUM error events whenever a RxJava Stream throws any [Exception].
 *
 * @param sdkCore the SDK instance to forward the errors to. If not provided, default instance
 * will be used.
 */
class DatadogRumErrorConsumer @JvmOverloads constructor(
    private val sdkCore: SdkCore = Datadog.getInstance()
) : Consumer<Throwable> {

    /** @inheritDoc */
    override fun accept(error: Throwable) {
        GlobalRumMonitor.get(sdkCore).addError(REQUEST_ERROR_MESSAGE, RumErrorSource.SOURCE, error, emptyMap())
    }

    internal companion object {
        internal const val REQUEST_ERROR_MESSAGE = "RxJava stream error"
    }
}
