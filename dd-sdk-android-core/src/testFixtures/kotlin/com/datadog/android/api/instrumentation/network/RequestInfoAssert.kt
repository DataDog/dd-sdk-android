/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.instrumentation.network

import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

class RequestInfoAssert private constructor(actual: HttpRequestInfo) :
    AbstractObjectAssert<RequestInfoAssert, HttpRequestInfo>(actual, RequestInfoAssert::class.java) {

    fun hasUrl(url: String) = apply {
        assertThat(actual.url)
            .overridingErrorMessage("Expected URL $url but was ${actual.url}")
            .isEqualTo(url)
    }

    fun hasMethod(method: String) = apply {
        assertThat(actual.method)
            .overridingErrorMessage("Expected method $method but was ${actual.method}")
            .isEqualTo(method)
    }

    fun hasHeader(key: String, value: String) = apply {
        assertThat(actual.headers.getValue(key))
            .overridingErrorMessage("Expected header $key=$value but was ${actual.headers}")
            .isEqualTo(listOf(value))
    }

    fun hasContentType(contentType: String) = apply {
        assertThat(actual.contentType)
            .overridingErrorMessage("Expected content type $contentType but was ${actual.contentType}")
            .isEqualTo(contentType)
    }

    fun <T> hasTag(type: Class<T>, tag: T) = apply {
        assertThat(actual.tag(type))
            .overridingErrorMessage("Expected tag $tag but was ${actual.tag(type)}")
            .isEqualTo(tag)
    }

    companion object Companion {
        fun assertThat(info: HttpRequestInfo) = RequestInfoAssert(info)
    }
}
