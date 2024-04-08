/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core

import com.datadog.android.Datadog
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
internal class SdkReferenceTest {

    @Mock
    lateinit var mockSdkCore: DatadogCore

    @BeforeEach
    fun `set up`() {
        Datadog.registry.register(null, mockSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.registry.clear()
    }

    @Test
    fun `M return SDK instance W get() {instance exists}`() {
        // Given
        val testedReference = SdkReference(null)

        // When
        val sdkCore = testedReference.get()

        // Then
        assertThat(sdkCore).isSameAs(mockSdkCore)
    }

    @Test
    fun `M return null W get() {instance doesn't exist}`(
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
    fun `M release reference W get() {instance is stopped}`() {
        // Given
        val testedReference = SdkReference(null)
        assertThat(testedReference.get()).isNotNull
        whenever(mockSdkCore.isActive) doReturn false

        // When
        val sdkCore = testedReference.get()

        // Then
        assertThat(sdkCore).isNull()
    }

    @Test
    fun `M call onSdkInstanceCaptured once W get() { multiple threads }`(
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
}
