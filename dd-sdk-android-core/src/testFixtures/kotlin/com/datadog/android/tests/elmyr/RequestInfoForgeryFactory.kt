/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tests.elmyr

import com.datadog.android.api.instrumentation.network.RequestInfo
import com.datadog.android.core.internal.net.HttpSpec
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class RequestInfoForgeryFactory : ForgeryFactory<RequestInfo> {
    override fun getForgery(forge: Forge): RequestInfo {
        return StubRequestInfo(
            url = forge.aStringMatching("https://[a-z0-9]+\\.com"),
            headers = forge.aMap { aString() to aList { aString() } },
            contentType = forge.anAlphabeticalString(),
            method = forge.anElementFrom(HttpSpec.Method.values()),
            contentLength = forge.aNullable { aLong(min = 0) },
            tags = forge.aMap { anAlphabeticalString() to anAlphabeticalString() }
        )
    }

    private data class StubRequestInfo(
        override val url: String,
        override val headers: Map<String, List<String>>,
        override val contentType: String?,
        override val method: String,
        private val contentLength: Long?,
        private val tags: Map<Any, Any?>
    ) : RequestInfo {

        @Suppress("UNCHECKED_CAST")
        override fun <T> tag(type: Class<out T>): T? = tags[type] as? T

        override fun contentLength(): Long? = contentLength
    }
}
