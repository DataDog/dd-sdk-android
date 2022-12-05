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
import android.util.Log
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.log.assertj.NetworkInfoAssert.Companion.assertThat
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.context.NetworkInfo
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
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
    lateinit var mockWriter: DataWriter<NetworkInfo>

    @Mock
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @BeforeEach
    fun `set up`() {
        // setup the network capabilities to return the unspecified values by default
        whenever(mockCapabilities.linkUpstreamBandwidthKbps) doReturn 0
        whenever(mockCapabilities.linkDownstreamBandwidthKbps) doReturn 0
        whenever(mockCapabilities.signalStrength) doReturn
            NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED
        whenever(mockCapabilities.hasTransport(any())) doReturn false
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.BASE

        testedProvider =
            CallbackNetworkInfoProvider(mockWriter, mockBuildSdkVersionProvider)
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
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.Q

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
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.LOLLIPOP

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
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.LOLLIPOP

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
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.Q

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
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.LOLLIPOP

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
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.LOLLIPOP

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
    fun `M delegate to persister with new NetworkInfo W register()`(
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

        verify(mockWriter).write(networkInfo)
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

        verify(logger.mockDevLogHandler)
            .handleLog(
                Log.ERROR,
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

        verify(logger.mockDevLogHandler)
            .handleLog(
                Log.ERROR,
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

        verify(logger.mockDevLogHandler)
            .handleLog(
                Log.ERROR,
                CallbackNetworkInfoProvider.ERROR_REGISTER,
                exception
            )
    }

    @Test
    fun `M delegate to persister with new NetworkInfo W register() with RuntimeException`(
        @StringForgery message: String
    ) {
        val context = mock<Context>()
        val manager = mock<ConnectivityManager>()
        val exception = RuntimeException(message)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn manager
        whenever(manager.registerDefaultNetworkCallback(testedProvider)) doThrow exception

        testedProvider.register(context)

        verify(mockWriter).write(testedProvider.getLatestNetworkInfo())
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
    fun `M delegate to persister with new NetworkInfo W register() with SecurityException`(
        @StringForgery message: String
    ) {
        val context = mock<Context>()
        val manager = mock<ConnectivityManager>()
        val exception = SecurityException(message)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn manager
        whenever(manager.registerDefaultNetworkCallback(testedProvider)) doThrow exception

        testedProvider.register(context)

        verify(mockWriter).write(testedProvider.getLatestNetworkInfo())
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

        verify(logger.mockDevLogHandler)
            .handleLog(
                Log.ERROR,
                CallbackNetworkInfoProvider.ERROR_UNREGISTER,
                exception
            )
    }

    @Test
    fun `M warn developers W unregister() with null service`() {
        val context = mock<Context>()
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)) doReturn null

        testedProvider.unregister(context)

        verify(logger.mockDevLogHandler)
            .handleLog(
                Log.ERROR,
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

        verify(logger.mockDevLogHandler)
            .handleLog(
                Log.ERROR,
                CallbackNetworkInfoProvider.ERROR_UNREGISTER,
                exception
            )
    }

    @Test
    fun `M delegate to persister W capabilities changed`() {
        // WHEN
        testedProvider.onCapabilitiesChanged(mockNetwork, mockCapabilities)

        // THEN
        verify(mockWriter).write(testedProvider.getLatestNetworkInfo())
    }

    companion object {
        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
