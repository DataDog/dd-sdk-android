/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.user

import com.datadog.android.api.context.UserInfo
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
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogUserInfoProviderTest {

    private lateinit var testedProvider: DatadogUserInfoProvider

    @BeforeEach
    fun `set up`() {
        testedProvider = DatadogUserInfoProvider()
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
        @Forgery userInfo: UserInfo,
        @StringForgery userId: String
    ) {
        // Given
        val nonNullUserId = userInfo.id ?: userId
        val validUserInfo = userInfo.copy(id = nonNullUserId)

        // When
        testedProvider.setUserInfo(nonNullUserId, userInfo.name, userInfo.email, userInfo.additionalProperties)
        val result = testedProvider.getUserInfo()

        // Then
        assertThat(result).isEqualTo(validUserInfo)
    }

    @Test
    fun `M keep existing properties W addUserProperties() is called`(
        @Forgery userInfo: UserInfo,
        @StringForgery userId: String,
        forge: Forge
    ) {
        // Given
        val customProperties = forge.exhaustiveAttributes()
        val nonNullUserId = userInfo.id ?: userId
        testedProvider.setUserInfo(nonNullUserId, userInfo.name, userInfo.email, customProperties)

        // When
        testedProvider.addUserProperties(customProperties)

        // Then
        assertThat(
            testedProvider.getUserInfo().additionalProperties
        ).isEqualTo(customProperties)
    }

    @Test
    fun `M use immutable properties W addUserProperties() is called { changing properties values }`(
        forge: Forge
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
        forge: Forge
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
        forge: Forge
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
        @StringForgery userId: String,
        @StringForgery key: String,
        @StringForgery value1: String,
        @StringForgery value2: String
    ) {
        // Given
        val nonNullUserId = userInfo.id ?: userId
        testedProvider.setUserInfo(nonNullUserId, userInfo.name, userInfo.email, mutableMapOf(key to value1))

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
    fun `M enriches empty user info with anonymousId W setAnonymousId`(
        @StringForgery(type = StringForgeryType.HEXADECIMAL) anonymousId: String
    ) {
        // When
        testedProvider.setAnonymousId(anonymousId)

        // Then
        testedProvider.getUserInfo().let {
            assertThat(it.anonymousId).isEqualTo(anonymousId)
            assertThat(it.id).isNull()
            assertThat(it.name).isNull()
            assertThat(it.email).isNull()
            assertThat(it.additionalProperties).isEmpty()
        }
    }

    @Test
    fun `M enriches existing user info with anonymousId W setAnonymousId`(
        @StringForgery(type = StringForgeryType.HEXADECIMAL) anonymousId: String,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @StringForgery(regex = "\\w+@\\w+") email: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)])
        ) fakeUserProperties: Map<String, String>
    ) {
        // Given
        testedProvider.setUserInfo(id, name, email, fakeUserProperties)

        // When
        testedProvider.setAnonymousId(anonymousId)

        // Then
        testedProvider.getUserInfo().let {
            assertThat(it.anonymousId).isEqualTo(anonymousId)
            assertThat(it.id).isEqualTo(id)
            assertThat(it.name).isEqualTo(name)
            assertThat(it.email).isEqualTo(email)
            assertThat(it.additionalProperties).isEqualTo(fakeUserProperties)
        }
    }

    @Test
    fun `M clears the anonymousId W setAnonymousId { null }`(
        @StringForgery(type = StringForgeryType.HEXADECIMAL) anonymousId: String
    ) {
        // Given
        testedProvider.setAnonymousId(anonymousId)

        // When
        testedProvider.setAnonymousId(null)

        // Then
        assertThat(testedProvider.getUserInfo().anonymousId).isNull()
    }

    @Test
    fun `M clears the anonymousId and keeps existing user info W setAnonymousId { null }`(
        @StringForgery(type = StringForgeryType.HEXADECIMAL) anonymousId: String,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @StringForgery(regex = "\\w+@\\w+") email: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)])
        ) fakeUserProperties: Map<String, String>
    ) {
        // Given
        testedProvider.setUserInfo(id, name, email, fakeUserProperties)
        testedProvider.setAnonymousId(anonymousId)

        // When
        testedProvider.setAnonymousId(null)

        // Then
        testedProvider.getUserInfo().let {
            assertThat(it.anonymousId).isNull()
            assertThat(it.id).isEqualTo(id)
            assertThat(it.name).isEqualTo(name)
            assertThat(it.email).isEqualTo(email)
            assertThat(it.additionalProperties).isEqualTo(fakeUserProperties)
        }
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

    @Test
    fun `M return default userInfo W clearUserInfo()`(
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @StringForgery(regex = "\\w+@\\w+") email: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)])
        ) fakeUserProperties: Map<String, String>
    ) {
        // Given
        testedProvider.setUserInfo(id, name, email, fakeUserProperties)

        // When
        testedProvider.clearUserInfo()

        // Then
        assertThat(testedProvider.getUserInfo()).isEqualTo(UserInfo())
    }
}
