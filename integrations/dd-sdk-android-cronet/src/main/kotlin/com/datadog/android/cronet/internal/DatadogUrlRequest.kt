/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.rum.internal.net.RumResourceInstrumentation
import org.chromium.net.UrlRequest
import java.nio.ByteBuffer

internal class DatadogUrlRequest(
    private val info: HttpRequestInfo,
    private val delegate: UrlRequest,
    private val rumResourceInstrumentation: RumResourceInstrumentation
) : UrlRequest() {

    override fun start() {
        rumResourceInstrumentation.startResource(info)
        rumResourceInstrumentation.sendWaitForResourceTimingEvent(info)
        delegate.start()
    }

    override fun followRedirect() = delegate.followRedirect()

    override fun read(buffer: ByteBuffer?) = delegate.read(buffer)

    override fun cancel() = delegate.cancel()

    override fun isDone(): Boolean = delegate.isDone

    override fun getStatus(listener: StatusListener?) = delegate.getStatus(listener)
}
