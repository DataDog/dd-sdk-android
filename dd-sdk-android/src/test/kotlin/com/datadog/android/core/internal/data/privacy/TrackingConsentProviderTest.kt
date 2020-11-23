/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.privacy

import com.datadog.android.core.internal.privacy.TrackingConsentProvider
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.privacy.TrackingConsentProviderCallback
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.argForWhich
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Java6Assertions.assertThat
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
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class TrackingConsentProviderTest {

    lateinit var testedConsentProvider: TrackingConsentProvider

    @Mock
    lateinit var mockedCallback: TrackingConsentProviderCallback

    @BeforeEach
    fun `set up`() {
        testedConsentProvider = TrackingConsentProvider(TrackingConsent.PENDING)
    }

    @Test
    fun `M hold PENDING consent by default W initialised`(forge: Forge) {
        assertThat(testedConsentProvider.getConsent()).isEqualTo(TrackingConsent.PENDING)
    }

    @Test
    fun `M update last consent W required`(forge: Forge) {
        // GIVEN
        val fakeConsent = forge.aValueFrom(TrackingConsent::class.java)

        // WHEN
        testedConsentProvider.setConsent(fakeConsent)

        // THEN
        assertThat(testedConsentProvider.getConsent()).isEqualTo(fakeConsent)
    }

    @Test
    fun `M notify callbacks W updating consent`(forge: Forge) {
        // GIVEN
        val fakeConsent =
            forge.aValueFrom(TrackingConsent::class.java, listOf(TrackingConsent.PENDING))
        testedConsentProvider.registerCallback(mockedCallback)

        // WHEN
        testedConsentProvider.setConsent(fakeConsent)

        // THEN
        verify(mockedCallback).onConsentUpdated(TrackingConsent.PENDING, fakeConsent)
        verifyNoMoreInteractions(mockedCallback)
    }

    @Test
    fun `M not notify callbacks W updating consent with same value`(forge: Forge) {
        // GIVEN
        val fakeConsent =
            forge.aValueFrom(TrackingConsent::class.java, listOf(TrackingConsent.PENDING))
        testedConsentProvider.registerCallback(mockedCallback)
        testedConsentProvider.setConsent(fakeConsent)

        // WHEN
        testedConsentProvider.setConsent(fakeConsent)

        // THEN
        verify(mockedCallback).onConsentUpdated(TrackingConsent.PENDING, fakeConsent)
        verifyNoMoreInteractions(mockedCallback)
    }

    @Test
    fun `M unregister all callbacks W requested`(forge: Forge) {
        // GIVEN
        val fakeConsent =
            forge.aValueFrom(TrackingConsent::class.java, listOf(TrackingConsent.PENDING))
        val anotherMockedCallback: TrackingConsentProviderCallback = mock()
        testedConsentProvider.registerCallback(anotherMockedCallback)
        testedConsentProvider.registerCallback(mockedCallback)

        // WHEN
        testedConsentProvider.unregisterAllCallbacks()
        testedConsentProvider.setConsent(fakeConsent)

        // THEN
        verifyZeroInteractions(mockedCallback)
        verifyZeroInteractions(anotherMockedCallback)
    }

    @Test
    fun `M unregister first W called asynchronously`(forge: Forge) {
        // GIVEN
        val fakeConsent = forge.aValueFrom(
            TrackingConsent::class.java,
            listOf(TrackingConsent.PENDING)
        )
        testedConsentProvider.registerCallback(mockedCallback)
        val countDownLatch = CountDownLatch(2)

        // WHEN
        Thread {
            testedConsentProvider.unregisterAllCallbacks()
            countDownLatch.countDown()
        }.start()
        Thread {
            Thread.sleep(1)
            testedConsentProvider.setConsent(fakeConsent)
            countDownLatch.countDown()
        }.start()

        // THEN
        verifyZeroInteractions(mockedCallback)
    }

    @Test
    fun `M always return the right value W updating from multiple threads`(forge: Forge) {
        // GIVEN
        val fakedConsent1 = forge.aValueFrom(TrackingConsent::class.java)
        val fakedConsent2 =
            forge.aValueFrom(TrackingConsent::class.java, listOf(TrackingConsent.PENDING))
        val countDownLatch = CountDownLatch(2)

        // WHEN
        Thread {
            testedConsentProvider.setConsent(fakedConsent1)
            countDownLatch.countDown()
        }.start()
        Thread {
            Thread.sleep(10) // just to give time to the first thread
            testedConsentProvider.setConsent(fakedConsent2)
            countDownLatch.countDown()
        }.start()
        countDownLatch.await(1, TimeUnit.SECONDS)

        // THEN
        assertThat(testedConsentProvider.getConsent()).isEqualTo(fakedConsent2)
    }

    @Test
    fun `M notify the registered callback W registering from different threads`(forge: Forge) {
        // GIVEN
        val fakeConsent1 = TrackingConsent.GRANTED
        val fakeConsent2 = TrackingConsent.NOT_GRANTED
        val countDownLatch = CountDownLatch(3)

        // WHEN
        Thread {
            testedConsentProvider.registerCallback(mockedCallback)
            countDownLatch.countDown()
        }.start()
        Thread {
            Thread.sleep(2) // just to callback register thread to take the lock
            testedConsentProvider.setConsent(fakeConsent1)
            countDownLatch.countDown()
        }.start()
        Thread {
            Thread.sleep(2)
            testedConsentProvider.setConsent(fakeConsent2)
            countDownLatch.countDown()
        }.start()
        countDownLatch.await(1, TimeUnit.SECONDS)

        // THEN
        assertThat(testedConsentProvider.getConsent()).isIn(fakeConsent1, fakeConsent2)
        verify(mockedCallback).onConsentUpdated(
            argForWhich {
                this == TrackingConsent.PENDING || this == fakeConsent2
            },
            eq(fakeConsent1)
        )
        verify(mockedCallback).onConsentUpdated(
            argForWhich {
                this == TrackingConsent.PENDING || this == fakeConsent1
            },
            eq(fakeConsent2)
        )
        verifyNoMoreInteractions(mockedCallback)
    }
}
