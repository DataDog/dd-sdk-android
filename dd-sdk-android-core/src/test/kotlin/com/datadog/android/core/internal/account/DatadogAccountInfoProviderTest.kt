/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.account

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.AccountInfo
import com.datadog.android.core.internal.account.DatadogAccountInfoProvider.Companion.MSG_ACCOUNT_NULL
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogAccountInfoProviderTest {

    private lateinit var testedProvider: DatadogAccountInfoProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedProvider = DatadogAccountInfoProvider(mockInternalLogger)
    }

    @Test
    fun `M return null W getAccountInfo() {account info not set}`() {
        // When
        val result = testedProvider.getAccountInfo()

        // Then
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `M return saved accountInfo W setAccountInfo() and getAccountInfo()`(
        @Forgery accountInfo: AccountInfo
    ) {
        // When
        testedProvider.setAccountInfo(
            accountInfo.id,
            accountInfo.name,
            accountInfo.extraInfo
        )
        val result = testedProvider.getAccountInfo()

        // Then
        assertThat(result).isEqualTo(accountInfo)
    }

    @Test
    fun `M keep existing properties W addExtraInfo() is called`(
        @Forgery accountInfo: AccountInfo,
        @StringForgery forge: Forge
    ) {
        // Given
        val fakeExtraInfo = forge.exhaustiveAttributes()
        testedProvider.setAccountInfo(
            accountInfo.id,
            accountInfo.name,
            fakeExtraInfo
        )

        // When
        testedProvider.addExtraInfo(fakeExtraInfo)
        // Then
        assertThat(testedProvider.getAccountInfo()?.extraInfo).isEqualTo(fakeExtraInfo)
    }

    @Test
    fun `M use immutable properties W addExtraInfo() is called { changing properties values }`(
        @StringForgery forge: Forge,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String
    ) {
        // Given
        val fakeProperties = forge.exhaustiveAttributes()
        val fakeExpectedProperties = fakeProperties.toMap()
        val fakeMutableProperties = fakeProperties.toMutableMap()
        testedProvider.setAccountInfo(id, name, emptyMap())
        testedProvider.addExtraInfo(fakeMutableProperties)

        // When
        fakeMutableProperties.keys.forEach {
            fakeMutableProperties[it] = forge.anAlphabeticalString()
        }

        // Then
        assertThat(
            testedProvider.getAccountInfo()?.extraInfo
        ).isEqualTo(fakeExpectedProperties)
    }

    @Test
    fun `M use immutable properties W addExtraInfo() is called { adding properties }`(
        @StringForgery forge: Forge,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String
    ) {
        // Given
        val fakeProperties = forge.exhaustiveAttributes()
        val fakeExpectedProperties = fakeProperties.toMap()
        val fakeMutableProperties = fakeProperties.toMutableMap()
        testedProvider.setAccountInfo(id, name, emptyMap())
        testedProvider.addExtraInfo(fakeMutableProperties)

        // When
        repeat(forge.anInt(1, 10)) {
            fakeMutableProperties[forge.anAlphabeticalString()] = forge.anAlphabeticalString()
        }

        // Then
        assertThat(
            testedProvider.getAccountInfo()?.extraInfo
        ).isEqualTo(fakeExpectedProperties)
    }

    @Test
    fun `M use immutable properties W addExtraInfo() is called { removing properties }`(
        @StringForgery forge: Forge,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String
    ) {
        // Given
        val fakeProperties = forge.exhaustiveAttributes()
        val fakeExpectedProperties = fakeProperties.toMap()
        val fakeMutableProperties = fakeProperties.toMutableMap()
        testedProvider.setAccountInfo(id, name, emptyMap())
        testedProvider.addExtraInfo(fakeMutableProperties)

        // When
        repeat(forge.anInt(1, fakeMutableProperties.size + 1)) {
            fakeMutableProperties.remove(fakeMutableProperties.keys.random())
        }

        // Then
        assertThat(testedProvider.getAccountInfo()?.extraInfo).isEqualTo(fakeExpectedProperties)
    }

    @Test
    fun `M keep new property key W addExtraInfo() is called and the key already exists`(
        @Forgery accountInfo: AccountInfo,
        @StringForgery key: String,
        @StringForgery value1: String,
        @StringForgery value2: String
    ) {
        // Given
        testedProvider.setAccountInfo(
            accountInfo.id,
            accountInfo.name,
            mutableMapOf(key to value1)
        )

        // When
        testedProvider.addExtraInfo(mapOf(key to value2))

        // Then
        assertThat(
            testedProvider.getAccountInfo()?.extraInfo
        ).isEqualTo(
            mapOf(key to value2)
        )
    }

    @Test
    fun `M use immutable values W setAccountInfo { changing properties values }()`(
        forge: Forge,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)])
        ) fakeExtraInfo: Map<String, String>
    ) {
        // Given
        val fakeMutableExtraInfo = fakeExtraInfo.toMutableMap()
        val fakeExpectedExtraInfo = fakeExtraInfo.toMap()
        testedProvider.setAccountInfo(id, name, fakeMutableExtraInfo)

        // When
        fakeMutableExtraInfo.keys.forEach { key ->
            fakeMutableExtraInfo[key] = forge.anAlphaNumericalString()
        }

        // Then
        assertThat(testedProvider.getAccountInfo()?.extraInfo).isEqualTo(
            fakeExpectedExtraInfo
        )
    }

    @Test
    fun `M use immutable values W setAccountInfo { adding properties }()`(
        forge: Forge,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)])
        ) fakeExtraInfo: Map<String, String>
    ) {
        // Given
        val fakeMutableExtraInfo = fakeExtraInfo.toMutableMap()
        val fakeExpectedExtraInfo = fakeExtraInfo.toMap()
        testedProvider.setAccountInfo(id, name, fakeMutableExtraInfo)

        // When
        repeat(forge.anInt(1, 10)) {
            fakeMutableExtraInfo[forge.anAlphabeticalString()] = forge.anAlphabeticalString()
        }

        // Then
        assertThat(testedProvider.getAccountInfo()?.extraInfo).isEqualTo(
            fakeExpectedExtraInfo
        )
    }

    @Test
    fun `M use immutable values W setAccountInfo { removing properties }()`(
        forge: Forge,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)])
        ) fakeExtraInfo: Map<String, String>
    ) {
        // Given
        val fakeMutableExtraInfo = fakeExtraInfo.toMutableMap()
        val fakeExpectedExtraInfo = fakeExtraInfo.toMap()
        testedProvider.setAccountInfo(id, name, fakeMutableExtraInfo)

        // When
        repeat(forge.anInt(1, fakeMutableExtraInfo.size + 1)) {
            fakeMutableExtraInfo.remove(fakeMutableExtraInfo.keys.random())
        }

        // Then
        assertThat(testedProvider.getAccountInfo()?.extraInfo).isEqualTo(
            fakeExpectedExtraInfo
        )
    }

    @Test
    fun `M warn user with log W addExtraInfo { account info null }()`(
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)])
        ) fakeExtraInfo: Map<String, String>
    ) {
        // Given
        val fakeMutableExtraInfo = fakeExtraInfo.toMutableMap()

        // When
        testedProvider.addExtraInfo(fakeMutableExtraInfo)

        // Then
        assertThat(testedProvider.getAccountInfo()?.extraInfo).isEqualTo(
            null
        )
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                level = eq(InternalLogger.Level.WARN),
                target = eq(InternalLogger.Target.USER),
                messageBuilder = capture(),
                throwable = isNull(),
                onlyOnce = any(),
                additionalProperties = isNull(),
                force = eq(false)
            )
            allValues.forEach {
                assertThat(it()).isEqualTo(MSG_ACCOUNT_NULL)
            }
        }
    }

    @Test
    fun `M return null W call clearAccountInfo`(
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)])
        ) fakeExtraInfo: Map<String, String>
    ) {
        // Given
        val fakeMutableExtraInfo = fakeExtraInfo.toMutableMap()
        testedProvider.setAccountInfo(id, name, fakeMutableExtraInfo)

        // When
        testedProvider.clearAccountInfo()

        // Then
        assertThat(testedProvider.getAccountInfo()).isNull()
    }
}
