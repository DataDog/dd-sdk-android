/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.api.context.DeviceType
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.internal.RumErrorSourceType
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.TimeseriesCpuEvent
import com.datadog.android.rum.model.TimeseriesMemoryEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class RumEventExtTest {

    @ParameterizedTest
    @EnumSource(RumResourceMethod::class)
    fun `M return method W toMethod()`(
        method: RumResourceMethod
    ) {
        // When
        val result = method.toResourceMethod()

        // Then
        assertThat(result.name).isEqualTo(method.name)
    }

    @ParameterizedTest
    @EnumSource(RumResourceMethod::class)
    fun `M return method W toErrorMethod()`(
        method: RumResourceMethod
    ) {
        // When
        val result = method.toErrorMethod()

        // Then
        assertThat(result.name).isEqualTo(method.name)
    }

    @ParameterizedTest
    @EnumSource(RumResourceKind::class)
    fun `M return resource type W toSchemaType()`(
        kind: RumResourceKind
    ) {
        // When
        val result = kind.toSchemaType()

        // Then
        if (kind == RumResourceKind.UNKNOWN) {
            assertThat(result).isEqualTo(ResourceEvent.ResourceType.OTHER)
        } else {
            assertThat(kind.name).isEqualTo(result.name)
        }
    }

    @ParameterizedTest
    @EnumSource(RumErrorSource::class)
    fun `M return error source W toSchemaSource()`(
        kind: RumErrorSource
    ) {
        // When
        val result = kind.toSchemaSource()

        // Then
        assertThat(kind.name).isEqualTo(result.name)
    }

    @ParameterizedTest
    @EnumSource(RumErrorSourceType::class)
    fun `M return error source type W toSchemaSourceType()`(
        kind: RumErrorSourceType
    ) {
        // When
        val result = kind.toSchemaSourceType()

        // Then
        assertThat(kind.name).isEqualTo(result.name)
    }

    @ParameterizedTest
    @EnumSource(RumActionType::class)
    fun `M return action type W toSchemaType()`(
        type: RumActionType
    ) {
        // When
        val result = type.toSchemaType()

        // Then
        assertThat(type.name).isEqualTo(result.name)
    }

    // region network info

    @Test
    fun `M return connectivity W toResourceConnectivity() {not connected}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED,
            carrierName = null,
            carrierId = null,
            upKbps = null,
            downKbps = null,
            strength = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toResourceConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ResourceEvent.Connectivity(
                ResourceEvent.Status.NOT_CONNECTED,
                emptyList(),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toResourceConnectivity() {Wifi}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_WIFI,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toResourceConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ResourceEvent.Connectivity(
                ResourceEvent.Status.CONNECTED,
                listOf(ResourceEvent.Interface.WIFI),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toResourceConnectivity() {Wimax}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_WIMAX,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toResourceConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ResourceEvent.Connectivity(
                ResourceEvent.Status.CONNECTED,
                listOf(ResourceEvent.Interface.WIMAX),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toResourceConnectivity() {Ethernet}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_ETHERNET,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toResourceConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ResourceEvent.Connectivity(
                ResourceEvent.Status.CONNECTED,
                listOf(ResourceEvent.Interface.ETHERNET),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toResourceConnectivity() {Bluetooth}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_BLUETOOTH,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toResourceConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ResourceEvent.Connectivity(
                ResourceEvent.Status.CONNECTED,
                listOf(ResourceEvent.Interface.BLUETOOTH),
                null
            )
        )
    }

    @ParameterizedTest
    @EnumSource(
        NetworkInfo.Connectivity::class,
        names = [
            "NETWORK_2G", "NETWORK_3G", "NETWORK_4G",
            "NETWORK_5G", "NETWORK_MOBILE_OTHER", "NETWORK_CELLULAR"
        ]
    )
    fun `M return connectivity W toResourceConnectivity() {Cellular}`(
        connectivity: NetworkInfo.Connectivity,
        @Forgery fakeNetworkInfo: NetworkInfo,
        @StringForgery fakeCarrierName: String,
        @StringForgery fakeCellularTechnology: String
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = connectivity,
            carrierName = fakeCarrierName,
            cellularTechnology = fakeCellularTechnology
        )

        // When
        val result = networkInfo.toResourceConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ResourceEvent.Connectivity(
                ResourceEvent.Status.CONNECTED,
                listOf(ResourceEvent.Interface.CELLULAR),
                cellular = ResourceEvent.Cellular(networkInfo.cellularTechnology, networkInfo.carrierName)
            )
        )
    }

    @Test
    fun `M return connectivity W toResourceConnectivity() {Other}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_OTHER,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toResourceConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ResourceEvent.Connectivity(
                ResourceEvent.Status.CONNECTED,
                listOf(ResourceEvent.Interface.OTHER),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toErrorConnectivity() {not connected}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED,
            carrierName = null,
            carrierId = null,
            upKbps = null,
            downKbps = null,
            strength = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toErrorConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ErrorEvent.Connectivity(
                ErrorEvent.ConnectivityStatus.NOT_CONNECTED,
                emptyList(),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toErrorConnectivity() {Wifi}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_WIFI,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toErrorConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ErrorEvent.Connectivity(
                ErrorEvent.ConnectivityStatus.CONNECTED,
                listOf(ErrorEvent.Interface.WIFI),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toErrorConnectivity() {Wimax}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_WIMAX,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toErrorConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ErrorEvent.Connectivity(
                ErrorEvent.ConnectivityStatus.CONNECTED,
                listOf(ErrorEvent.Interface.WIMAX),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toErrorConnectivity() {Ethernet}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_ETHERNET,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toErrorConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ErrorEvent.Connectivity(
                ErrorEvent.ConnectivityStatus.CONNECTED,
                listOf(ErrorEvent.Interface.ETHERNET),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toErrorConnectivity() {Bluetooth}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_BLUETOOTH,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toErrorConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ErrorEvent.Connectivity(
                ErrorEvent.ConnectivityStatus.CONNECTED,
                listOf(ErrorEvent.Interface.BLUETOOTH),
                null
            )
        )
    }

    @ParameterizedTest
    @EnumSource(
        NetworkInfo.Connectivity::class,
        names = [
            "NETWORK_2G", "NETWORK_3G", "NETWORK_4G",
            "NETWORK_5G", "NETWORK_MOBILE_OTHER", "NETWORK_CELLULAR"
        ]
    )
    fun `M return connectivity W toErrorConnectivity() {Cellular}`(
        connectivity: NetworkInfo.Connectivity,
        @Forgery fakeNetworkInfo: NetworkInfo,
        @StringForgery fakeCarrierName: String,
        @StringForgery fakeCellularTechnology: String
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = connectivity,
            carrierName = fakeCarrierName,
            cellularTechnology = fakeCellularTechnology
        )

        // When
        val result = networkInfo.toErrorConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ErrorEvent.Connectivity(
                ErrorEvent.ConnectivityStatus.CONNECTED,
                listOf(ErrorEvent.Interface.CELLULAR),
                cellular = ErrorEvent.Cellular(networkInfo.cellularTechnology, networkInfo.carrierName)
            )
        )
    }

    @Test
    fun `M return connectivity W toErrorConnectivity() {Other}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_OTHER,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toErrorConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ErrorEvent.Connectivity(
                ErrorEvent.ConnectivityStatus.CONNECTED,
                listOf(ErrorEvent.Interface.OTHER),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toLongTaskConnectivity() {not connected}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED,
            carrierName = null,
            carrierId = null,
            upKbps = null,
            downKbps = null,
            strength = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toLongTaskConnectivity()

        // Then
        assertThat(result).isEqualTo(
            LongTaskEvent.Connectivity(
                LongTaskEvent.ConnectivityStatus.NOT_CONNECTED,
                emptyList(),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toLongTaskConnectivity() {Wifi}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_WIFI,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toLongTaskConnectivity()

        // Then
        assertThat(result).isEqualTo(
            LongTaskEvent.Connectivity(
                LongTaskEvent.ConnectivityStatus.CONNECTED,
                listOf(LongTaskEvent.Interface.WIFI),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toLongTaskConnectivity() {Wimax}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_WIMAX,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toLongTaskConnectivity()

        // Then
        assertThat(result).isEqualTo(
            LongTaskEvent.Connectivity(
                LongTaskEvent.ConnectivityStatus.CONNECTED,
                listOf(LongTaskEvent.Interface.WIMAX),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toLongTaskConnectivity() {Ethernet}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_ETHERNET,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toLongTaskConnectivity()

        // Then
        assertThat(result).isEqualTo(
            LongTaskEvent.Connectivity(
                LongTaskEvent.ConnectivityStatus.CONNECTED,
                listOf(LongTaskEvent.Interface.ETHERNET),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toLongTaskConnectivity() {Bluetooth}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_BLUETOOTH,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toLongTaskConnectivity()

        // Then
        assertThat(result).isEqualTo(
            LongTaskEvent.Connectivity(
                LongTaskEvent.ConnectivityStatus.CONNECTED,
                listOf(LongTaskEvent.Interface.BLUETOOTH),
                null
            )
        )
    }

    @ParameterizedTest
    @EnumSource(
        NetworkInfo.Connectivity::class,
        names = [
            "NETWORK_2G", "NETWORK_3G", "NETWORK_4G",
            "NETWORK_5G", "NETWORK_MOBILE_OTHER", "NETWORK_CELLULAR"
        ]
    )
    fun `M return connectivity W toLongTaskConnectivity() {Cellular}`(
        connectivity: NetworkInfo.Connectivity,
        @Forgery fakeNetworkInfo: NetworkInfo,
        @StringForgery fakeCarrierName: String,
        @StringForgery fakeCellularTechnology: String
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = connectivity,
            carrierName = fakeCarrierName,
            cellularTechnology = fakeCellularTechnology
        )

        // When
        val result = networkInfo.toLongTaskConnectivity()

        // Then
        assertThat(result).isEqualTo(
            LongTaskEvent.Connectivity(
                LongTaskEvent.ConnectivityStatus.CONNECTED,
                listOf(LongTaskEvent.Interface.CELLULAR),
                cellular = LongTaskEvent.Cellular(networkInfo.cellularTechnology, networkInfo.carrierName)
            )
        )
    }

    @Test
    fun `M return connectivity W toLongTaskConnectivity() {Other}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_OTHER,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toLongTaskConnectivity()

        // Then
        assertThat(result).isEqualTo(
            LongTaskEvent.Connectivity(
                LongTaskEvent.ConnectivityStatus.CONNECTED,
                listOf(LongTaskEvent.Interface.OTHER),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toActionConnectivity() {not connected}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED,
            carrierName = null,
            carrierId = null,
            upKbps = null,
            downKbps = null,
            strength = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toActionConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ActionEvent.Connectivity(
                ActionEvent.Status.NOT_CONNECTED,
                emptyList(),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toActionConnectivity() {Wifi}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_WIFI,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toActionConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ActionEvent.Connectivity(
                ActionEvent.Status.CONNECTED,
                listOf(ActionEvent.Interface.WIFI),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toActionConnectivity() {Wimax}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_WIMAX,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toActionConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ActionEvent.Connectivity(
                ActionEvent.Status.CONNECTED,
                listOf(ActionEvent.Interface.WIMAX),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toActionConnectivity() {Ethernet}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_ETHERNET,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toActionConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ActionEvent.Connectivity(
                ActionEvent.Status.CONNECTED,
                listOf(ActionEvent.Interface.ETHERNET),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toActionConnectivity() {Bluetooth}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_BLUETOOTH,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toActionConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ActionEvent.Connectivity(
                ActionEvent.Status.CONNECTED,
                listOf(ActionEvent.Interface.BLUETOOTH),
                null
            )
        )
    }

    @ParameterizedTest
    @EnumSource(
        NetworkInfo.Connectivity::class,
        names = [
            "NETWORK_2G", "NETWORK_3G", "NETWORK_4G",
            "NETWORK_5G", "NETWORK_MOBILE_OTHER", "NETWORK_CELLULAR"
        ]
    )
    fun `M return connectivity W toActionConnectivity() {Cellular}`(
        connectivity: NetworkInfo.Connectivity,
        @Forgery fakeNetworkInfo: NetworkInfo,
        @StringForgery fakeCarrierName: String,
        @StringForgery fakeCellularTechnology: String
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = connectivity,
            carrierName = fakeCarrierName,
            cellularTechnology = fakeCellularTechnology
        )

        // When
        val result = networkInfo.toActionConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ActionEvent.Connectivity(
                ActionEvent.Status.CONNECTED,
                listOf(ActionEvent.Interface.CELLULAR),
                cellular = ActionEvent.Cellular(networkInfo.cellularTechnology, networkInfo.carrierName)
            )
        )
    }

    @Test
    fun `M return connectivity W toActionConnectivity() {Other}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_OTHER,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toActionConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ActionEvent.Connectivity(
                ActionEvent.Status.CONNECTED,
                listOf(ActionEvent.Interface.OTHER),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toViewConnectivity() {not connected}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED,
            carrierName = null,
            carrierId = null,
            upKbps = null,
            downKbps = null,
            strength = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toViewConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ViewEvent.Connectivity(
                ViewEvent.ConnectivityStatus.NOT_CONNECTED,
                emptyList(),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toViewConnectivity() {Wifi}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_WIFI,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toViewConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ViewEvent.Connectivity(
                ViewEvent.ConnectivityStatus.CONNECTED,
                listOf(ViewEvent.Interface.WIFI),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toViewConnectivity() {Wimax}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_WIMAX,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toViewConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ViewEvent.Connectivity(
                ViewEvent.ConnectivityStatus.CONNECTED,
                listOf(ViewEvent.Interface.WIMAX),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toViewConnectivity() {Ethernet}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_ETHERNET,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toViewConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ViewEvent.Connectivity(
                ViewEvent.ConnectivityStatus.CONNECTED,
                listOf(ViewEvent.Interface.ETHERNET),
                null
            )
        )
    }

    @Test
    fun `M return connectivity W toViewConnectivity() {Bluetooth}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_BLUETOOTH,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toViewConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ViewEvent.Connectivity(
                ViewEvent.ConnectivityStatus.CONNECTED,
                listOf(ViewEvent.Interface.BLUETOOTH),
                null
            )
        )
    }

    @ParameterizedTest
    @EnumSource(
        NetworkInfo.Connectivity::class,
        names = [
            "NETWORK_2G", "NETWORK_3G", "NETWORK_4G",
            "NETWORK_5G", "NETWORK_MOBILE_OTHER", "NETWORK_CELLULAR"
        ]
    )
    fun `M return connectivity W toViewConnectivity() {Cellular}`(
        connectivity: NetworkInfo.Connectivity,
        @Forgery fakeNetworkInfo: NetworkInfo,
        @StringForgery fakeCarrierName: String,
        @StringForgery fakeCellularTechnology: String
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = connectivity,
            carrierName = fakeCarrierName,
            cellularTechnology = fakeCellularTechnology
        )

        // When
        val result = networkInfo.toViewConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ViewEvent.Connectivity(
                ViewEvent.ConnectivityStatus.CONNECTED,
                listOf(ViewEvent.Interface.CELLULAR),
                cellular = ViewEvent.Cellular(networkInfo.cellularTechnology, networkInfo.carrierName)
            )
        )
    }

    @Test
    fun `M return connectivity W toViewConnectivity() {Other}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = NetworkInfo.Connectivity.NETWORK_OTHER,
            carrierName = null,
            carrierId = null,
            cellularTechnology = null
        )

        // When
        val result = networkInfo.toViewConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ViewEvent.Connectivity(
                ViewEvent.ConnectivityStatus.CONNECTED,
                listOf(ViewEvent.Interface.OTHER),
                null
            )
        )
    }

    @ParameterizedTest
    @EnumSource(NetworkInfo.Connectivity::class)
    fun `M return connectivity W toTimeseriesCpuConnectivity()`(
        connectivity: NetworkInfo.Connectivity,
        @Forgery fakeNetworkInfo: NetworkInfo,
        @StringForgery fakeCarrierName: String,
        @StringForgery fakeCellularTechnology: String
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = connectivity,
            carrierName = fakeCarrierName,
            cellularTechnology = fakeCellularTechnology
        )

        // When
        val result = networkInfo.toTimeseriesCpuConnectivity()

        // Then
        assertThat(result.status.name).isEqualTo(expectedStatusName(connectivity))
        assertThat(result.interfaces?.map { it.name })
            .isEqualTo(EXPECTED_INTERFACE_NAMES.getValue(connectivity))
        assertThat(result.cellular)
            .isEqualTo(TimeseriesCpuEvent.Cellular(fakeCellularTechnology, fakeCarrierName))
    }

    @Test
    fun `M return null cellular W toTimeseriesCpuConnectivity() {no carrier info}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(carrierName = null, cellularTechnology = null)

        // When
        val result = networkInfo.toTimeseriesCpuConnectivity()

        // Then
        assertThat(result.cellular).isNull()
    }

    @ParameterizedTest
    @EnumSource(NetworkInfo.Connectivity::class)
    fun `M return connectivity W toTimeseriesMemoryConnectivity()`(
        connectivity: NetworkInfo.Connectivity,
        @Forgery fakeNetworkInfo: NetworkInfo,
        @StringForgery fakeCarrierName: String,
        @StringForgery fakeCellularTechnology: String
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(
            connectivity = connectivity,
            carrierName = fakeCarrierName,
            cellularTechnology = fakeCellularTechnology
        )

        // When
        val result = networkInfo.toTimeseriesMemoryConnectivity()

        // Then
        assertThat(result.status.name).isEqualTo(expectedStatusName(connectivity))
        assertThat(result.interfaces?.map { it.name })
            .isEqualTo(EXPECTED_INTERFACE_NAMES.getValue(connectivity))
        assertThat(result.cellular)
            .isEqualTo(TimeseriesMemoryEvent.Cellular(fakeCellularTechnology, fakeCarrierName))
    }

    @Test
    fun `M return null cellular W toTimeseriesMemoryConnectivity() {no carrier info}`(
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        val networkInfo = fakeNetworkInfo.copy(carrierName = null, cellularTechnology = null)

        // When
        val result = networkInfo.toTimeseriesMemoryConnectivity()

        // Then
        assertThat(result.cellular).isNull()
    }

    // endregion

    // region device type conversion

    @ParameterizedTest
    @EnumSource(DeviceType::class)
    fun `M return schema device type W toViewSchemaType()`(
        deviceType: DeviceType
    ) {
        // When
        val schemaDeviceType = deviceType.toViewSchemaType()

        // Then
        assertThat(schemaDeviceType.name).isEqualTo(deviceType.name)
    }

    @ParameterizedTest
    @EnumSource(DeviceType::class)
    fun `M return schema device type W toActionSchemaType()`(
        deviceType: DeviceType
    ) {
        // When
        val schemaDeviceType = deviceType.toActionSchemaType()

        // Then
        assertThat(schemaDeviceType.name).isEqualTo(deviceType.name)
    }

    @ParameterizedTest
    @EnumSource(DeviceType::class)
    fun `M return schema device type W toLongTaskSchemaType()`(
        deviceType: DeviceType
    ) {
        // When
        val schemaDeviceType = deviceType.toLongTaskSchemaType()

        // Then
        assertThat(schemaDeviceType.name).isEqualTo(deviceType.name)
    }

    @ParameterizedTest
    @EnumSource(DeviceType::class)
    fun `M return schema device type W toResourceSchemaType()`(
        deviceType: DeviceType
    ) {
        // When
        val schemaDeviceType = deviceType.toResourceSchemaType()

        // Then
        assertThat(schemaDeviceType.name).isEqualTo(deviceType.name)
    }

    @ParameterizedTest
    @EnumSource(DeviceType::class)
    fun `M return schema device type W toErrorSchemaType()`(
        deviceType: DeviceType
    ) {
        // When
        val schemaDeviceType = deviceType.toErrorSchemaType()

        // Then
        assertThat(schemaDeviceType.name).isEqualTo(deviceType.name)
    }

    @ParameterizedTest
    @EnumSource(DeviceType::class)
    fun `M return schema device type W toTimeseriesCpuSchemaType()`(
        deviceType: DeviceType
    ) {
        // When
        val schemaDeviceType = deviceType.toTimeseriesCpuSchemaType()

        // Then
        assertThat(schemaDeviceType.name).isEqualTo(deviceType.name)
    }

    @ParameterizedTest
    @EnumSource(DeviceType::class)
    fun `M return schema device type W toTimeseriesMemorySchemaType()`(
        deviceType: DeviceType
    ) {
        // When
        val schemaDeviceType = deviceType.toTimeseriesMemorySchemaType()

        // Then
        assertThat(schemaDeviceType.name).isEqualTo(deviceType.name)
    }

    // endregion

    companion object {

        // Interface enums are generated per event type but share their names, so a single
        // name-based table covers both timeseries schemas. getValue() fails loudly if a new
        // Connectivity value is added without updating the mapping.
        private val EXPECTED_INTERFACE_NAMES = mapOf(
            NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED to emptyList<String>(),
            NetworkInfo.Connectivity.NETWORK_ETHERNET to listOf("ETHERNET"),
            NetworkInfo.Connectivity.NETWORK_WIFI to listOf("WIFI"),
            NetworkInfo.Connectivity.NETWORK_WIMAX to listOf("WIMAX"),
            NetworkInfo.Connectivity.NETWORK_BLUETOOTH to listOf("BLUETOOTH"),
            NetworkInfo.Connectivity.NETWORK_2G to listOf("CELLULAR"),
            NetworkInfo.Connectivity.NETWORK_3G to listOf("CELLULAR"),
            NetworkInfo.Connectivity.NETWORK_4G to listOf("CELLULAR"),
            NetworkInfo.Connectivity.NETWORK_5G to listOf("CELLULAR"),
            NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER to listOf("CELLULAR"),
            NetworkInfo.Connectivity.NETWORK_CELLULAR to listOf("CELLULAR"),
            NetworkInfo.Connectivity.NETWORK_OTHER to listOf("OTHER")
        )

        private fun expectedStatusName(connectivity: NetworkInfo.Connectivity): String {
            return if (connectivity == NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED) {
                "NOT_CONNECTED"
            } else {
                "CONNECTED"
            }
        }
    }
}
