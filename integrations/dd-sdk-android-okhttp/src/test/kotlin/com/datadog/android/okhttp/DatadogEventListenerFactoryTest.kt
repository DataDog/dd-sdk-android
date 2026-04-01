/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.okhttp.internal.rum.buildResourceId
import com.datadog.android.okhttp.utils.config.DatadogSingletonTestConfiguration
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class DatadogEventListenerFactoryTest {

    lateinit var testedFactory: DatadogEventListener.Factory

    @Mock
    lateinit var mockCall: Call

    @StringForgery(regex = "[a-z]+\\.[a-z]{3}")
    lateinit var fakeDomain: String

    private lateinit var fakeRequest: Request

    @BeforeEach
    fun `set up`() {
        fakeRequest = Request.Builder()
            .get().url("https://$fakeDomain/")
            .build()

        whenever(mockCall.request()) doReturn fakeRequest

        testedFactory = DatadogEventListener.Factory()
    }

    @Test
    fun `M create event listener W create()`() {
        // When
        val result = testedFactory.create(mockCall)

        // Then
        check(result is DatadogEventListener)
        assertThat(result.key).isEqualTo(fakeRequest.buildResourceId(false))
    }

    @Test
    fun `M create no-op event listener W create() { SDK instance is not ready }`(
        @StringForgery fakeSdkInstanceName: String
    ) {
        // When
        val factory = DatadogEventListener.Factory(fakeSdkInstanceName)
        val result = factory.create(mockCall)

        // Then
        assertThat(result).isSameAs(DatadogEventListener.Factory.NO_OP_EVENT_LISTENER)
    }

    // region RUMS-5184 regression tests

    @Test
    fun `RUMS-5184 M produce different ResourceIds W buildResourceId called twice without UUID tag`() {
        // Given
        // A Request with no UUID tag — simulating what DatadogInterceptor and
        // DatadogEventListener.Factory each see independently.
        val request = Request.Builder()
            .get().url("https://$fakeDomain/api/resource")
            .build()

        // When
        // Interceptor calls buildResourceId(generateUuid=true) → ResourceId(key, uuid_A)
        val interceptorResourceId = request.buildResourceId(generateUuid = true)
        // Factory.create() calls buildResourceId(generateUuid=true) → ResourceId(key, uuid_B)
        val factoryResourceId = request.buildResourceId(generateUuid = true)

        // Then
        // The two IDs share the same key but have different UUIDs because buildResourceId()
        // never stores the generated UUID back on the request tag. Correct behaviour would be
        // that the two IDs are equal so that timing events are matched to the right scope.
        // This assertion documents the CORRECT expected behaviour; it FAILS on the buggy code.
        assertThat(interceptorResourceId).isEqualTo(factoryResourceId)
    }

    // endregion

    companion object {
        val datadogCore = DatadogSingletonTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(datadogCore)
        }
    }
}
