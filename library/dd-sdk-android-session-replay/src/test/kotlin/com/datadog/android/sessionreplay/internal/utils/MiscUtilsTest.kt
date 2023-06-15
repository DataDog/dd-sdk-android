/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.Resources.Theme
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Display
import android.view.WindowManager
import android.view.WindowMetrics
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
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
    ExtendWith(ApiLevelExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class MiscUtilsTest {

    @Mock
    lateinit var mockTheme: Theme

    var fakeThemeColor: Int = 0

    var fakeOrientation: Int = 0

    lateinit var fakeConfiguration: Configuration

    var fakeDensity: Float = 0f

    lateinit var fakeDisplayMetrics: DisplayMetrics

    lateinit var mockResources: Resources

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeOrientation = forge.anElementFrom(
            intArrayOf(
                Configuration.ORIENTATION_UNDEFINED,
                Configuration.ORIENTATION_LANDSCAPE,
                Configuration.ORIENTATION_PORTRAIT
            )
        )
        fakeThemeColor = forge.forgeThemeColor(mockTheme)
        fakeConfiguration = Configuration().apply { orientation = fakeOrientation }
        fakeDensity = forge.aPositiveFloat()
        fakeDisplayMetrics = DisplayMetrics().apply {
            density = fakeDensity
        }
        mockResources = mockResources(fakeConfiguration, fakeDisplayMetrics)
    }

    // region Theme color

    @Test
    fun `M resolve theme color W resolveThemeColor{ theme has color }`() {
        // Then
        assertThat(MiscUtils.resolveThemeColor(mockTheme)).isEqualTo(fakeThemeColor)
    }

    @Test
    fun `M return null W resolveThemeColor{ theme has no color }`(forge: Forge) {
        // Given
        forge.forgeThemeInvalidNoColor(mockTheme)

        // Then
        assertThat(MiscUtils.resolveThemeColor(mockTheme)).isNull()
    }

    // endregion

    // region System Information

    @Suppress("DEPRECATION")
    @Test
    fun `M resolve system information W resolveSystemInformation`(forge: Forge) {
        // Given
        val expectedThemeColorAsHexa = StringUtils.formatColorAndAlphaAsHexa(
            fakeThemeColor,
            MiscUtils.OPAQUE_ALPHA_VALUE
        )
        val fakeScreenWidth = forge.aPositiveInt()
        val fakeScreenHeight = forge.aPositiveInt()
        val expectedScreenWidth = fakeScreenWidth.toLong().densityNormalized(fakeDensity)
        val expectedScreenHeight = fakeScreenHeight.toLong().densityNormalized(fakeDensity)
        val mockDisplay: Display = mock {
            whenever(it.getSize(any())).thenAnswer {
                val point = it.arguments[0] as Point
                point.x = fakeScreenWidth
                point.y = fakeScreenHeight
                null
            }
        }
        val mockWindowManager: WindowManager = mock {
            whenever(it.defaultDisplay).thenReturn(mockDisplay)
        }
        val mockContext: Context = mockContext(mockWindowManager, mockResources, mockTheme)

        // When
        val systemInformation = MiscUtils.resolveSystemInformation(mockContext)

        // Then
        assertThat(systemInformation).isEqualTo(
            SystemInformation(
                screenBounds = GlobalBounds(0, 0, expectedScreenWidth, expectedScreenHeight),
                screenOrientation = fakeOrientation,
                screenDensity = fakeDensity,
                themeColor = expectedThemeColorAsHexa
            )
        )
    }

    @TestTargetApi(Build.VERSION_CODES.R)
    @Test
    fun `M resolve system information W resolveSystemInformation{ R and above }`(forge: Forge) {
        // Given
        val expectedThemeColorAsHexa = StringUtils.formatColorAndAlphaAsHexa(
            fakeThemeColor,
            MiscUtils.OPAQUE_ALPHA_VALUE
        )
        val fakeScreenWidth = forge.aPositiveInt(strict = true)
        val fakeScreenHeight = forge.aPositiveInt(strict = true)
        val fakeScreenBoundsTop = forge.aPositiveInt(strict = true)
        val fakeScreenBoundsBottom = fakeScreenBoundsTop + fakeScreenHeight
        val fakeScreenBoundsLeft = forge.aPositiveInt(strict = true)
        val fakeScreenBoundsRight = fakeScreenBoundsLeft + fakeScreenWidth
        val expectedScreenWidth = fakeScreenWidth.toLong().densityNormalized(fakeDensity)
        val expectedScreenHeight = fakeScreenHeight.toLong().densityNormalized(fakeDensity)
        val fakeScreenBounds = Rect().apply {
            left = fakeScreenBoundsLeft
            right = fakeScreenBoundsRight
            bottom = fakeScreenBoundsBottom
            top = fakeScreenBoundsTop
        }
        val mockWindowMetrics: WindowMetrics = mock {
            whenever(it.bounds).thenReturn(fakeScreenBounds)
        }
        val mockWindowManager: WindowManager = mock {
            whenever(it.currentWindowMetrics).thenReturn(mockWindowMetrics)
        }
        val mockContext: Context = mockContext(mockWindowManager, mockResources, mockTheme)

        // When
        val systemInformation = MiscUtils.resolveSystemInformation(mockContext)

        // Then
        assertThat(systemInformation).isEqualTo(
            SystemInformation(
                screenBounds = GlobalBounds(0, 0, expectedScreenWidth, expectedScreenHeight),
                screenOrientation = fakeOrientation,
                screenDensity = fakeDensity,
                themeColor = expectedThemeColorAsHexa
            )
        )
    }

    @Test
    fun `M return empty screen bounds W resolveSystemInformation{windowManager was null}`() {
        // Given
        val expectedThemeColorAsHexa = StringUtils.formatColorAndAlphaAsHexa(
            fakeThemeColor,
            MiscUtils.OPAQUE_ALPHA_VALUE
        )
        val mockContext: Context = mockContext(null, mockResources, mockTheme)

        // When
        val systemInformation = MiscUtils.resolveSystemInformation(mockContext)

        // Then
        assertThat(systemInformation).isEqualTo(
            SystemInformation(
                screenBounds = GlobalBounds(0, 0, 0, 0),
                screenOrientation = fakeOrientation,
                screenDensity = fakeDensity,
                themeColor = expectedThemeColorAsHexa
            )
        )
    }

    @Test
    fun `M return null theme color W resolveSystemInformation{theme color is invalid}`(
        forge: Forge
    ) {
        // Given
        forge.forgeThemeInvalidNoColor(mockTheme)
        val mockContext: Context = mockContext(null, mockResources, mockTheme)

        // When
        val systemInformation = MiscUtils.resolveSystemInformation(mockContext)

        // Then
        assertThat(systemInformation).isEqualTo(
            SystemInformation(
                screenBounds = GlobalBounds(0, 0, 0, 0),
                screenOrientation = fakeOrientation,
                screenDensity = fakeDensity,
                themeColor = null
            )
        )
    }

    // endregion

    // region Internal

    private fun mockResources(
        configuration: Configuration,
        displayMetrics: DisplayMetrics
    ): Resources =
        mock {
            whenever(it.configuration).thenReturn(configuration)
            whenever(it.displayMetrics).thenReturn(displayMetrics)
        }

    private fun mockContext(
        windowManager: WindowManager?,
        resources: Resources,
        theme: Theme
    ): Context = mock {
        whenever(it.getSystemService(Context.WINDOW_SERVICE)).thenReturn(windowManager)
        whenever(it.resources).thenReturn(resources)
        whenever(it.theme).thenReturn(theme)
    }

    private fun Forge.forgeThemeColor(theme: Theme): Int {
        val fakeColor = aPositiveInt()
        whenever(
            theme.resolveAttribute(
                eq(android.R.attr.windowBackground),
                any(),
                eq(true)
            )
        ).doAnswer {
            val typedValue = it.getArgument<TypedValue>(1)
            typedValue.data = fakeColor
            typedValue.type = anInt(
                min = TypedValue.TYPE_FIRST_COLOR_INT,
                max = TypedValue.TYPE_LAST_COLOR_INT + 1
            )
            true
        }

        return fakeColor
    }
    private fun Forge.forgeThemeInvalidNoColor(theme: Theme): Int {
        val fakeColor = aPositiveInt()
        whenever(
            theme.resolveAttribute(
                eq(android.R.attr.windowBackground),
                any(),
                eq(true)
            )
        ).doAnswer {
            val typedValue = it.getArgument<TypedValue>(1)
            typedValue.data = fakeColor
            typedValue.type = anElementFrom(
                anInt(min = Int.MIN_VALUE, max = TypedValue.TYPE_FIRST_COLOR_INT),
                anInt(min = TypedValue.TYPE_LAST_COLOR_INT + 1, max = Int.MAX_VALUE)
            )
            true
        }

        return fakeColor
    }

    // endregion
}
