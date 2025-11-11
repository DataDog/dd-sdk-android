/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.instrumentation.network

import org.assertj.core.api.Assertions

class RequestInfoAssertions private constructor(private val info: RequestInfo) {

    fun hasUrl(url: String) = apply {
        Assertions.assertThat(info.url).isEqualTo(url)
    }

    fun hasMethod(method: String) = apply {
        Assertions.assertThat(info.method).isEqualTo(method)
    }

    fun hasHeader(key: String, value: String) = apply {
        Assertions.assertThat(info.headers.getValue(key).first()).isEqualTo(value)
    }

    fun hasContentType(contentType: String) = apply {
        Assertions.assertThat(info.contentType).isEqualTo(contentType)
    }

    fun <T> hasTag(type: Class<T>, tag: T) = apply {
        Assertions.assertThat(info.tag(type)).isEqualTo(tag)
    }

    companion object {
        fun assertThat(info: RequestInfo) = RequestInfoAssertions(info)
    }
}