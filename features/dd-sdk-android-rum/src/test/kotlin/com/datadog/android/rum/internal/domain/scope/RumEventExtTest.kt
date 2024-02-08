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
    fun `𝕄 return method 𝕎 toMethod()`(
        method: RumResourceMethod
    ) {
        // When
        val result = method.toResourceMethod()

        // Then
        assertThat(result.name).isEqualTo(method.name)
    }

    @ParameterizedTest
    @EnumSource(RumResourceMethod::class)
    fun `𝕄 return method 𝕎 toErrorMethod()`(
        method: RumResourceMethod
    ) {
        // When
        val result = method.toErrorMethod()

        // Then
        assertThat(result.name).isEqualTo(method.name)
    }

    @ParameterizedTest
    @EnumSource(RumResourceKind::class)
    fun `𝕄 return resource type 𝕎 toSchemaType()`(
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
    fun `𝕄 return error source 𝕎 toSchemaSource()`(
        kind: RumErrorSource
    ) {
        // When
        val result = kind.toSchemaSource()

        // Then
        assertThat(kind.name).isEqualTo(result.name)
    }

    @ParameterizedTest
    @EnumSource(RumErrorSourceType::class)
    fun `𝕄 return error source type 𝕎 toSchemaSourceType()`(
        kind: RumErrorSourceType
    ) {
        // When
        val result = kind.toSchemaSourceType()

        // Then
        assertThat(kind.name).isEqualTo(result.name)
    }

    @ParameterizedTest
    @EnumSource(RumActionType::class)
    fun `𝕄 return action type 𝕎 toSchemaType()`(
        type: RumActionType
    ) {
        // When
        val result = type.toSchemaType()

        // Then
        assertThat(type.name).isEqualTo(result.name)
    }

    // region network info

    @Test
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {not connected}`() {
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
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Wifi}`(
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
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Wimax}`(
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
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Ethernet}`(
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
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Bluetooth}`(
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
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Cellular}`(
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
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Other}`(
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
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {not connected}`() {
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
                ErrorEvent.Status.NOT_CONNECTED,
                emptyList(),
                null
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Wifi}`(
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
                ErrorEvent.Status.CONNECTED,
                listOf(ErrorEvent.Interface.WIFI),
                null
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Wimax}`(
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
                ErrorEvent.Status.CONNECTED,
                listOf(ErrorEvent.Interface.WIMAX),
                null
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Ethernet}`(
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
                ErrorEvent.Status.CONNECTED,
                listOf(ErrorEvent.Interface.ETHERNET),
                null
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Bluetooth}`(
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
                ErrorEvent.Status.CONNECTED,
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
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Cellular}`(
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
                ErrorEvent.Status.CONNECTED,
                listOf(ErrorEvent.Interface.CELLULAR),
                cellular = ErrorEvent.Cellular(networkInfo.cellularTechnology, networkInfo.carrierName)
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Other}`(
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
                ErrorEvent.Status.CONNECTED,
                listOf(ErrorEvent.Interface.OTHER),
                null
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {not connected}`() {
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
                LongTaskEvent.Status.NOT_CONNECTED,
                emptyList(),
                null
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Wifi}`(
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
                LongTaskEvent.Status.CONNECTED,
                listOf(LongTaskEvent.Interface.WIFI),
                null
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Wimax}`(
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
                LongTaskEvent.Status.CONNECTED,
                listOf(LongTaskEvent.Interface.WIMAX),
                null
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Ethernet}`(
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
                LongTaskEvent.Status.CONNECTED,
                listOf(LongTaskEvent.Interface.ETHERNET),
                null
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Bluetooth}`(
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
                LongTaskEvent.Status.CONNECTED,
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
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Cellular}`(
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
                LongTaskEvent.Status.CONNECTED,
                listOf(LongTaskEvent.Interface.CELLULAR),
                cellular = LongTaskEvent.Cellular(networkInfo.cellularTechnology, networkInfo.carrierName)
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Other}`(
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
                LongTaskEvent.Status.CONNECTED,
                listOf(LongTaskEvent.Interface.OTHER),
                null
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toActionConnectivity() {not connected}`() {
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
    fun `𝕄 return connectivity 𝕎 toActionConnectivity() {Wifi}`(
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
    fun `𝕄 return connectivity 𝕎 toActionConnectivity() {Wimax}`(
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
    fun `𝕄 return connectivity 𝕎 toActionConnectivity() {Ethernet}`(
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
    fun `𝕄 return connectivity 𝕎 toActionConnectivity() {Bluetooth}`(
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
    fun `𝕄 return connectivity 𝕎 toActionConnectivity() {Cellular}`(
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
    fun `𝕄 return connectivity 𝕎 toActionConnectivity() {Other}`(
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
    fun `𝕄 return connectivity 𝕎 toViewConnectivity() {not connected}`() {
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
                ViewEvent.Status.NOT_CONNECTED,
                emptyList(),
                null
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toViewConnectivity() {Wifi}`(
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
                ViewEvent.Status.CONNECTED,
                listOf(ViewEvent.Interface.WIFI),
                null
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toViewConnectivity() {Wimax}`(
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
                ViewEvent.Status.CONNECTED,
                listOf(ViewEvent.Interface.WIMAX),
                null
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toViewConnectivity() {Ethernet}`(
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
                ViewEvent.Status.CONNECTED,
                listOf(ViewEvent.Interface.ETHERNET),
                null
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toViewConnectivity() {Bluetooth}`(
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
                ViewEvent.Status.CONNECTED,
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
    fun `𝕄 return connectivity 𝕎 toViewConnectivity() {Cellular}`(
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
                ViewEvent.Status.CONNECTED,
                listOf(ViewEvent.Interface.CELLULAR),
                cellular = ViewEvent.Cellular(networkInfo.cellularTechnology, networkInfo.carrierName)
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toViewConnectivity() {Other}`(
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
                ViewEvent.Status.CONNECTED,
                listOf(ViewEvent.Interface.OTHER),
                null
            )
        )
    }

    // endregion

    // region device type conversion

    @ParameterizedTest
    @EnumSource(DeviceType::class)
    fun `𝕄 return schema device type 𝕎 toViewSchemaType()`(
        deviceType: DeviceType
    ) {
        // When
        val schemaDeviceType = deviceType.toViewSchemaType()

        // Then
        assertThat(schemaDeviceType.name).isEqualTo(deviceType.name)
    }

    @ParameterizedTest
    @EnumSource(DeviceType::class)
    fun `𝕄 return schema device type 𝕎 toActionSchemaType()`(
        deviceType: DeviceType
    ) {
        // When
        val schemaDeviceType = deviceType.toActionSchemaType()

        // Then
        assertThat(schemaDeviceType.name).isEqualTo(deviceType.name)
    }

    @ParameterizedTest
    @EnumSource(DeviceType::class)
    fun `𝕄 return schema device type 𝕎 toLongTaskSchemaType()`(
        deviceType: DeviceType
    ) {
        // When
        val schemaDeviceType = deviceType.toLongTaskSchemaType()

        // Then
        assertThat(schemaDeviceType.name).isEqualTo(deviceType.name)
    }

    @ParameterizedTest
    @EnumSource(DeviceType::class)
    fun `𝕄 return schema device type 𝕎 toResourceSchemaType()`(
        deviceType: DeviceType
    ) {
        // When
        val schemaDeviceType = deviceType.toResourceSchemaType()

        // Then
        assertThat(schemaDeviceType.name).isEqualTo(deviceType.name)
    }

    @ParameterizedTest
    @EnumSource(DeviceType::class)
    fun `𝕄 return schema device type 𝕎 toErrorSchemaType()`(
        deviceType: DeviceType
    ) {
        // When
        val schemaDeviceType = deviceType.toErrorSchemaType()

        // Then
        assertThat(schemaDeviceType.name).isEqualTo(deviceType.name)
    }

    // endregion
}
