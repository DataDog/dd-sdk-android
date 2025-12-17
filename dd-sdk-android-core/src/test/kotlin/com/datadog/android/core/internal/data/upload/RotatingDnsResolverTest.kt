/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Dns
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.net.InetAddress
import kotlin.time.Duration.Companion.milliseconds

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RotatingDnsResolverTest {

    lateinit var testedDns: Dns

    @Mock
    lateinit var mockDelegate: Dns

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @StringForgery
    lateinit var fakeHostname: String

    @LongForgery(min = 0L)
    var fakeElapsedTimeNs: Long = 0L

    lateinit var fakeInetAddresses: List<InetAddress>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeInetAddresses = forge.aList { mock() }

        testedDns = RotatingDnsResolver(mockDelegate, TEST_TTL_MS, mockTimeProvider)
    }

    @Test
    fun `M return delegate result W lookup {unknown hostname}`() {
        // Given
        whenever(mockDelegate.lookup(fakeHostname)) doReturn fakeInetAddresses

        // When
        val result = testedDns.lookup(fakeHostname)

        // Then
        assertThat(result).containsExactlyElementsOf(fakeInetAddresses)
    }

    @Test
    fun `M rotate known result W lookup {known hostname}`() {
        // Given
        whenever(mockDelegate.lookup(fakeHostname)) doReturn fakeInetAddresses
        val result = mutableListOf<InetAddress>()

        // When
        repeat(fakeInetAddresses.size) {
            result.add(testedDns.lookup(fakeHostname).first())
        }

        // Then
        assertThat(result).containsExactlyElementsOf(fakeInetAddresses)
        verify(mockDelegate).lookup(fakeHostname)
    }

    @Test
    fun `M renew result W lookup {expired hostname}`(
        forge: Forge
    ) {
        // Given
        val fakeInetAddresses2: List<InetAddress> = forge.aList { mock() }
        whenever(mockDelegate.lookup(fakeHostname)).doReturn(fakeInetAddresses, fakeInetAddresses2)

        // When
        val result = testedDns.lookup(fakeHostname)
        val fakeExpiredTime = fakeElapsedTimeNs + TEST_TTL_MS.inWholeNanoseconds
        whenever(mockTimeProvider.getDeviceElapsedTimeNs()) doReturn fakeExpiredTime
        val result2 = testedDns.lookup(fakeHostname)

        // Then
        assertThat(result).containsExactlyElementsOf(fakeInetAddresses)
        assertThat(result2).containsExactlyElementsOf(fakeInetAddresses2)
        verify(mockDelegate, times(2)).lookup(fakeHostname)
    }

    @Test
    fun `M renew result W lookup {empty hostname}`() {
        // Given
        whenever(mockDelegate.lookup(fakeHostname)).doReturn(emptyList(), fakeInetAddresses)

        // When
        val result = testedDns.lookup(fakeHostname)
        val result2 = testedDns.lookup(fakeHostname)

        // Then
        assertThat(result).isEmpty()
        assertThat(result2).containsExactlyElementsOf(fakeInetAddresses)
    }

    @RepeatedTest(30)
    fun `M not throw exception W concurrent access to lookup`(forge: Forge) {
        // Given
        // we need to keep the list of addresses low as it can only be reproduced with low number and it reflects
        // the real use case where we have a small number of addresses to rotate
        fakeInetAddresses = forge.aList(size = forge.anInt(min = 1, max = 3)) { mock() }
        whenever(mockDelegate.lookup(fakeHostname)) doReturn fakeInetAddresses
        // just wait the TTL time to make sure all threads are concurrently accessing the lookup
        Thread.sleep(TEST_TTL_MS.inWholeMilliseconds)

        var exceptionThrown: Exception? = null

        // When
        List(100) {
            Thread {
                Thread.sleep(forge.aLong(min = 0, max = 100))
                try {
                    testedDns.lookup(fakeHostname)
                } catch (e: Exception) {
                    exceptionThrown = e
                }
            }.apply {
                start()
            }
        }.forEach { it.join() }

        // Then
        assertThat(exceptionThrown).isNull()
    }

    companion object {
        internal val TEST_TTL_MS = 250.milliseconds
    }
}
