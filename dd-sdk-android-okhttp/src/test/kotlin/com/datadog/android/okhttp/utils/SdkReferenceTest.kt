/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.utils

import com.datadog.android.okhttp.utils.config.DatadogSingletonTestConfiguration
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
internal class SdkReferenceTest {

    @Test
    fun `𝕄 return SDK instance 𝕎 get() {instance exists}`() {
        // Given
        val testedReference = SdkReference(null)

        // When
        val sdkCore = testedReference.get()

        // Then
        assertThat(sdkCore).isSameAs(datadogCore.mockInstance)
    }

    @Test
    fun `𝕄 return null 𝕎 get() {instance doesn't exist}`(
        @StringForgery fakeInstanceName: String
    ) {
        // Given
        val emptyReference = SdkReference(fakeInstanceName)

        // When
        val sdkCore = emptyReference.get()

        // Then
        assertThat(sdkCore).isNull()
    }

    @Test
    fun `𝕄 call onSdkInstanceCaptured once 𝕎 get() { multiple threads }`(
        @IntForgery(min = 2, max = 10) threadCount: Int
    ) {
        // Given
        var callsCount = 0
        val testedReference = SdkReference(null) {
            callsCount++
        }

        // When
        val threads = buildList(threadCount) { add(Thread { testedReference.get() }) }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Then
        assertThat(callsCount).isEqualTo(1)
    }

    companion object {

        val datadogCore = DatadogSingletonTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(datadogCore)
        }
    }
}
