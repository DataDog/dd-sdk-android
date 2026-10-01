/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.instrumentation.network

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class PeekableBodyInfoTest {

    @Test
    fun `M cap limit and delegate W request peekBody() { capability supported }`(
        @StringForgery fakePayload: String
    ) {
        // Given
        val fakeSnapshot = HttpBodySnapshot(fakePayload.toByteArray())
        val testedInfo = PeekableRequestInfo(fakeSnapshot)
        val fakeLimit = Long.MAX_VALUE

        // When
        val result = (testedInfo as HttpRequestInfo).peekBody(fakeLimit)

        // Then
        assertThat(result).isSameAs(fakeSnapshot)
        assertThat(testedInfo.lastMaxBytes).isEqualTo(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)
    }

    @Test
    fun `M return null W request peekBody() { capability unsupported }`() {
        // Given
        val testedInfo: HttpRequestInfo = StubRequestInfo()

        // When
        val result = testedInfo.peekBody()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M delegate W response peekBody() { capability supported }`(
        @StringForgery fakePayload: String,
        @LongForgery(min = 1, max = HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES) fakeLimit: Long
    ) {
        // Given
        val fakeSnapshot = HttpBodySnapshot(fakePayload.toByteArray())
        val testedInfo = PeekableResponseInfo(fakeSnapshot)

        // When
        val result = (testedInfo as HttpResponseInfo).peekBody(fakeLimit)

        // Then
        assertThat(result).isSameAs(fakeSnapshot)
        assertThat(testedInfo.lastMaxBytes).isEqualTo(fakeLimit)
    }

    @Test
    fun `M return null W response peekBody() { capability unsupported }`() {
        // Given
        val testedInfo: HttpResponseInfo = StubResponseInfo()

        // When
        val result = testedInfo.peekBody()

        // Then
        assertThat(result).isNull()
    }

    private open class StubRequestInfo : HttpRequestInfo {
        override val url: String = ""
        override val headers: Map<String, List<String>> = emptyMap()
        override val contentType: String? = null
        override val method: String = "GET"
        override fun contentLength(): Long? = null
    }

    private class PeekableRequestInfo(
        private val snapshot: HttpBodySnapshot
    ) : StubRequestInfo(), PeekableBodyInfo {
        var lastMaxBytes: Long? = null

        override fun peekBody(maxBytes: Long): HttpBodySnapshot {
            lastMaxBytes = maxBytes
            return snapshot
        }
    }

    private open class StubResponseInfo : HttpResponseInfo {
        override val url: String = ""
        override val statusCode: Int = 200
        override val headers: Map<String, List<String>> = emptyMap()
        override val contentType: String? = null
        override val contentLength: Long? = null
        override val request: HttpRequestInfo? = null
    }

    private class PeekableResponseInfo(
        private val snapshot: HttpBodySnapshot
    ) : StubResponseInfo(), PeekableBodyInfo {
        var lastMaxBytes: Long? = null

        override fun peekBody(maxBytes: Long): HttpBodySnapshot {
            lastMaxBytes = maxBytes
            return snapshot
        }
    }
}
