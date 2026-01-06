/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("DEPRECATION")

package com.datadog.android.core.internal.net.info

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.telephony.TelephonyManager
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.utils.assertj.NetworkInfoAssert.Companion.assertThat
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import android.net.NetworkInfo as AndroidNetworkInfo

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
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

    @Mock
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @BeforeEach
    fun `set up`() {
        whenever(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityManager)
        whenever(mockContext.getSystemService(Context.TELEPHONY_SERVICE))
            .doReturn(mockTelephonyManager)
        whenever(mockConnectivityManager.activeNetworkInfo) doReturn mockNetworkInfo
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn false

        testedProvider = BroadcastReceiverNetworkInfoProvider(mockBuildSdkVersionProvider)
    }

    @Test
    fun `initial state is not connected`() {
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasCellularTechnology(null)
    }

    @Test
    fun `it will do nothing if unregister is called before register`() {
        // When
        testedProvider.unregister(mockContext)

        // Then
        verifyNoInteractions(mockContext)
    }

    @Test
    fun `it will unregister the receiver only once`() {
        // Given
        val countDownLatch = CountDownLatch(2)
        testedProvider.register(mockContext)

        // When
        Thread {
            testedProvider.unregister(mockContext)
            countDownLatch.countDown()
        }.start()
        Thread {
            testedProvider.unregister(mockContext)
            countDownLatch.countDown()
        }.start()

        // Then
        countDownLatch.await(3, TimeUnit.SECONDS)
        verify(mockContext).unregisterReceiver(testedProvider)
    }

    @Test
    fun `read network info on register`() {
        stubNetworkInfo(ConnectivityManager.TYPE_WIFI, -1)

        testedProvider.register(mockContext)
        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_WIFI)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasCellularTechnology(null)
    }

    @Test
    fun `M delegate to persister W onReceive`() {
        stubNetworkInfo(ConnectivityManager.TYPE_WIFI, -1)
        testedProvider.onReceive(mockContext, mockIntent)

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_WIFI)
            .hasCarrierName(null)
            .hasCarrierId(null)
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
            .hasCarrierId(null)
            .hasCellularTechnology(null)
    }

    @Test
    fun `not connected (connected but no internet)`() {
        // @hide ConnectivityManager.TYPE_NONE = -1
        stubNetworkInfo(-1, -1)
        testedProvider.onReceive(mockContext, mockIntent)

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasCellularTechnology(null)
    }

    @Test
    fun `connected to wifi`() {
        stubNetworkInfo(ConnectivityManager.TYPE_WIFI, -1)
        testedProvider.onReceive(mockContext, mockIntent)

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_WIFI)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasCellularTechnology(null)
    }

    @Test
    fun `connected to ethernet`() {
        stubNetworkInfo(ConnectivityManager.TYPE_ETHERNET, -1)
        testedProvider.onReceive(mockContext, mockIntent)

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_ETHERNET)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasCellularTechnology(null)
    }

    @ParameterizedTest
    @MethodSource("2gSubtypeToMobileTypes")
    fun `connected to mobile 2G`(subtype: NetworkType, mobileType: MobileType, forge: Forge) {
        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        stubNetworkInfo(mobileType.id, subtype.id)
        stubTelephonyManager(carrierName, carrierId)
        testedProvider.onReceive(mockContext, mockIntent)

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_2G)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasCellularTechnology(mobileSubtypeNames[subtype.id])
    }

    @ParameterizedTest
    @MethodSource("2gSubtypeToMobileTypes")
    fun `connected to mobile 2G API 28+`(
        subtype: NetworkType,
        mobileType: MobileType,
        forge: Forge
    ) {
        // GIVEN
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn true

        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        stubNetworkInfo(mobileType.id, subtype.id)
        stubTelephonyManager(carrierName, carrierId)
        testedProvider.onReceive(mockContext, mockIntent)

        // WHEN
        val networkInfo = testedProvider.getLatestNetworkInfo()

        // THEN
        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_2G)
            .hasCarrierName(carrierName)
            .hasCarrierId(carrierId.toLong())
            .hasCellularTechnology(mobileSubtypeNames[subtype.id])
    }

    @ParameterizedTest
    @MethodSource("3gSubtypeToMobileTypes")
    fun `connected to mobile 3G`(subtype: NetworkType, mobileType: MobileType, forge: Forge) {
        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        stubNetworkInfo(mobileType.id, subtype.id)
        stubTelephonyManager(carrierName, carrierId)
        testedProvider.onReceive(mockContext, mockIntent)

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_3G)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasCellularTechnology(mobileSubtypeNames[subtype.id])
    }

    @ParameterizedTest
    @MethodSource("3gSubtypeToMobileTypes")
    fun `connected to mobile 3G API 28+`(
        subtype: NetworkType,
        mobileType: MobileType,
        forge: Forge
    ) {
        // GIVEN
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn true

        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        stubNetworkInfo(mobileType.id, subtype.id)
        stubTelephonyManager(carrierName, carrierId)
        testedProvider.onReceive(mockContext, mockIntent)

        // WHEN
        val networkInfo = testedProvider.getLatestNetworkInfo()

        // THEN
        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_3G)
            .hasCarrierName(carrierName)
            .hasCarrierId(carrierId.toLong())
            .hasCellularTechnology(mobileSubtypeNames[subtype.id])
    }

    @ParameterizedTest
    @MethodSource("4gSubtypeToMobileTypes")
    fun `connected to mobile 4G`(subtype: NetworkType, mobileType: MobileType, forge: Forge) {
        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        stubNetworkInfo(mobileType.id, subtype.id)
        stubTelephonyManager(carrierName, carrierId)
        testedProvider.onReceive(mockContext, mockIntent)

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_4G)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasCellularTechnology(mobileSubtypeNames[subtype.id])
    }

    @ParameterizedTest
    @MethodSource("4gSubtypeToMobileTypes")
    fun `connected to mobile 4G API 28+`(
        subtype: NetworkType,
        mobileType: MobileType,
        forge: Forge
    ) {
        // GIVEN
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn true

        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        stubNetworkInfo(mobileType.id, subtype.id)
        stubTelephonyManager(carrierName, carrierId)
        testedProvider.onReceive(mockContext, mockIntent)

        // WHEN
        val networkInfo = testedProvider.getLatestNetworkInfo()

        // THEN
        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_4G)
            .hasCarrierName(carrierName)
            .hasCarrierId(carrierId.toLong())
            .hasCellularTechnology(mobileSubtypeNames[subtype.id])
    }

    @ParameterizedTest
    @MethodSource("5gSubtypeToMobileTypes")
    fun `connected to mobile 5G`(subtype: NetworkType, mobileType: MobileType, forge: Forge) {
        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        stubNetworkInfo(mobileType.id, subtype.id)
        stubTelephonyManager(carrierName, carrierId)
        testedProvider.onReceive(mockContext, mockIntent)

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_5G)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasCellularTechnology(mobileSubtypeNames[subtype.id])
    }

    @ParameterizedTest
    @MethodSource("5gSubtypeToMobileTypes")
    fun `connected to mobile 5G API 28+`(
        subtype: NetworkType,
        mobileType: MobileType,
        forge: Forge
    ) {
        // GIVEN
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn true

        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        stubNetworkInfo(mobileType.id, subtype.id)
        stubTelephonyManager(carrierName, carrierId)
        testedProvider.onReceive(mockContext, mockIntent)

        // WHEN
        val networkInfo = testedProvider.getLatestNetworkInfo()

        // THEN
        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_5G)
            .hasCarrierName(carrierName)
            .hasCarrierId(carrierId.toLong())
            .hasCellularTechnology(mobileSubtypeNames[subtype.id])
    }

    @ParameterizedTest
    @MethodSource("getKnownMobileTypes")
    fun `connected to mobile unknown`(mobileType: MobileType, forge: Forge) {
        val subtype = forge.anInt(min = 32)
        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        stubNetworkInfo(mobileType.id, subtype)
        stubTelephonyManager(carrierName, carrierId)
        testedProvider.onReceive(mockContext, mockIntent)

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasCellularTechnology(null)
    }

    @ParameterizedTest
    @MethodSource("getKnownMobileTypes")
    fun `connected to mobile unknown API 28+`(mobileType: MobileType, forge: Forge) {
        // GIVEN
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn true

        val subtype = forge.anInt(min = 32)
        val carrierName = forge.anAlphabeticalString()
        val carrierId = forge.aPositiveInt(strict = true)
        stubNetworkInfo(mobileType.id, subtype)
        stubTelephonyManager(carrierName, carrierId)
        testedProvider.onReceive(mockContext, mockIntent)

        // WHEN
        val networkInfo = testedProvider.getLatestNetworkInfo()

        // THEN
        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER)
            .hasCarrierName(carrierName)
            .hasCarrierId(carrierId.toLong())
            .hasCellularTechnology(null)
    }

    @ParameterizedTest
    @MethodSource("getKnownMobileTypes")
    fun `connected to mobile unknown carrier`(mobileType: MobileType) {
        // GIVEN
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn true

        stubNetworkInfo(
            mobileType.id,
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        )
        stubTelephonyManager(null, 0)
        testedProvider.onReceive(mockContext, mockIntent)

        // WHEN
        val networkInfo = testedProvider.getLatestNetworkInfo()

        // THEN
        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER)
            .hasCarrierName("Unknown Carrier Name")
            .hasCarrierId(0)
            .hasCellularTechnology(null)
    }

    @Test
    fun `connected to unknown network`(forge: Forge) {
        stubNetworkInfo(forge.anInt(min = 6), -1)
        testedProvider.onReceive(mockContext, mockIntent)

        val networkInfo = testedProvider.getLatestNetworkInfo()

        assertThat(networkInfo)
            .hasConnectivity(NetworkInfo.Connectivity.NETWORK_OTHER)
            .hasCarrierName(null)
            .hasCarrierId(null)
            .hasCellularTechnology(null)
    }

    // region Internal

    private fun stubTelephonyManager(carrierName: String?, carrierId: Int) {
        whenever(mockTelephonyManager.simCarrierIdName) doReturn carrierName
        whenever(mockTelephonyManager.simCarrierId) doReturn carrierId
    }

    private fun stubNetworkInfo(networkType: Int, networkSubtype: Int) {
        if (networkType < 0) {
            whenever(mockNetworkInfo.isConnected) doReturn false
            whenever(mockNetworkInfo.type) doReturn -1
        } else {
            whenever(mockNetworkInfo.isConnected) doReturn true
            whenever(mockNetworkInfo.type) doReturn networkType
        }
        whenever(mockNetworkInfo.subtype) doReturn networkSubtype
    }

    // endregion

    // ConnectivityManager.TYPE_MOBILE_XXX
    data class MobileType(val name: String, val id: Int)

    // TelephonyManager.NETWORK_TYPE_XXX
    data class NetworkType(val name: String, val id: Int)

    companion object {

        private val mobileTypeNames = mapOf(
            ConnectivityManager.TYPE_MOBILE to "Mobile",
            ConnectivityManager.TYPE_MOBILE_DUN to "Mobile_DUN",
            ConnectivityManager.TYPE_MOBILE_HIPRI to "Mobile_HIPRI",
            ConnectivityManager.TYPE_MOBILE_MMS to "Mobile_MSS",
            ConnectivityManager.TYPE_MOBILE_SUPL to "Mobile_SUPL"
        )

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

        private val knownMobileTypesWithNames = knownMobileTypes
            .map { MobileType(mobileTypeNames.getValue(it), it) }

        private val known2GSubtypesWithNames =
            known2GSubtypes.map { NetworkType(mobileSubtypeNames[it], it) }
        private val known3GSubtypesWithNames =
            known3GSubtypes.map { NetworkType(mobileSubtypeNames[it], it) }
        private val known4GSubtypesWithNames =
            known4GSubtypes.map { NetworkType(mobileSubtypeNames[it], it) }
        private val known5GSubtypesWithNames =
            known5GSubtypes.map { NetworkType(mobileSubtypeNames[it], it) }

        @Suppress("unused")
        @JvmStatic
        fun `2gSubtypeToMobileTypes`(): Stream<Arguments> {
            return allCombinations(known2GSubtypesWithNames, knownMobileTypesWithNames)
                .map { Arguments.of(it.first, it.second) }
                .stream()
        }

        @Suppress("unused")
        @JvmStatic
        fun `3gSubtypeToMobileTypes`(): Stream<Arguments> {
            return allCombinations(known3GSubtypesWithNames, knownMobileTypesWithNames)
                .map { Arguments.of(it.first, it.second) }
                .stream()
        }

        @Suppress("unused")
        @JvmStatic
        fun `4gSubtypeToMobileTypes`(): Stream<Arguments> {
            return allCombinations(known4GSubtypesWithNames, knownMobileTypesWithNames)
                .map { Arguments.of(it.first, it.second) }
                .stream()
        }

        @Suppress("unused")
        @JvmStatic
        fun `5gSubtypeToMobileTypes`(): Stream<Arguments> {
            return allCombinations(known5GSubtypesWithNames, knownMobileTypesWithNames)
                .map { Arguments.of(it.first, it.second) }
                .stream()
        }

        @Suppress("unused")
        @JvmStatic
        fun getKnownMobileTypes(): List<MobileType> {
            return knownMobileTypesWithNames
        }

        private fun allCombinations(
            networkTypes: Iterable<NetworkType>,
            mobileTypes: Iterable<MobileType>
        ): Iterable<Pair<NetworkType, MobileType>> {
            return networkTypes
                .flatMap { item ->
                    mobileTypes.map { item to it }
                }
        }
    }
}
