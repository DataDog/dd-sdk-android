/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.instrumentation.network

import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions

class RequestInfoAssert private constructor(actual: RequestInfo) :
    AbstractObjectAssert<RequestInfoAssert, RequestInfo>(actual, RequestInfoAssert::class.java) {

    fun hasUrl(url: String) = apply {
        Assertions.assertThat(actual.url).isEqualTo(url)
    }

    fun hasMethod(method: String) = apply {
        Assertions.assertThat(actual.method).isEqualTo(method)
    }

    fun hasHeader(key: String, value: String) = apply {
        Assertions.assertThat(actual.headers.getValue(key).first()).isEqualTo(value)
    }

    fun hasContentType(contentType: String) = apply {
        Assertions.assertThat(actual.contentType).isEqualTo(contentType)
    }

    fun <T> hasTag(type: Class<T>, tag: T) = apply {
        Assertions.assertThat(actual.tag(type)).isEqualTo(tag)
    }

    companion object Companion {
        fun assertThat(info: RequestInfo) = RequestInfoAssert(info)
    }
}
