/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net.info

import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.context.NetworkInfo
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
internal class NetworkInfoSerializerTest {

    lateinit var testedSerializer: NetworkInfoSerializer

    @BeforeEach
    fun `set up`() {
        testedSerializer = NetworkInfoSerializer()
    }

    @Test
    fun `M serialize the model W serialize`(@Forgery fakeNetworkInfo: NetworkInfo) {
        // WHEN
        val serializedObject = testedSerializer.serialize(fakeNetworkInfo)

        // THEN
        val deserializedObject = NetworkInfo.fromJson(serializedObject)

        assertThat(deserializedObject).isEqualTo(fakeNetworkInfo)
    }
}
