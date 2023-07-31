/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.user

import com.datadog.android.api.context.UserInfo
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogUserInfoProviderTest {

    lateinit var testedProvider: DatadogUserInfoProvider

    @Mock
    lateinit var mockWriter: DataWriter<UserInfo>

    @BeforeEach
    fun `set up`() {
        testedProvider = DatadogUserInfoProvider(mockWriter)
    }

    @Test
    fun `𝕄 return default userInfo 𝕎 getUserInfo()`() {
        // When
        val result = testedProvider.getUserInfo()

        // Then
        assertThat(result).isEqualTo(UserInfo())
    }

    @Test
    fun `𝕄 return saved userInfo 𝕎 setUserInfo() and getUserInfo()`(
        @Forgery userInfo: UserInfo
    ) {
        // When
        testedProvider.setUserInfo(userInfo)
        val result = testedProvider.getUserInfo()

        // Then
        assertThat(result).isEqualTo(userInfo)
    }

    @Test
    fun `M delegate to persister W setUserInfo`(@Forgery userInfo: UserInfo) {
        // When
        testedProvider.setUserInfo(userInfo)

        // Then
        verify(mockWriter).write(userInfo)
    }

    @Test
    fun `𝕄 keep existing properties 𝕎 setExtraProperties() is called`(
        @Forgery userInfo: UserInfo,
        @StringForgery forge: Forge
    ) {
        // Given
        val customProperties = forge.exhaustiveAttributes()
        testedProvider.setUserInfo(
            userInfo.copy(
                additionalProperties = customProperties
            )
        )

        // When
        testedProvider.addUserProperties(customProperties)
        // Then
        assertThat(
            testedProvider.getUserInfo().additionalProperties
        ).isEqualTo(customProperties)
    }

    @Test
    fun `𝕄 keep new property key 𝕎 setExtraProperties() is called and the key already exists`(
        @Forgery userInfo: UserInfo,
        @StringForgery key: String,
        @StringForgery value1: String,
        @StringForgery value2: String
    ) {
        // Given
        testedProvider.setUserInfo(
            userInfo.copy(additionalProperties = mutableMapOf(key to value1))
        )

        // When
        testedProvider.addUserProperties(mapOf(key to value2))

        // Then
        assertThat(
            testedProvider.getUserInfo().additionalProperties
        ).isEqualTo(
            mapOf(key to value2)
        )
    }
}
