/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.user

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.UserInfo
import com.datadog.android.utils.assertj.DeserializedMapAssert
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class UserInfoDeserializerTest {

    lateinit var testedDeserializer: UserInfoDeserializer

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedDeserializer = UserInfoDeserializer(mockInternalLogger)
    }

    @Test
    fun `M deserialize a model W deserialize`(@Forgery fakeUserInfo: UserInfo) {
        // GIVEN
        val serializedUserInfo = fakeUserInfo.toJson().asJsonObject.toString()

        // WHEN
        val deserializedUserInfo = testedDeserializer.deserialize(serializedUserInfo)

        // THEN
        assertThat(deserializedUserInfo)
            .usingRecursiveComparison()
            .ignoringFields("additionalProperties")
            .isEqualTo(fakeUserInfo)

        DeserializedMapAssert.assertThat(deserializedUserInfo!!.additionalProperties)
            .isEqualTo(fakeUserInfo.additionalProperties)
    }

    @Test
    fun `M return null W deserialize { wrong Json format }`() {
        // WHEN
        val deserializedEvent = testedDeserializer.deserialize("{]}")

        // THEN
        assertThat(deserializedEvent).isNull()
    }
}
