/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin

import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class RecordedRequestAssert(actual: RecordedRequest?) :
    AbstractObjectAssert<RecordedRequestAssert, RecordedRequest>(
        actual,
        RecordedRequestAssert::class.java
    ) {

    val bodyContentUtf8 = actual?.body?.readUtf8()

    fun containsFormData(name: String, value: String): RecordedRequestAssert {
        isNotNull()
        assertThat(bodyContentUtf8)
            .contains(
                "Content-Disposition: form-data; name=\"$name\"\r\n" +
                    "Content-Length: ${value.length}\r\n\r\n$value\r\n"
            )
        return this
    }

    fun containsMultipartFile(
        name: String,
        fileName: String,
        fileContent: String
    ): RecordedRequestAssert {
        isNotNull()
        assertThat(bodyContentUtf8)
            .contains(
                "Content-Disposition: form-data; name=\"$name\"; filename=\"$fileName\"\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: ${fileContent.length}\r\n\r\n$fileContent"
            )
        return this
    }

    fun hasMethod(expected: String): RecordedRequestAssert {
        isNotNull()
        assertThat(actual.method)
            .isEqualTo(expected)
        return this
    }

    companion object {
        fun assertThat(actual: RecordedRequest?): RecordedRequestAssert {
            return RecordedRequestAssert(actual)
        }
    }
}
