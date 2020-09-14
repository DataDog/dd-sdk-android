/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.core.internal.net.identifyRequest
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class DatadogEventListenerFactoryTest {

    lateinit var testedFactory: DatadogEventListener.Factory

    @Mock
    lateinit var mockCall: Call

    @RegexForgery("[a-z]+\\.[a-z]{3}")
    lateinit var fakeDomain: String

    lateinit var fakeRequest: Request

    @BeforeEach
    fun `set up`() {

        fakeRequest = Request.Builder()
            .get().url("https://$fakeDomain/")
            .build()

        whenever(mockCall.request()) doReturn fakeRequest

        testedFactory = DatadogEventListener.Factory()
    }

    @Test
    fun `𝕄 create event listener 𝕎 create()`() {
        // When
        val result = testedFactory.create(mockCall)

        // Then
        check(result is DatadogEventListener)
        assertThat(result.key).isEqualTo(identifyRequest(fakeRequest))
    }
}
