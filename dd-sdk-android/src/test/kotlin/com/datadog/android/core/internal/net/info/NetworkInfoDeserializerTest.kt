/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net.info

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
internal class NetworkInfoDeserializerTest {
    lateinit var testedDeserializer: NetworkInfoDeserializer
    lateinit var serializer: NetworkInfoSerializer

    @BeforeEach
    fun `set up`() {
        serializer = NetworkInfoSerializer()
        testedDeserializer = NetworkInfoDeserializer()
    }

    @Test
    fun `M deserialize a model W deserialize`(@Forgery networkInfo: NetworkInfo, forge: Forge) {
        // GIVEN
        val fakeNetworkInfo =
            networkInfo.copy(carrierName = forge.aString(), cellularTechnology = forge.aString())
        val serializedNetworkInfo = serializer.serialize(fakeNetworkInfo)

        // WHEN
        val deserializedNetworkInfo = testedDeserializer.deserialize(serializedNetworkInfo)

        // THEN
        assertThat(deserializedNetworkInfo).isEqualTo(fakeNetworkInfo)
    }

    @Test
    fun `M deserialize a model W deserialize {carrierName is null }`(
        @Forgery networkInfo: NetworkInfo,
        forge: Forge
    ) {
        // GIVEN
        val fakeNetworkInfo =
            networkInfo.copy(carrierName = null, cellularTechnology = forge.aString())
        val serializedNetworkInfo = serializer.serialize(fakeNetworkInfo)

        // WHEN
        val deserializedNetworkInfo = testedDeserializer.deserialize(serializedNetworkInfo)

        // THEN
        assertThat(deserializedNetworkInfo).isEqualTo(fakeNetworkInfo)
    }

    @Test
    fun `M deserialize a model W deserialize {cellularTechnology is null }`(
        @Forgery networkInfo: NetworkInfo,
        forge: Forge
    ) {
        // GIVEN
        val fakeNetworkInfo =
            networkInfo.copy(carrierName = forge.aString(), cellularTechnology = null)
        val serializedNetworkInfo = serializer.serialize(fakeNetworkInfo)

        // WHEN
        val deserializedNetworkInfo = testedDeserializer.deserialize(serializedNetworkInfo)

        // THEN
        assertThat(deserializedNetworkInfo).isEqualTo(fakeNetworkInfo)
    }

    @Test
    fun `𝕄 return null W deserialize { wrong Json format }`() {
        // WHEN
        val deserializedEvent = testedDeserializer.deserialize("{]}")

        // THEN
        assertThat(deserializedEvent).isNull()
    }
}
