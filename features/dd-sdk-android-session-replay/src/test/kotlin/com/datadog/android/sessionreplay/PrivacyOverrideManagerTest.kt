/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.view.View
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.PrivacyOverride
import com.datadog.android.sessionreplay.internal.PrivacyOverrideManager
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PrivacyOverrideManagerTest {
    private lateinit var testedPrivacyOverrides: PrivacyOverrideManager

    @BeforeEach
    fun setup() {
        testedPrivacyOverrides = PrivacyOverrideManager
    }

    @Test
    fun `M mark view as overridden W addPrivacyOverride()`(
        @Forgery fakePrivacy: PrivacyLevel
    ) {
        // Given
        val fakeView = mock<View>()
        val expected = generateExpectedPrivacyOverride(fakePrivacy)

        // When
        testedPrivacyOverrides.addPrivacyOverride(fakeView, fakePrivacy)

        // Then
        assertThat(testedPrivacyOverrides.getPrivacyOverrides(fakeView))
            .isEqualTo(expected)
    }

    @Test
    fun `M remove image privacy override W setViewNotHidden()`(
        forge: Forge
    ) {
        // Given
        val fakeView = mock<View>()
        val fakeImagePrivacy = forge.aValueFrom(ImagePrivacy::class.java)
        testedPrivacyOverrides.addPrivacyOverride(fakeView, fakeImagePrivacy)

        // When
        testedPrivacyOverrides.removeImagePrivacyOverride(fakeView)

        // Then
        assertThat(testedPrivacyOverrides.getPrivacyOverrides(fakeView))
            .isEqualTo(PrivacyOverride())
    }

    @Test
    fun `M remove touch privacy override W removeTouchPrivacyOverride()`(
        forge: Forge
    ) {
        // Given
        val fakeView = mock<View>()
        val fakeImagePrivacy = forge.aValueFrom(TouchPrivacy::class.java)
        testedPrivacyOverrides.addPrivacyOverride(fakeView, fakeImagePrivacy)

        // When
        testedPrivacyOverrides.removeTouchPrivacyOverride(fakeView)

        // Then
        assertThat(testedPrivacyOverrides.getPrivacyOverrides(fakeView))
            .isEqualTo(PrivacyOverride())
    }

    @Test
    fun `M remove text and input privacy override W removeTextAndInputPrivacyOverride()`(
        forge: Forge
    ) {
        // Given
        val fakeView = mock<View>()
        val fakeImagePrivacy = forge.aValueFrom(TextAndInputPrivacy::class.java)
        testedPrivacyOverrides.addPrivacyOverride(fakeView, fakeImagePrivacy)

        // When
        testedPrivacyOverrides.removeTextAndInputPrivacyOverride(fakeView)

        // Then
        assertThat(testedPrivacyOverrides.getPrivacyOverrides(fakeView))
            .isEqualTo(PrivacyOverride())
    }

    // region hiddenPrivacy

    @Test
    fun `M mark view as hidden W addHiddenOverride()`() {
        // Given
        val fakeView = mock<View>()
        val expected = PrivacyOverride().copy(
            hiddenPrivacy = true
        )

        // When
        testedPrivacyOverrides.addHiddenOverride(fakeView)

        // Then
        assertThat(testedPrivacyOverrides.getPrivacyOverrides(fakeView))
            .isEqualTo(expected)
    }

    @Test
    fun `M remove hidden privacy override W removeHiddenOverride()`() {
        // Given
        val fakeView = mock<View>()
        testedPrivacyOverrides.addHiddenOverride(fakeView)

        // When
        testedPrivacyOverrides.removeHiddenOverride(fakeView)

        // Then
        assertThat(testedPrivacyOverrides.getPrivacyOverrides(fakeView))
            .isEqualTo(PrivacyOverride())
    }

    // endregion

    private fun generateExpectedPrivacyOverride(fakePrivacy: PrivacyLevel): PrivacyOverride? {
        return when (fakePrivacy) {
            is ImagePrivacy -> PrivacyOverride().copy(
                imagePrivacy = fakePrivacy
            )

            is TextAndInputPrivacy -> PrivacyOverride().copy(
                textAndInputPrivacy = fakePrivacy
            )

            is TouchPrivacy -> PrivacyOverride().copy(
                touchPrivacy = fakePrivacy
            )

            else -> return null
        }
    }
}
