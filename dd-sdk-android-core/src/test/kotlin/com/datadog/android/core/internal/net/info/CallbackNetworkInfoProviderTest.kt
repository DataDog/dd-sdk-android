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
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.utils.assertj.NetworkInfoAssert.Companion.assertThat
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CallbackNetworkInfoProviderTest {

    lateinit var testedProvider: CallbackNetworkInfoProvider

    @Mock
    lateinit var mockNetwork: Network

    @Mock
    lateinit var mockCapabilities: NetworkCapabilities

    @Mock
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        // setup the network capabilities to return the unspecified values by default
        whenever(mockCapabilities.linkUpstreamBandwidthKbps) doReturn 0
        whenever(mockCapabilities.linkDownstreamBandwidthKbps) doReturn 0
        whenever(mockCapabilities.signalStrength) doReturn
            NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED
        whenever(mockCapabilities.hasTransport(any())) doReturn false
        whenever(mockBuildSdkVersionProvider.isAtLeastQ) doReturn false

        testedProvider = CallbackNetworkInfoProvider(mockBuildSdkVersionProvider, mockInternalLogger)
    }

    @Test
    fun `initial state is not connected`() {
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasUpSpeed(null)
            .hasDownSpeed(null)
            .hasStrength(null)
    }

    @Test
    fun `connected to wifi`(
        @IntForgery(min = 1) upSpeed: Int,
        @IntForgery(min = 1) downSpeed: Int,
        @IntForgery(min = -90, max = -40) strength: Int
    ) {
        // GIVEN
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) doReturn true
        whenever(mockCapabilities.linkUpstreamBandwidthKbps) doReturn upSpeed
        whenever(mockCapabilities.linkDownstreamBandwidthKbps) doReturn downSpeed
        whenever(mockCapabilities.signalStrength) doReturn strength
        whenever(mockBuildSdkVersionProvider.isAtLeastQ) doReturn true

        // WHEN
        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        // THEN
        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_WIFI)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasUpSpeed(upSpeed.toLong())
            .hasDownSpeed(downSpeed.toLong())
            .hasStrength(strength.toLong())
    }

    @Test
    fun `connected to wifi (no strength)`(
        @IntForgery(min = 1) upSpeed: Int,
        @IntForgery(min = 1) downSpeed: Int
    ) {
        // GIVEN
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) doReturn true
        whenever(mockCapabilities.linkUpstreamBandwidthKbps) doReturn upSpeed
        whenever(mockCapabilities.linkDownstreamBandwidthKbps) doReturn downSpeed

        // WHEN
        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        // THEN
        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_WIFI)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasUpSpeed(upSpeed.toLong())
            .hasDownSpeed(downSpeed.toLong())
            .hasStrength(null)
    }

    @Test
    fun `connected to wifi (no up or down bandwidth, no strength)`() {
        // GIVEN
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) doReturn true

        // WHEN
        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        // THEN
        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_WIFI)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasUpSpeed(null)
            .hasDownSpeed(null)
            .hasStrength(null)
    }

    @Test
    fun `connected to wifi aware`(
        @IntForgery(min = 1) upSpeed: Int,
        @IntForgery(min = 1) downSpeed: Int,
        @IntForgery(min = -90, max = -40) strength: Int
    ) {
        // GIVEN
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE))
            .doReturn(true)
        whenever(mockCapabilities.linkUpstreamBandwidthKbps) doReturn upSpeed
        whenever(mockCapabilities.linkDownstreamBandwidthKbps) doReturn downSpeed
        whenever(mockCapabilities.signalStrength) doReturn strength
        whenever(mockBuildSdkVersionProvider.isAtLeastQ) doReturn true

        // WHEN
        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        // THEN
        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_OTHER)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasUpSpeed(upSpeed.toLong())
            .hasDownSpeed(downSpeed.toLong())
            .hasStrength(strength.toLong())
    }

    @Test
    fun `connected to wifi aware (no strength)`(
        @IntForgery(min = 1) upSpeed: Int,
        @IntForgery(min = 1) downSpeed: Int
    ) {
        // GIVEN
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE))
            .doReturn(true)
        whenever(mockCapabilities.linkUpstreamBandwidthKbps) doReturn upSpeed
        whenever(mockCapabilities.linkDownstreamBandwidthKbps) doReturn downSpeed

        // WHEN
        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        // THEN
        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_OTHER)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasUpSpeed(upSpeed.toLong())
            .hasDownSpeed(downSpeed.toLong())
            .hasStrength(null)
    }

    @Test
    fun `connected to wifi aware (no up or down bandwidth, no strength)`() {
        // GIVEN
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE))
            .doReturn(true)

        // WHEN
        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        // THEN
        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_OTHER)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasUpSpeed(null)
            .hasDownSpeed(null)
            .hasStrength(null)
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
            .hasCarrierId(null)
            .hasUpSpeed(upSpeed.toLong())
            .hasDownSpeed(downSpeed.toLong())
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
            .hasCarrierId(null)
            .hasUpSpeed(upSpeed.toLong())
            .hasDownSpeed(downSpeed.toLong())
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
            .hasCarrierId(null)
            .hasUpSpeed(upSpeed.toLong())
            .hasDownSpeed(downSpeed.toLong())
            .hasStrength(null)
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
            .hasCarrierId(null)
            .hasUpSpeed(upSpeed.toLong())
            .hasDownSpeed(downSpeed.toLong())
            .hasStrength(null)
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
            .hasCarrierId(null)
            .hasUpSpeed(null)
            .hasDownSpeed(null)
            .hasStrength(null)
    }

    @Test
    fun `M register callback W register()`() {
        val context = mock<Context>()
        val manager = mock<ConnectivityManager>()
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn manager

        testedProvider.register(context)

        verify(manager).registerDefaultNetworkCallback(testedProvider)
    }

    @Test
    fun `M get current network state W register()`(
        @IntForgery(min = 1) upSpeed: Int,
        @IntForgery(min = 1) downSpeed: Int
    ) {
        val context = mock<Context>()
        val manager = mock<ConnectivityManager>()
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn manager
        whenever(manager.activeNetwork) doReturn mockNetwork
        whenever(manager.getNetworkCapabilities(mockNetwork)) doReturn mockCapabilities
        whenever(mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) doReturn true
        whenever(mockCapabilities.linkUpstreamBandwidthKbps) doReturn upSpeed
        whenever(mockCapabilities.linkDownstreamBandwidthKbps) doReturn downSpeed

        testedProvider.register(context)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        verify(manager).registerDefaultNetworkCallback(testedProvider)
        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_WIFI)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasUpSpeed(upSpeed.toLong())
            .hasDownSpeed(downSpeed.toLong())
            .hasStrength(null)
    }

    @Test
    fun `M register callback safely W register() with SecurityException`(
        @StringForgery message: String
    ) {
        // RUMM-852 in some cases the device throws a SecurityException on register
        val context = mock<Context>()
        val manager = mock<ConnectivityManager>()
        val exception = SecurityException(message)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn manager
        whenever(manager.registerDefaultNetworkCallback(testedProvider)) doThrow exception

        testedProvider.register(context)

        verify(manager).registerDefaultNetworkCallback(testedProvider)
    }

    @Test
    fun `M warn developers W register() with null service`() {
        val context = mock<Context>()
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn null

        testedProvider.register(context)

        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            CallbackNetworkInfoProvider.ERROR_REGISTER
        )
    }

    @Test
    fun `M warn developers W register() with SecurityException`(
        @StringForgery message: String
    ) {
        // RUMM-852 in some cases the device throws a SecurityException on register
        val context = mock<Context>()
        val manager = mock<ConnectivityManager>()
        val exception = SecurityException(message)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn manager
        whenever(manager.registerDefaultNetworkCallback(testedProvider)) doThrow exception

        testedProvider.register(context)

        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            CallbackNetworkInfoProvider.ERROR_REGISTER,
            exception
        )
    }

    @Test
    fun `M warn developers W register() with RuntimeException`(
        @StringForgery message: String
    ) {
        // RUMM-918 in some cases the device throws a IllegalArgumentException on register
        // "Too many NetworkRequests filed" This happens when registerDefaultNetworkCallback is
        // called too many times without matching unregisterNetworkCallback
        val context = mock<Context>()
        val manager = mock<ConnectivityManager>()
        val exception = RuntimeException(message)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn manager
        whenever(manager.registerDefaultNetworkCallback(testedProvider)) doThrow exception

        testedProvider.register(context)

        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            CallbackNetworkInfoProvider.ERROR_REGISTER,
            exception
        )
    }

    @Test
    fun `M assume network is available W register() with SecurityException + getLatestNetworkInfo`(
        @StringForgery message: String
    ) {
        // RUMM-852 in some cases the device throws a SecurityException on register
        val context = mock<Context>()
        val manager = mock<ConnectivityManager>()
        val exception = SecurityException(message)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn manager
        whenever(manager.registerDefaultNetworkCallback(testedProvider)) doThrow exception

        testedProvider.register(context)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_OTHER)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasUpSpeed(null)
            .hasDownSpeed(null)
            .hasStrength(null)
    }

    @Test
    fun `M assume network is available W register() with RuntimeException + getLatestNetworkInfo`(
        @StringForgery message: String
    ) {
        // RUMM-918 in some cases the device throws a IllegalArgumentException on register
        // "Too many NetworkRequests filed" This happens when registerDefaultNetworkCallback is
        // called too many times without matching unregisterNetworkCallback
        val context = mock<Context>()
        val manager = mock<ConnectivityManager>()
        val exception = RuntimeException(message)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn manager
        whenever(manager.registerDefaultNetworkCallback(testedProvider)) doThrow exception

        testedProvider.register(context)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_OTHER)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasUpSpeed(null)
            .hasDownSpeed(null)
            .hasStrength(null)
    }

    @Test
    fun `M unregister callback W unregister()`() {
        val context = mock<Context>()
        val manager = mock<ConnectivityManager>()
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn manager

        testedProvider.unregister(context)

        verify(manager).unregisterNetworkCallback(testedProvider)
    }

    @Test
    fun `M unregister callback safely W unregister() with SecurityException`(
        @StringForgery message: String
    ) {
        // RUMM-852 in some cases the device throws a SecurityException on register
        // Since we can't reproduce, let's assume it could happen on unregister too
        val context = mock<Context>()
        val manager = mock<ConnectivityManager>()
        val exception = SecurityException(message)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn manager
        whenever(manager.unregisterNetworkCallback(testedProvider)) doThrow exception

        testedProvider.unregister(context)

        verify(manager).unregisterNetworkCallback(testedProvider)
    }

    @Test
    fun `M warn developers W unregister() with SecurityException`(
        @StringForgery message: String
    ) {
        // RUMM-852 in some cases the device throws a SecurityException on register
        val context = mock<Context>()
        val manager = mock<ConnectivityManager>()
        val exception = SecurityException(message)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn manager
        whenever(manager.unregisterNetworkCallback(testedProvider)) doThrow exception

        testedProvider.unregister(context)

        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            CallbackNetworkInfoProvider.ERROR_UNREGISTER,
            exception
        )
    }

    @Test
    fun `M warn developers W unregister() with null service`() {
        val context = mock<Context>()
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn null

        testedProvider.unregister(context)

        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            CallbackNetworkInfoProvider.ERROR_UNREGISTER
        )
    }

    @Test
    fun `M warn developers W unregister() with RuntimeException`(
        @StringForgery message: String
    ) {
        // RUMM-918 in some cases the device throws a IllegalArgumentException on unregister
        // e.g. when the callback was not registered
        val context = mock<Context>()
        val manager = mock<ConnectivityManager>()
        val exception = RuntimeException(message)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn manager
        whenever(manager.unregisterNetworkCallback(testedProvider)) doThrow exception

        testedProvider.unregister(context)

        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            CallbackNetworkInfoProvider.ERROR_UNREGISTER,
            exception
        )
    }
}
