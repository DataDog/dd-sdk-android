/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.RumErrorSourceType
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.context.CarrierInfo
import com.datadog.android.v2.api.context.DeviceType
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import com.datadog.android.v2.api.context.NetworkInfo as NetworkInfoV2

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumEventExtTest {

    @ParameterizedTest
    @EnumSource(ResourceEvent.Method::class)
    fun `𝕄 return method 𝕎 toMethod() {valid name}`(
        method: ResourceEvent.Method
    ) {
        // Given
        val name = method.name

        // When
        val result = name.toMethod()

        // Then
        assertThat(result).isEqualTo(method)
    }

    @Test
    fun `𝕄 return GET 𝕎 toMethod() {invalid name}`(
        @StringForgery(type = StringForgeryType.NUMERICAL) name: String
    ) {
        // When
        val result = name.toMethod()

        // Then
        assertThat(result).isEqualTo(ResourceEvent.Method.GET)
    }

    @ParameterizedTest
    @EnumSource(ErrorEvent.Method::class)
    fun `𝕄 return method 𝕎 toErrorMethod() {valid name}`(
        method: ErrorEvent.Method
    ) {
        // Given
        val name = method.name

        // When
        val result = name.toErrorMethod()

        // Then
        assertThat(result).isEqualTo(method)
    }

    @Test
    fun `𝕄 return GET 𝕎 toErrorMethod() {invalid name}`(
        @StringForgery(type = StringForgeryType.NUMERICAL) name: String
    ) {
        // When
        val result = name.toErrorMethod()

        // Then
        assertThat(result).isEqualTo(ErrorEvent.Method.GET)
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
            NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED
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
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Wifi}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_WIFI
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
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Wimax}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_WIMAX
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
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Ethernet}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_ETHERNET
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
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Bluetooth}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_BLUETOOTH
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
        forge: Forge
    ) {
        // Given
        val technology = forge.anAlphabeticalString()
        val networkInfo = NetworkInfo(
            connectivity,
            cellularTechnology = technology
        )

        // When
        val result = networkInfo.toResourceConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ResourceEvent.Connectivity(
                ResourceEvent.Status.CONNECTED,
                listOf(ResourceEvent.Interface.CELLULAR),
                ResourceEvent.Cellular(technology)
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Other}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_OTHER
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
            NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED
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
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Wifi}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_WIFI
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
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Wimax}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_WIMAX
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
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Ethernet}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_ETHERNET
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
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Bluetooth}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_BLUETOOTH
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
        forge: Forge
    ) {
        // Given
        val technology = forge.anAlphabeticalString()
        val networkInfo = NetworkInfo(
            connectivity,
            cellularTechnology = technology
        )

        // When
        val result = networkInfo.toErrorConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ErrorEvent.Connectivity(
                ErrorEvent.Status.CONNECTED,
                listOf(ErrorEvent.Interface.CELLULAR),
                ErrorEvent.Cellular(technology)
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Other}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_OTHER
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
            NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED
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
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Wifi}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_WIFI
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
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Wimax}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_WIMAX
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
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Ethernet}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_ETHERNET
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
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Bluetooth}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_BLUETOOTH
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
        forge: Forge
    ) {
        // Given
        val technology = forge.anAlphabeticalString()
        val networkInfo = NetworkInfo(
            connectivity,
            cellularTechnology = technology
        )

        // When
        val result = networkInfo.toLongTaskConnectivity()

        // Then
        assertThat(result).isEqualTo(
            LongTaskEvent.Connectivity(
                LongTaskEvent.Status.CONNECTED,
                listOf(LongTaskEvent.Interface.CELLULAR),
                LongTaskEvent.Cellular(technology)
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Other}`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_OTHER
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
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {not connected, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_NOT_CONNECTED,
            carrier = null
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
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Wifi, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_WIFI,
            carrier = null
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
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Wimax, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_WIMAX,
            carrier = null
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
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Ethernet, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_ETHERNET,
            carrier = null
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
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Bluetooth, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_BLUETOOTH,
            carrier = null
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
        NetworkInfoV2.Connectivity::class,
        names = [
            "NETWORK_2G", "NETWORK_3G", "NETWORK_4G",
            "NETWORK_5G", "NETWORK_MOBILE_OTHER", "NETWORK_CELLULAR"
        ]
    )
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Cellular, v2}`(
        connectivity: NetworkInfoV2.Connectivity,
        forge: Forge
    ) {
        // Given
        val fakeTechnology = forge.aNullable { forge.anAlphabeticalString() }
        val fakeCarrierName = forge.aNullable { forge.anAlphabeticalString() }
        val networkInfo = NetworkInfoV2(
            connectivity,
            carrier = CarrierInfo(fakeTechnology, fakeCarrierName)
        )

        // When
        val result = networkInfo.toResourceConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ResourceEvent.Connectivity(
                ResourceEvent.Status.CONNECTED,
                listOf(ResourceEvent.Interface.CELLULAR),
                ResourceEvent.Cellular(fakeTechnology, fakeCarrierName)
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Other, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_OTHER,
            carrier = null
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
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {not connected, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_NOT_CONNECTED,
            carrier = null
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
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Wifi, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_WIFI,
            carrier = null
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
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Wimax, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_WIMAX,
            carrier = null
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
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Ethernet, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_ETHERNET,
            carrier = null
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
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Bluetooth, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_BLUETOOTH,
            carrier = null
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
        NetworkInfoV2.Connectivity::class,
        names = [
            "NETWORK_2G", "NETWORK_3G", "NETWORK_4G",
            "NETWORK_5G", "NETWORK_MOBILE_OTHER", "NETWORK_CELLULAR"
        ]
    )
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Cellular, v2}`(
        connectivity: NetworkInfoV2.Connectivity,
        forge: Forge
    ) {
        // Given
        val fakeTechnology = forge.aNullable { forge.anAlphabeticalString() }
        val fakeCarrierName = forge.aNullable { forge.anAlphabeticalString() }
        val networkInfo = NetworkInfoV2(
            connectivity,
            carrier = CarrierInfo(fakeTechnology, fakeCarrierName)
        )

        // When
        val result = networkInfo.toErrorConnectivity()

        // Then
        assertThat(result).isEqualTo(
            ErrorEvent.Connectivity(
                ErrorEvent.Status.CONNECTED,
                listOf(ErrorEvent.Interface.CELLULAR),
                ErrorEvent.Cellular(fakeTechnology, fakeCarrierName)
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Other, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_OTHER,
            carrier = null
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
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {not connected, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_NOT_CONNECTED,
            carrier = null
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
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Wifi, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_WIFI,
            carrier = null
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
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Wimax, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_WIMAX,
            carrier = null
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
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Ethernet, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_ETHERNET,
            carrier = null
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
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Bluetooth, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_BLUETOOTH,
            carrier = null
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
        NetworkInfoV2.Connectivity::class,
        names = [
            "NETWORK_2G", "NETWORK_3G", "NETWORK_4G",
            "NETWORK_5G", "NETWORK_MOBILE_OTHER", "NETWORK_CELLULAR"
        ]
    )
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Cellular, v2}`(
        connectivity: NetworkInfoV2.Connectivity,
        forge: Forge
    ) {
        // Given
        val fakeTechnology = forge.aNullable { forge.anAlphabeticalString() }
        val fakeCarrierName = forge.aNullable { forge.anAlphabeticalString() }
        val networkInfo = NetworkInfoV2(
            connectivity,
            carrier = CarrierInfo(fakeTechnology, fakeCarrierName)
        )

        // When
        val result = networkInfo.toLongTaskConnectivity()

        // Then
        assertThat(result).isEqualTo(
            LongTaskEvent.Connectivity(
                LongTaskEvent.Status.CONNECTED,
                listOf(LongTaskEvent.Interface.CELLULAR),
                LongTaskEvent.Cellular(fakeTechnology, fakeCarrierName)
            )
        )
    }

    @Test
    fun `𝕄 return connectivity 𝕎 toLongTaskConnectivity() {Other, v2}`() {
        // Given
        val networkInfo = NetworkInfoV2(
            connectivity = NetworkInfoV2.Connectivity.NETWORK_OTHER,
            carrier = null
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
