/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net.info

import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
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
internal class NetworkInfoSerializerTest {

    lateinit var testedSerializer: NetworkInfoSerializer

    @BeforeEach
    fun `set up`() {
        testedSerializer = NetworkInfoSerializer()
    }

    @Test
    fun `M serialize the model W serialize`(@Forgery networkInfo: NetworkInfo, forge: Forge) {
        // GIVEN
        val fakeNetworkInfo = networkInfo.copy(
            carrierName = forge.aString(),
            cellularTechnology = forge.aString()
        )
        // WHEN
        val serializedObject = testedSerializer.serialize(fakeNetworkInfo)
        val jsonObject = JsonParser.parseString(serializedObject).asJsonObject

        // THEN
        assertThat(jsonObject)
            .hasField(NetworkInfoSerializer.CARRIER_NAME, fakeNetworkInfo.carrierName!!)
            .hasField(NetworkInfoSerializer.CARRIER_ID, fakeNetworkInfo.carrierId)
            .hasField(
                NetworkInfoSerializer.CELLULAR_TECHNOLOGY,
                fakeNetworkInfo.cellularTechnology!!
            )
            .hasField(NetworkInfoSerializer.STRENGTH, fakeNetworkInfo.strength)
            .hasField(NetworkInfoSerializer.DOWN_KBPS, fakeNetworkInfo.downKbps)
            .hasField(NetworkInfoSerializer.UP_KBPS, fakeNetworkInfo.upKbps)
            .hasField(
                NetworkInfoSerializer.CONNECTIVITY,
                fakeNetworkInfo.connectivity.toString()
            )
    }

    @Test
    fun `M serialize the model W serialize {carrierName is null}`(
        @Forgery networkInfo: NetworkInfo,
        forge: Forge
    ) {
        // GIVEN
        val fakeNetworkInfo =
            networkInfo.copy(carrierName = null, cellularTechnology = forge.aString())

        // WHEN
        val serializedObject = testedSerializer.serialize(fakeNetworkInfo)
        val jsonObject = JsonParser.parseString(serializedObject).asJsonObject

        // THEN
        assertThat(jsonObject)
            .doesNotHaveField(NetworkInfoSerializer.CARRIER_NAME)
            .hasField(NetworkInfoSerializer.CARRIER_ID, fakeNetworkInfo.carrierId)
            .hasField(
                NetworkInfoSerializer.CELLULAR_TECHNOLOGY,
                fakeNetworkInfo.cellularTechnology!!
            )
            .hasField(NetworkInfoSerializer.STRENGTH, fakeNetworkInfo.strength)
            .hasField(NetworkInfoSerializer.DOWN_KBPS, fakeNetworkInfo.downKbps)
            .hasField(NetworkInfoSerializer.UP_KBPS, fakeNetworkInfo.upKbps)
            .hasField(
                NetworkInfoSerializer.CONNECTIVITY,
                fakeNetworkInfo.connectivity.toString()
            )
    }

    @Test
    fun `M serialize the model W serialize {cellular technology is null}`(
        @Forgery networkInfo: NetworkInfo,
        forge: Forge
    ) {

        // GIVEN
        val fakeNetworkInfo =
            networkInfo.copy(cellularTechnology = null, carrierName = forge.aString())

        // WHEN
        val serializedObject = testedSerializer.serialize(fakeNetworkInfo)
        val jsonObject = JsonParser.parseString(serializedObject).asJsonObject

        // THEN
        assertThat(jsonObject)
            .doesNotHaveField(NetworkInfoSerializer.CELLULAR_TECHNOLOGY)
            .hasField(NetworkInfoSerializer.CARRIER_ID, fakeNetworkInfo.carrierId)
            .hasField(NetworkInfoSerializer.CARRIER_NAME, fakeNetworkInfo.carrierName!!)
            .hasField(NetworkInfoSerializer.STRENGTH, fakeNetworkInfo.strength)
            .hasField(NetworkInfoSerializer.DOWN_KBPS, fakeNetworkInfo.downKbps)
            .hasField(NetworkInfoSerializer.UP_KBPS, fakeNetworkInfo.upKbps)
            .hasField(
                NetworkInfoSerializer.CONNECTIVITY,
                fakeNetworkInfo.connectivity.toString()
            )
    }
}
