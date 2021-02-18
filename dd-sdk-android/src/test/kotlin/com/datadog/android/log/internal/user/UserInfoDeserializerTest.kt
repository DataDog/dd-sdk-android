/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.user

import com.datadog.android.core.model.UserInfo
import com.datadog.android.utils.assertj.DatadogMapAnyValueAssert
import com.datadog.android.utils.forge.Configurator
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
internal class UserInfoDeserializerTest {
    lateinit var testedDeserializer: UserInfoDeserializer
    lateinit var serializer: UserInfoSerializer

    @BeforeEach
    fun `set up`() {
        serializer = UserInfoSerializer()
        testedDeserializer = UserInfoDeserializer()
    }

    @Test
    fun `M deserialize a model W deserialize`(@Forgery fakeUserInfo: UserInfo) {
        // GIVEN
        val serializedUserInfo = serializer.serialize(fakeUserInfo)

        // WHEN
        val deserializedUserInfo = testedDeserializer.deserialize(serializedUserInfo)

        // THEN
        assertThat(deserializedUserInfo).isEqualToIgnoringGivenFields(
            fakeUserInfo,
            "extraInfo"
        )

        DatadogMapAnyValueAssert.assertThat(deserializedUserInfo!!.extraInfo)
            .isEqualTo(fakeUserInfo.extraInfo)
    }

    @Test
    fun `𝕄 return null W deserialize { wrong Json format }`() {
        // WHEN
        val deserializedEvent = testedDeserializer.deserialize("{]}")

        // THEN
        assertThat(deserializedEvent).isNull()
    }
}
