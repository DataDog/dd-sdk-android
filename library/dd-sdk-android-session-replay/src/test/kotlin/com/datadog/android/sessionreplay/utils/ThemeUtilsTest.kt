/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import android.content.res.Resources.Theme
import android.util.TypedValue
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ThemeUtilsTest {

    @Mock
    lateinit var mockTheme: Theme

    @Test
    fun `M resolve theme color W resolveThemeColor{ theme has color }`(forge: Forge) {
        // Given
        val fakeColor = forge.aPositiveInt()
        whenever(
            mockTheme.resolveAttribute(
                eq(android.R.attr.windowBackground),
                any(),
                eq(true)
            )
        )
            .doAnswer {
                val typedValue = it.getArgument<TypedValue>(1)
                typedValue.data = fakeColor
                typedValue.type = forge.anInt(
                    min = TypedValue.TYPE_FIRST_COLOR_INT,
                    max = TypedValue.TYPE_LAST_COLOR_INT + 1
                )
                true
            }

        // Then
        assertThat(ThemeUtils.resolveThemeColor(mockTheme)).isEqualTo(fakeColor)
    }

    @Test
    fun `M return null W resolveThemeColor{ theme has no color }`(forge: Forge) {
        // Given
        val fakeColor = forge.aPositiveInt()
        whenever(
            mockTheme.resolveAttribute(
                eq(android.R.attr.windowBackground),
                any(),
                eq(true)
            )
        )
            .doAnswer {
                val typedValue = it.getArgument<TypedValue>(1)
                typedValue.data = fakeColor
                typedValue.type = forge.anElementFrom(
                    forge.anInt(min = Int.MIN_VALUE, max = TypedValue.TYPE_FIRST_COLOR_INT),
                    forge.anInt(min = TypedValue.TYPE_LAST_COLOR_INT + 1, max = Int.MAX_VALUE)
                )
                true
            }

        // Then
        assertThat(ThemeUtils.resolveThemeColor(mockTheme)).isNull()
    }
}
