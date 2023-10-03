/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api

import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.trace.TracingHeaderType
import okhttp3.HttpUrl

internal class ApproveAllFirstPartyHostHeaderTypeResolver : FirstPartyHostHeaderTypeResolver {

    override fun isEmpty(): Boolean = false

    override fun isFirstPartyUrl(url: HttpUrl): Boolean = true

    override fun isFirstPartyUrl(url: String): Boolean = true

    override fun headerTypesForUrl(url: String): Set<TracingHeaderType> = setOf(TracingHeaderType.DATADOG)

    override fun headerTypesForUrl(url: HttpUrl): Set<TracingHeaderType> = setOf(TracingHeaderType.DATADOG)

    override fun getAllHeaderTypes(): Set<TracingHeaderType> = setOf(TracingHeaderType.DATADOG)
}
