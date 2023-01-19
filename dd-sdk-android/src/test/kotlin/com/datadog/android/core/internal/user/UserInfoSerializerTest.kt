/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.user

import com.datadog.android.utils.assertj.DeserializedMapAssert.Companion.assertThat
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.context.UserInfo
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
internal class UserInfoSerializerTest {

    lateinit var testedSerializer: UserInfoSerializer

    @BeforeEach
    fun `set up`() {
        testedSerializer = UserInfoSerializer()
    }

    @Test
    fun `M serialize the event W serialize`(@Forgery fakeUserInfo: UserInfo) {
        // WHEN
        val serializedObject = testedSerializer.serialize(fakeUserInfo)
        val deserializedUserInfo = UserInfo.fromJson(serializedObject)

        // THEN
        assertThat(deserializedUserInfo)
            .usingRecursiveComparison()
            .ignoringFields("additionalProperties")
            .isEqualTo(fakeUserInfo)

        assertThat(deserializedUserInfo.additionalProperties)
            .isEqualTo(fakeUserInfo.additionalProperties)
    }
}
