/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.stub

import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.trace.TracingHeaderType
import okhttp3.HttpUrl

/**
 * Stubbed implementation of [FirstPartyHostHeaderTypeResolver], which is an
 * allow all, trace all implementation (using the W3C TraceContext headers).
 */
class StubFirstPartyHostHeaderTypeResolver :
    FirstPartyHostHeaderTypeResolver {

    // region FirstPartyHostHeaderTypeResolver

    override fun isEmpty(): Boolean = false

    override fun isFirstPartyUrl(url: HttpUrl): Boolean = true

    override fun isFirstPartyUrl(url: String): Boolean = true

    override fun headerTypesForUrl(url: String): Set<TracingHeaderType> = setOf(TracingHeaderType.TRACECONTEXT)

    override fun headerTypesForUrl(url: HttpUrl): Set<TracingHeaderType> = setOf(TracingHeaderType.TRACECONTEXT)

    override fun getAllHeaderTypes(): Set<TracingHeaderType> = setOf(TracingHeaderType.TRACECONTEXT)

    // endregion
}
