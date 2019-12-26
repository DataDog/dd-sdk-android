/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.datadog.android.log.assertj.NetworkInfoAssert.Companion.assertThat
import com.datadog.android.log.forge.Configurator
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CallbackNetworkInfoProviderTest {

    lateinit var testedProvider: CallbackNetworkInfoProvider

    @Mock
    lateinit var mockNetwork: Network
    @Mock
    lateinit var mockCapabilities: NetworkCapabilities

    @BeforeEach
    fun `set up`() {
        whenever(mockCapabilities.hasTransport(any())) doReturn false

        testedProvider = CallbackNetworkInfoProvider()
    }

    @Test
    fun `initial state is not connected`() {
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
            .hasCarrierName(null)
            .hasCarrierId(-1)
    }

    @Test
    fun `connected to wifi`() {
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) doReturn true

        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_WIFI)
            .hasCarrierName(null)
            .hasCarrierId(-1)
    }

    @Test
    fun `connected to wifi aware`() {
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE))
            .doReturn(true)

        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_OTHER)
            .hasCarrierName(null)
            .hasCarrierId(-1)
    }

    @Test
    fun `connected to cellular`() {
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            .doReturn(true)

        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_CELLULAR)
            .hasCarrierName(null)
            .hasCarrierId(-1)
    }

    @Test
    fun `connected to ethernet`() {
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            .doReturn(true)

        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_ETHERNET)
            .hasCarrierName(null)
            .hasCarrierId(-1)
    }

    @Test
    fun `connected to VPN`() {
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) doReturn true

        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_OTHER)
            .hasCarrierName(null)
            .hasCarrierId(-1)
    }

    @Test
    fun `connected to LoWPAN`() {
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN))
            .doReturn(true)

        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_OTHER)
            .hasCarrierName(null)
            .hasCarrierId(-1)
    }

    @Test
    fun `network lost`() {
        whenever(mockCapabilities.hasTransport(any())) doReturn true

        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        testedProvider.onLost(mockNetwork)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
            .hasCarrierName(null)
            .hasCarrierId(-1)
    }

    @Test
    fun `registers callback`() {
        val context = mock<Context>()
        val manager = mock<ConnectivityManager>()
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn manager

        testedProvider.register(context)

        verify(manager).registerDefaultNetworkCallback(testedProvider)
    }

    @Test
    fun `unregisters callback`() {
        val context = mock<Context>()
        val manager = mock<ConnectivityManager>()
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn manager

        testedProvider.unregister(context)

        verify(manager).unregisterNetworkCallback(testedProvider)
    }
}
