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
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.MapForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
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

    private lateinit var testedProvider: DatadogUserInfoProvider

    @Mock
    lateinit var mockWriter: DataWriter<UserInfo>

    @BeforeEach
    fun `set up`() {
        testedProvider = DatadogUserInfoProvider(mockWriter)
    }

    @Test
    fun `M return default userInfo W getUserInfo()`() {
        // When
        val result = testedProvider.getUserInfo()

        // Then
        assertThat(result).isEqualTo(UserInfo())
    }

    @Test
    fun `M return saved userInfo W setUserInfo() and getUserInfo()`(
        @Forgery userInfo: UserInfo
    ) {
        // When
        testedProvider.setUserInfo(userInfo.id, userInfo.name, userInfo.email, userInfo.additionalProperties)
        val result = testedProvider.getUserInfo()

        // Then
        assertThat(result).isEqualTo(userInfo)
    }

    @Test
    fun `M delegate to persister W setUserInfo`(@Forgery userInfo: UserInfo) {
        // When
        testedProvider.setUserInfo(userInfo.id, userInfo.name, userInfo.email, userInfo.additionalProperties)

        // Then
        verify(mockWriter).write(userInfo)
    }

    @Test
    fun `M keep existing properties W addUserProperties() is called`(
        @Forgery userInfo: UserInfo,
        @StringForgery forge: Forge
    ) {
        // Given
        val customProperties = forge.exhaustiveAttributes()
        testedProvider.setUserInfo(userInfo.id, userInfo.name, userInfo.email, customProperties)

        // When
        testedProvider.addUserProperties(customProperties)
        // Then
        assertThat(
            testedProvider.getUserInfo().additionalProperties
        ).isEqualTo(customProperties)
    }

    @Test
    fun `M use immutable properties W addUserProperties() is called { changing properties values }`(
        @StringForgery forge: Forge
    ) {
        // Given
        val fakeProperties = forge.exhaustiveAttributes()
        val fakeExpectedProperties = fakeProperties.toMap()
        val fakeMutableProperties = fakeProperties.toMutableMap()
        testedProvider.addUserProperties(fakeMutableProperties)

        // When
        fakeMutableProperties.keys.forEach {
            fakeMutableProperties[it] = forge.anAlphabeticalString()
        }

        // Then
        assertThat(
            testedProvider.getUserInfo().additionalProperties
        ).isEqualTo(fakeExpectedProperties)
    }

    @Test
    fun `M use immutable properties W addUserProperties() is called { adding properties }`(
        @StringForgery forge: Forge
    ) {
        // Given
        val fakeProperties = forge.exhaustiveAttributes()
        val fakeExpectedProperties = fakeProperties.toMap()
        val fakeMutableProperties = fakeProperties.toMutableMap()
        testedProvider.addUserProperties(fakeMutableProperties)

        // When
        repeat(forge.anInt(1, 10)) {
            fakeMutableProperties[forge.anAlphabeticalString()] = forge.anAlphabeticalString()
        }

        // Then
        assertThat(
            testedProvider.getUserInfo().additionalProperties
        ).isEqualTo(fakeExpectedProperties)
    }

    @Test
    fun `M use immutable properties W addUserProperties() is called { removing properties }`(
        @StringForgery forge: Forge
    ) {
        // Given
        val fakeProperties = forge.exhaustiveAttributes()
        val fakeExpectedProperties = fakeProperties.toMap()
        val fakeMutableProperties = fakeProperties.toMutableMap()
        testedProvider.addUserProperties(fakeMutableProperties)

        // When
        repeat(forge.anInt(1, fakeMutableProperties.size + 1)) {
            fakeMutableProperties.remove(fakeMutableProperties.keys.random())
        }

        // Then
        assertThat(
            testedProvider.getUserInfo().additionalProperties
        ).isEqualTo(fakeExpectedProperties)
    }

    @Test
    fun `M keep new property key W addUserProperties() is called and the key already exists`(
        @Forgery userInfo: UserInfo,
        @StringForgery key: String,
        @StringForgery value1: String,
        @StringForgery value2: String
    ) {
        // Given
        testedProvider.setUserInfo(userInfo.id, userInfo.name, userInfo.email, mutableMapOf(key to value1))

        // When
        testedProvider.addUserProperties(mapOf(key to value2))

        // Then
        assertThat(
            testedProvider.getUserInfo().additionalProperties
        ).isEqualTo(
            mapOf(key to value2)
        )
    }

    @Test
    fun `M use immutable values W setUserInfo { changing properties values }()`(
        forge: Forge,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @StringForgery(regex = "\\w+@\\w+") email: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)])
        ) fakeUserProperties: Map<String, String>
    ) {
        // Given
        val fakeMutableUserProperties = fakeUserProperties.toMutableMap()
        val fakeExpectedUserProperties = fakeUserProperties.toMap()
        testedProvider.setUserInfo(id, name, email, fakeMutableUserProperties)

        // When
        fakeMutableUserProperties.keys.forEach { key ->
            fakeMutableUserProperties[key] = forge.anAlphaNumericalString()
        }

        // Then
        assertThat(testedProvider.getUserInfo().additionalProperties).isEqualTo(fakeExpectedUserProperties)
    }

    @Test
    fun `M use immutable values W setUserInfo { adding properties }()`(
        forge: Forge,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @StringForgery(regex = "\\w+@\\w+") email: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)])
        ) fakeUserProperties: Map<String, String>
    ) {
        // Given
        val fakeMutableUserProperties = fakeUserProperties.toMutableMap()
        val fakeExpectedUserProperties = fakeUserProperties.toMap()
        testedProvider.setUserInfo(id, name, email, fakeMutableUserProperties)

        // When
        repeat(forge.anInt(1, 10)) {
            fakeMutableUserProperties[forge.anAlphabeticalString()] = forge.anAlphabeticalString()
        }

        // Then
        assertThat(testedProvider.getUserInfo().additionalProperties).isEqualTo(fakeExpectedUserProperties)
    }

    @Test
    fun `M use immutable values W setUserInfo { removing properties }()`(
        forge: Forge,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @StringForgery(regex = "\\w+@\\w+") email: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)])
        ) fakeUserProperties: Map<String, String>
    ) {
        // Given
        val fakeMutableUserProperties = fakeUserProperties.toMutableMap()
        val fakeExpectedUserProperties = fakeUserProperties.toMap()
        testedProvider.setUserInfo(id, name, email, fakeMutableUserProperties)

        // When
        repeat(forge.anInt(1, fakeMutableUserProperties.size + 1)) {
            fakeMutableUserProperties.remove(fakeMutableUserProperties.keys.random())
        }

        // Then
        assertThat(testedProvider.getUserInfo().additionalProperties).isEqualTo(fakeExpectedUserProperties)
    }
}
