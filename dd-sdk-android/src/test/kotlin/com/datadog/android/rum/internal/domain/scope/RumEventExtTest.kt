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
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumEventExtTest {

    @RepeatedTest(12)
    fun `𝕄 return method 𝕎 toMethod() {valid name}`(
        @Forgery method: ResourceEvent.Method
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

    @RepeatedTest(12)
    fun `𝕄 return method 𝕎 toErrorMethod() {valid name}`(
        @Forgery method: ErrorEvent.Method
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

    @RepeatedTest(22)
    fun `𝕄 return resource type 𝕎 toSchemaType()`(
        @Forgery kind: RumResourceKind
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

    @RepeatedTest(12)
    fun `𝕄 return error source 𝕎 toSchemaSource()`(
        @Forgery kind: RumErrorSource
    ) {
        // When
        val result = kind.toSchemaSource()

        // Then
        assertThat(kind.name).isEqualTo(result.name)
    }

    @RepeatedTest(10)
    fun `𝕄 return action type 𝕎 toSchemaType()`(
        @Forgery type: RumActionType
    ) {
        // When
        val result = type.toSchemaType()

        // Then
        assertThat(type.name).isEqualTo(result.name)
    }

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

    @Test
    fun `𝕄 return connectivity 𝕎 toResourceConnectivity() {Cellular}`(
        forge: Forge
    ) {
        // Given
        val connectivity = forge.anElementFrom(
            NetworkInfo.Connectivity.NETWORK_2G,
            NetworkInfo.Connectivity.NETWORK_3G,
            NetworkInfo.Connectivity.NETWORK_4G,
            NetworkInfo.Connectivity.NETWORK_5G,
            NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER,
            NetworkInfo.Connectivity.NETWORK_CELLULAR
        )
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

    @Test
    fun `𝕄 return connectivity 𝕎 toErrorConnectivity() {Cellular}`(
        forge: Forge
    ) {
        // Given
        val connectivity = forge.anElementFrom(
            NetworkInfo.Connectivity.NETWORK_2G,
            NetworkInfo.Connectivity.NETWORK_3G,
            NetworkInfo.Connectivity.NETWORK_4G,
            NetworkInfo.Connectivity.NETWORK_5G,
            NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER,
            NetworkInfo.Connectivity.NETWORK_CELLULAR
        )
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
}
