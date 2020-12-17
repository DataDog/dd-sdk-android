/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ktx.tracing

import com.datadog.tools.unit.forge.BaseConfigurator
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Span
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
class OkHttpRequestExtTest {

    @Test
    fun `set the parentSpan through the Request builder`(
        @RegexForgery("http://[a-z0-9_]{8}\\.[a-z]{3}/") fakeUrl: String
    ) {
        val parentSpan: Span = mock()
        val request = Request.Builder().url(fakeUrl).parentSpan(parentSpan).build()

        assertThat(request.tag(Span::class.java)).isEqualTo(parentSpan)
    }
}
