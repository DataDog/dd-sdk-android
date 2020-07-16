/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net.info

// import org.assertj.core.api.Assertions.assertThat
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo as AndroidNetworkInfo
import android.os.Build
import android.telephony.TelephonyManager
import com.datadog.android.log.assertj.NetworkInfoAssert.Companion.assertThat
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
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
@Suppress("DEPRECATION")
internal class BroadcastReceiverNetworkInfoProviderTest {

    lateinit var testedProvider: BroadcastReceiverNetworkInfoProvider

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockConnectivityManager: ConnectivityManager

    @Mock
    lateinit var mockTelephonyManager: TelephonyManager

    @Mock
    lateinit var mockNetworkInfo: AndroidNetworkInfo

    @Mock
    lateinit var mockIntent: Intent

    @BeforeEach
    fun `set up`() {
        whenever(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityManager)
        whenever(mockContext.getSystemService(Context.TELEPHONY_SERVICE))
            .doReturn(mockTelephonyManager)

        testedProvider = BroadcastReceiverNetworkInfoProvider()
    }

    @Test
    fun `initial state is not connected`() {
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasCellularTechnology(null)
    }

    @Test
    fun `read network info on register`() {
        whenever(mockNetworkInfo.isConnected) doReturn true
        whenever(mockNetworkInfo.type) doReturn ConnectivityManager.TYPE_WIFI
        whenever(mockConnectivityManager.activeNetworkInfo) doReturn mockNetworkInfo

        testedProvider.register(mockContext)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_WIFI)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasCellularTechnology(null)
    }

    @Test
    fun `not connected (null)`() {
        whenever(mockConnectivityManager.activeNetworkInfo) doReturn null
        testedProvider.onReceive(mockContext, mockIntent)

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasCellularTechnology(null)
    }

    @Test
    fun `not connected (connected but no internet)`() {
        broadcastNetworkChangeEvent(-1 /* @hide ConnectivityManager.TYPE_NONE*/)

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasCellularTechnology(null)
    }

    @Test
    fun `connected to wifi`() {
        broadcastNetworkChangeEvent(ConnectivityManager.TYPE_WIFI)

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_WIFI)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasCellularTechnology(null)
    }

    @Test
    fun `connected to ethernet`() {
        broadcastNetworkChangeEvent(ConnectivityManager.TYPE_ETHERNET)

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_ETHERNET)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasCellularTechnology(null)
    }

    @Test
    fun `connected to mobile 2G`(forge: Forge) {
        val subtype = forge.anElementFrom(known2GSubtypes)
        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        broadcastNetworkChangeEvent(
            forge.anElementFrom(knownMobileTypes), subtype, carrierName, carrierId
        )

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_2G)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasCellularTechnology(mobileSubtypeNames[subtype])
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.P)
    fun `connected to mobile 2G API 28+`(forge: Forge) {
        val subtype = forge.anElementFrom(known2GSubtypes)
        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        broadcastNetworkChangeEvent(
            forge.anElementFrom(knownMobileTypes), subtype, carrierName, carrierId
        )

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_2G)
            .hasCarrierName(carrierName)
            .hasCarrierId(carrierId)
            .hasCellularTechnology(mobileSubtypeNames[subtype])
    }

    @Test
    fun `connected to mobile 3G`(forge: Forge) {
        val subtype = forge.anElementFrom(known3GSubtypes)
        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        broadcastNetworkChangeEvent(
            forge.anElementFrom(knownMobileTypes), subtype, carrierName, carrierId
        )

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_3G)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasCellularTechnology(mobileSubtypeNames[subtype])
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.P)
    fun `connected to mobile 3G API 28+`(forge: Forge) {
        val subtype = forge.anElementFrom(known3GSubtypes)
        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        broadcastNetworkChangeEvent(
            forge.anElementFrom(knownMobileTypes), subtype, carrierName, carrierId
        )

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_3G)
            .hasCarrierName(carrierName)
            .hasCarrierId(carrierId)
            .hasCellularTechnology(mobileSubtypeNames[subtype])
    }

    @Test
    fun `connected to mobile 4G`(forge: Forge) {
        val subtype = forge.anElementFrom(known4GSubtypes)
        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        broadcastNetworkChangeEvent(
            forge.anElementFrom(knownMobileTypes), subtype, carrierName, carrierId
        )

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_4G)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasCellularTechnology(mobileSubtypeNames[subtype])
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.P)
    fun `connected to mobile 4G API 28+`(forge: Forge) {
        val subtype = forge.anElementFrom(known4GSubtypes)
        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        broadcastNetworkChangeEvent(
            forge.anElementFrom(knownMobileTypes), subtype, carrierName, carrierId
        )

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_4G)
            .hasCarrierName(carrierName)
            .hasCarrierId(carrierId)
            .hasCellularTechnology(mobileSubtypeNames[subtype])
    }

    @Test
    fun `connected to mobile 5G`(forge: Forge) {
        val subtype = forge.anElementFrom(known5GSubtypes)
        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        broadcastNetworkChangeEvent(
            forge.anElementFrom(knownMobileTypes), subtype, carrierName, carrierId
        )

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_5G)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasCellularTechnology(mobileSubtypeNames[subtype])
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.P)
    fun `connected to mobile 5G API 28+`(forge: Forge) {
        val subtype = forge.anElementFrom(known5GSubtypes)
        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        broadcastNetworkChangeEvent(
            forge.anElementFrom(knownMobileTypes), subtype, carrierName, carrierId
        )

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_5G)
            .hasCarrierName(carrierName)
            .hasCarrierId(carrierId)
            .hasCellularTechnology(mobileSubtypeNames[subtype])
    }

    @Test
    fun `connected to mobile unknown`(forge: Forge) {
        val subtype = forge.anInt(min = 32)
        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        broadcastNetworkChangeEvent(
            forge.anElementFrom(knownMobileTypes), subtype, carrierName, carrierId
        )

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasCellularTechnology(null)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.P)
    fun `connected to mobile unknown API 28+`(forge: Forge) {
        val subtype = forge.anInt(min = 32)
        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        broadcastNetworkChangeEvent(
            forge.anElementFrom(knownMobileTypes), subtype, carrierName, carrierId
        )

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER)
            .hasCarrierName(carrierName)
            .hasCarrierId(carrierId)
            .hasCellularTechnology(null)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.P)
    fun `connected to mobile unknown carrier`(forge: Forge) {
        broadcastNetworkChangeEvent(
            forge.anElementFrom(knownMobileTypes),
            TelephonyManager.NETWORK_TYPE_UNKNOWN,
            null,
            0
        )

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER)
            .hasCarrierName("Unknown Carrier Name")
            .hasCarrierId(0)
            .hasCellularTechnology(null)
    }

    @Test
    fun `connected to unknown network`(forge: Forge) {
        broadcastNetworkChangeEvent(forge.anInt(min = 6))

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_OTHER)
            .hasCarrierName(null)
            .hasCarrierId(-1)
            .hasCellularTechnology(null)
    }

    // region Internal

    private fun broadcastNetworkChangeEvent(
        networkType: Int,
        networkSubtype: Int = -1,
        carrierName: String? = null,
        carrierId: Int = -1
    ) {
        // Setup AndroidNetworkInfo
        if (networkType < 0) {
            whenever(mockNetworkInfo.isConnected) doReturn false
            whenever(mockNetworkInfo.type) doReturn -1
        } else {
            whenever(mockNetworkInfo.isConnected) doReturn true
            whenever(mockNetworkInfo.type) doReturn networkType
        }
        whenever(mockNetworkInfo.subtype) doReturn networkSubtype

        // Setup Connectivity Manager
        whenever(mockConnectivityManager.activeNetworkInfo) doReturn mockNetworkInfo

        // Setup Telephony Manager
        whenever(mockTelephonyManager.simCarrierIdName) doReturn carrierName
        whenever(mockTelephonyManager.simCarrierId) doReturn carrierId

        testedProvider.onReceive(mockContext, mockIntent)
    }

    // endregion

    companion object {

        private val mobileSubtypeNames = arrayOf(
            "unknown", "GPRS", "Edge", "UMTS", "CDMA", "CDMAEVDORev0", "CDMAEVDORevA", "CDMA1x",
            "HSDPA", "HSUPA", "HSPA", "iDen", "CDMAEVDORevB", "LTE", "eHRPD", "HSPA+", "GSM",
            "TD_SCDMA", "IWLAN", "LTE_CA", "New Radio"
        )

        internal val knownMobileTypes = listOf(
            ConnectivityManager.TYPE_MOBILE,
            ConnectivityManager.TYPE_MOBILE_DUN,
            ConnectivityManager.TYPE_MOBILE_HIPRI,
            ConnectivityManager.TYPE_MOBILE_MMS,
            ConnectivityManager.TYPE_MOBILE_SUPL
        )

        internal val known2GSubtypes = listOf(
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN,
            TelephonyManager.NETWORK_TYPE_GSM
        )

        internal val known3GSubtypes = listOf(
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA
        )
        internal val known4GSubtypes = listOf(
            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_IWLAN,
            19 // @Hide TelephonyManager.NETWORK_TYPE_LTE_CA,
        )
        internal val known5GSubtypes = listOf(
            TelephonyManager.NETWORK_TYPE_NR
        )
    }
}
