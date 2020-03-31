/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net.info

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.datadog.android.log.assertj.NetworkInfoAssert.Companion.assertThat
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.IntForgery
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

        testedProvider =
            CallbackNetworkInfoProvider()
    }

    @Test
    fun `initial state is not connected`() {
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasUpSpeed(-1)
            .hasDownSpeed(-1)
            .hasStrength(Int.MIN_VALUE)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.Q)
    fun `connected to wifi`(
        @IntForgery(min = 1) upSpeed: Int,
        @IntForgery(min = 1) downSpeed: Int,
        @IntForgery(min = -90, max = -40) strength: Int
    ) {
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) doReturn true
        whenever(mockCapabilities.linkUpstreamBandwidthKbps) doReturn upSpeed
        whenever(mockCapabilities.linkDownstreamBandwidthKbps) doReturn downSpeed
        whenever(mockCapabilities.signalStrength) doReturn strength

        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_WIFI)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasUpSpeed(upSpeed)
            .hasDownSpeed(downSpeed)
            .hasStrength(strength)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `connected to wifi (no strength)`(
        @IntForgery(min = 1) upSpeed: Int,
        @IntForgery(min = 1) downSpeed: Int
    ) {
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) doReturn true
        whenever(mockCapabilities.linkUpstreamBandwidthKbps) doReturn upSpeed
        whenever(mockCapabilities.linkDownstreamBandwidthKbps) doReturn downSpeed

        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_WIFI)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasUpSpeed(upSpeed)
            .hasDownSpeed(downSpeed)
            .hasStrength(Int.MIN_VALUE)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.Q)
    fun `connected to wifi aware`(
        @IntForgery(min = 1) upSpeed: Int,
        @IntForgery(min = 1) downSpeed: Int,
        @IntForgery(min = -90, max = -40) strength: Int
    ) {
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE))
            .doReturn(true)
        whenever(mockCapabilities.linkUpstreamBandwidthKbps) doReturn upSpeed
        whenever(mockCapabilities.linkDownstreamBandwidthKbps) doReturn downSpeed
        whenever(mockCapabilities.signalStrength) doReturn strength

        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_OTHER)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasUpSpeed(upSpeed)
            .hasDownSpeed(downSpeed)
            .hasStrength(strength)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `connected to wifi aware (no strength)`(
        @IntForgery(min = 1) upSpeed: Int,
        @IntForgery(min = 1) downSpeed: Int
    ) {
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE))
            .doReturn(true)
        whenever(mockCapabilities.linkUpstreamBandwidthKbps) doReturn upSpeed
        whenever(mockCapabilities.linkDownstreamBandwidthKbps) doReturn downSpeed

        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_OTHER)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasUpSpeed(upSpeed)
            .hasDownSpeed(downSpeed)
            .hasStrength(Int.MIN_VALUE)
    }

    @Test
    fun `connected to cellular`(
        @IntForgery(min = 1) upSpeed: Int,
        @IntForgery(min = 1) downSpeed: Int
    ) {
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            .doReturn(true)
        whenever(mockCapabilities.linkUpstreamBandwidthKbps) doReturn upSpeed
        whenever(mockCapabilities.linkDownstreamBandwidthKbps) doReturn downSpeed

        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_CELLULAR)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasUpSpeed(upSpeed)
            .hasDownSpeed(downSpeed)
    }

    @Test
    fun `connected to ethernet`(
        @IntForgery(min = 1) upSpeed: Int,
        @IntForgery(min = 1) downSpeed: Int
    ) {
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            .doReturn(true)
        whenever(mockCapabilities.linkUpstreamBandwidthKbps) doReturn upSpeed
        whenever(mockCapabilities.linkDownstreamBandwidthKbps) doReturn downSpeed

        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_ETHERNET)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasUpSpeed(upSpeed)
            .hasDownSpeed(downSpeed)
    }

    @Test
    fun `connected to VPN`(
        @IntForgery(min = 1) upSpeed: Int,
        @IntForgery(min = 1) downSpeed: Int
    ) {
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) doReturn true
        whenever(mockCapabilities.linkUpstreamBandwidthKbps) doReturn upSpeed
        whenever(mockCapabilities.linkDownstreamBandwidthKbps) doReturn downSpeed

        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_OTHER)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasUpSpeed(upSpeed)
            .hasDownSpeed(downSpeed)
    }

    @Test
    fun `connected to LoWPAN`(
        @IntForgery(min = 1) upSpeed: Int,
        @IntForgery(min = 1) downSpeed: Int
    ) {
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN))
            .doReturn(true)
        whenever(mockCapabilities.linkUpstreamBandwidthKbps) doReturn upSpeed
        whenever(mockCapabilities.linkDownstreamBandwidthKbps) doReturn downSpeed

        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_OTHER)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasUpSpeed(upSpeed)
            .hasDownSpeed(downSpeed)
    }

    @Test
    fun `network lost`(
        @IntForgery(min = 1) upSpeed: Int,
        @IntForgery(min = 1) downSpeed: Int
    ) {
        whenever(mockCapabilities.hasTransport(any())) doReturn true
        whenever(mockCapabilities.linkUpstreamBandwidthKbps) doReturn upSpeed
        whenever(mockCapabilities.linkDownstreamBandwidthKbps) doReturn downSpeed

        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        testedProvider.onLost(mockNetwork)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasUpSpeed(-1)
            .hasDownSpeed(-1)
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
