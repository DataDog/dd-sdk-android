/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.core.internal.DatadogCore
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    fun `M capture SDK without another get W register {reference requested before initialization}`(
        @StringForgery fakeInstanceName: String
    ) {
        // Given
        val mockCallback = mock<(SdkCore) -> Unit>()
        val testedReference = SdkReference(fakeInstanceName, mockCallback)
        assertThat(testedReference.get()).isNull()
        assertThat(testedReference.get()).isNull()

        // When
        Datadog.registry.register(fakeInstanceName, mockSdkCore)

        // Then
        verify(mockCallback).invoke(mockSdkCore)
        verifyNoMoreInteractions(mockCallback)
    }

    @Test
    fun `M notify only matching references W register`(
        @StringForgery fakeInstanceName: String
    ) {
        // Given
        Datadog.registry.clear()
        val fakeDefaultCaptures = mutableListOf<SdkCore>()
        val fakeNamedCaptures = mutableListOf<SdkCore>()
        val testedDefaultReference = SdkReference { fakeDefaultCaptures.add(it) }
        val testedNamedReference = SdkReference(fakeInstanceName) { fakeNamedCaptures.add(it) }
        testedDefaultReference.get()
        testedNamedReference.get()
        val mockNamedCore = mock<SdkCore>()

        // When
        Datadog.registry.register(fakeInstanceName, mockNamedCore)
        Datadog.registry.register(null, mockSdkCore)

        // Then
        assertThat(fakeDefaultCaptures).containsExactly(mockSdkCore)
        assertThat(fakeNamedCaptures).containsExactly(mockNamedCore)
    }

    @Test
    fun `M capture replacement SDK without request W unregister and register`(
        @StringForgery fakeInstanceName: String
    ) {
        // Given
        val fakeCaptures = mutableListOf<SdkCore>()
        val testedReference = SdkReference(fakeInstanceName) { fakeCaptures.add(it) }
        testedReference.get()
        Datadog.registry.register(fakeInstanceName, mockSdkCore)
        val mockReplacementCore = mock<SdkCore>()

        // When
        Datadog.registry.unregister(fakeInstanceName)
        Datadog.registry.register(fakeInstanceName, mockReplacementCore)

        // Then
        assertThat(fakeCaptures).containsExactly(mockSdkCore, mockReplacementCore)
        assertThat(testedReference.get()).isSameAs(mockReplacementCore)
    }

    @Test
    fun `M discard waiting references W clear`(@StringForgery fakeInstanceName: String) {
        // Given
        val fakeCaptures = mutableListOf<SdkCore>()
        val testedReference = SdkReference(fakeInstanceName) { fakeCaptures.add(it) }
        testedReference.get()

        // When
        Datadog.registry.clear()
        Datadog.registry.register(fakeInstanceName, mockSdkCore)

        // Then
        assertThat(fakeCaptures).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M allow another thread to access registry W capture callback`(
        fakeSdkAlreadyInitialized: Boolean,
        @StringForgery fakeInstanceName: String
    ) {
        // Given
        whenever(mockSdkCore.isActive) doReturn true
        if (fakeSdkAlreadyInitialized) Datadog.registry.register(fakeInstanceName, mockSdkCore)
        val fakeExecutor = Executors.newSingleThreadExecutor()
        var fakeCapturedCore: SdkCore? = null
        val testedReference = SdkReference(fakeInstanceName) {
            fakeCapturedCore = fakeExecutor.submit<SdkCore> {
                Datadog.getInstance(fakeInstanceName)
            }.get(5, TimeUnit.SECONDS)
        }

        // When
        try {
            testedReference.get()
            if (!fakeSdkAlreadyInitialized) Datadog.registry.register(fakeInstanceName, mockSdkCore)

            // Then
            assertThat(fakeCapturedCore).isSameAs(mockSdkCore)
            assertThat(testedReference.get()).isSameAs(mockSdkCore)
        } finally {
            fakeExecutor.shutdownNow()
        }
    }

    @Test
    fun `M call onSdkInstanceCaptured once W get() { multiple threads }`(
        @IntForgery(min = 2, max = 10) threadCount: Int
    ) {
        // Given
        whenever(mockSdkCore.isActive) doReturn true
        val mockCallback = mock<(SdkCore) -> Unit>()
        val testedReference = SdkReference(null, mockCallback)
        val fakeStart = CountDownLatch(1)
        val fakeExecutor = Executors.newFixedThreadPool(threadCount)

        // When
        try {
            val results = List(threadCount) {
                fakeExecutor.submit<SdkCore?> {
                    check(fakeStart.await(5, TimeUnit.SECONDS))
                    testedReference.get()
                }
            }
            fakeStart.countDown()

            // Then
            results.forEach { assertThat(it.get(5, TimeUnit.SECONDS)).isSameAs(mockSdkCore) }
            assertThat(testedReference.get()).isSameAs(mockSdkCore)
            verify(mockCallback).invoke(mockSdkCore)
            verifyNoMoreInteractions(mockCallback)
        } finally {
            fakeExecutor.shutdownNow()
        }
    }

    @Test
    fun `M capture once W get and register {concurrent initialization}`(
        @StringForgery fakeInstanceName: String
    ) {
        // Given
        whenever(mockSdkCore.isActive) doReturn true
        val mockCallback = mock<(SdkCore) -> Unit>()
        val testedReference = SdkReference(fakeInstanceName, mockCallback)
        val fakeStart = CountDownLatch(1)
        val fakeExecutor = Executors.newFixedThreadPool(2)

        // When
        try {
            val acquisition = fakeExecutor.submit<SdkCore?> {
                check(fakeStart.await(5, TimeUnit.SECONDS))
                testedReference.get()
            }
            val registration = fakeExecutor.submit {
                check(fakeStart.await(5, TimeUnit.SECONDS))
                Datadog.registry.register(fakeInstanceName, mockSdkCore)
            }
            fakeStart.countDown()
            acquisition.get(5, TimeUnit.SECONDS)
            registration.get(5, TimeUnit.SECONDS)

            // Then
            assertThat(testedReference.get()).isSameAs(mockSdkCore)
            verify(mockCallback).invoke(mockSdkCore)
            verifyNoMoreInteractions(mockCallback)
        } finally {
            fakeExecutor.shutdownNow()
        }
    }
}
