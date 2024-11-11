/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.SessionReplayConfiguration.Builder.Companion.DUPLICATE_EXTENSION_DETECTED
import com.datadog.android.sessionreplay.SessionReplayConfiguration.Builder.Companion.DUPLICATE_MAPPER_DETECTED
import com.datadog.android.sessionreplay.SessionReplayConfiguration.Builder.Companion.SAMPLE_IN_ALL_SESSIONS
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.verifyLog
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = ForgeConfigurator::class)
internal class SessionReplayConfigurationBuilderTest {

    private lateinit var testedBuilder: SessionReplayConfiguration.Builder

    @Mock
    lateinit var mockExtensionSupport: ExtensionSupport
    private lateinit var fakeExpectedCustomMappers: List<MapperTypeWrapper<*>>

    @FloatForgery
    var fakeSampleRate: Float = 0f

    @BeforeEach
    fun `set up`() {
        fakeExpectedCustomMappers = listOf(MapperTypeWrapper(View::class.java, mock()))
        whenever(mockExtensionSupport.getCustomViewMappers()).thenReturn(fakeExpectedCustomMappers)
        testedBuilder = SessionReplayConfiguration.Builder()
    }

    @Test
    fun `M use sensible defaults W build()`() {
        // When
        val sessionReplayConfiguration = testedBuilder.build()

        // Then
        assertThat(sessionReplayConfiguration.customEndpointUrl).isNull()
        assertThat(sessionReplayConfiguration.privacy).isEqualTo(SessionReplayPrivacy.MASK)
        assertThat(sessionReplayConfiguration.imagePrivacy).isEqualTo(ImagePrivacy.MASK_ALL)
        assertThat(sessionReplayConfiguration.touchPrivacy).isEqualTo(TouchPrivacy.HIDE)
        assertThat(sessionReplayConfiguration.sampleRate).isEqualTo(SAMPLE_IN_ALL_SESSIONS)
        assertThat(sessionReplayConfiguration.customMappers).isEmpty()
        assertThat(sessionReplayConfiguration.customOptionSelectorDetectors).isEmpty()
        assertThat(sessionReplayConfiguration.dynamicOptimizationEnabled).isEqualTo(true)
    }

    @Test
    fun `M build config with custom site W useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com") sessionReplayUrl: String
    ) {
        // When
        testedBuilder = SessionReplayConfiguration.Builder(fakeSampleRate)
        val sessionReplayConfiguration = testedBuilder
            .useCustomEndpoint(sessionReplayUrl)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.customEndpointUrl)
            .isEqualTo(sessionReplayUrl)
        assertThat(sessionReplayConfiguration.privacy).isEqualTo(SessionReplayPrivacy.MASK)
        assertThat(sessionReplayConfiguration.imagePrivacy).isEqualTo(ImagePrivacy.MASK_ALL)
        assertThat(sessionReplayConfiguration.touchPrivacy).isEqualTo(TouchPrivacy.HIDE)
        assertThat(sessionReplayConfiguration.customMappers).isEmpty()
        assertThat(sessionReplayConfiguration.customOptionSelectorDetectors).isEmpty()
        assertThat(sessionReplayConfiguration.sampleRate).isEqualTo(fakeSampleRate)
    }

    @Test
    fun `M use the given image privacy rule W setImagePrivacy`(
        @Forgery fakeImagePrivacy: ImagePrivacy
    ) {
        // When
        val sessionReplayConfiguration = testedBuilder
            .setImagePrivacy(fakeImagePrivacy)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.imagePrivacy).isEqualTo(fakeImagePrivacy)
    }

    @Test
    fun `M use the given touch privacy rule W setTouchPrivacy`(
        @Forgery fakeTouchPrivacy: TouchPrivacy
    ) {
        // When
        val sessionReplayConfiguration = testedBuilder
            .setTouchPrivacy(fakeTouchPrivacy)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.touchPrivacy).isEqualTo(fakeTouchPrivacy)
    }

    @Test
    fun `M use the given text and input privacy rule W setTextAndInputPrivacy`(
        @Forgery fakeTextAndInputPrivacy: TextAndInputPrivacy
    ) {
        // When
        val sessionReplayConfiguration = testedBuilder
            .setTextAndInputPrivacy(fakeTextAndInputPrivacy)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.textAndInputPrivacy).isEqualTo(fakeTextAndInputPrivacy)
    }

    @Test
    fun `M pass startRecordingImmediately W startRecordingImmediately`() {
        // When
        val sessionReplayConfiguration = testedBuilder
            .startRecordingImmediately(true)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.startRecordingImmediately).isTrue()
    }

    @Test
    fun `M use the provided custom Mappers W addExtensionSupport()`() {
        // Given
        val sessionReplayConfiguration = testedBuilder
            .addExtensionSupport(mockExtensionSupport)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.customMappers)
            .isEqualTo(fakeExpectedCustomMappers)
    }

    @Test
    fun `M use the given dynamic optimization W setDynamicOptimization()`(
        @BoolForgery fakeDynamicOptimizationEnabled: Boolean
    ) {
        // Given
        val sessionReplayConfiguration = testedBuilder
            .setDynamicOptimizationEnabled(fakeDynamicOptimizationEnabled)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.dynamicOptimizationEnabled)
            .isEqualTo(fakeDynamicOptimizationEnabled)
    }

    @Test
    fun `M return empty map W addExtensionSupport { no mappers provided }`() {
        // Given
        whenever(mockExtensionSupport.getCustomViewMappers()).thenReturn(emptyList())
        val sessionReplayConfiguration = testedBuilder
            .addExtensionSupport(mockExtensionSupport)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.customMappers).isEmpty()
    }

    @Suppress("DEPRECATION")
    @Test
    fun `M not overwrite fgm W setPrivacy { fgm already set }`() {
        // When
        val sessionReplayConfiguration = testedBuilder
            .setImagePrivacy(ImagePrivacy.MASK_ALL)
            .setPrivacy(SessionReplayPrivacy.ALLOW)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.imagePrivacy).isEqualTo(ImagePrivacy.MASK_ALL)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `M set appropriate fgm privacy W setPrivacy { allow }`() {
        // When
        val sessionReplayConfiguration = testedBuilder
            .setPrivacy(SessionReplayPrivacy.ALLOW)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.imagePrivacy).isEqualTo(ImagePrivacy.MASK_NONE)
        assertThat(sessionReplayConfiguration.touchPrivacy).isEqualTo(TouchPrivacy.SHOW)
        assertThat(sessionReplayConfiguration.textAndInputPrivacy).isEqualTo(TextAndInputPrivacy.MASK_SENSITIVE_INPUTS)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `M set appropriate fgm privacy W setPrivacy { mask_user_input }`() {
        // When
        val sessionReplayConfiguration = testedBuilder
            .setPrivacy(SessionReplayPrivacy.MASK_USER_INPUT)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.imagePrivacy).isEqualTo(ImagePrivacy.MASK_LARGE_ONLY)
        assertThat(sessionReplayConfiguration.touchPrivacy).isEqualTo(TouchPrivacy.HIDE)
        assertThat(sessionReplayConfiguration.textAndInputPrivacy).isEqualTo(TextAndInputPrivacy.MASK_ALL_INPUTS)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `M set appropriate fgm privacy W setPrivacy { mask }`() {
        // When
        val sessionReplayConfiguration = testedBuilder
            .setPrivacy(SessionReplayPrivacy.MASK)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.imagePrivacy).isEqualTo(ImagePrivacy.MASK_ALL)
        assertThat(sessionReplayConfiguration.touchPrivacy).isEqualTo(TouchPrivacy.HIDE)
        assertThat(sessionReplayConfiguration.textAndInputPrivacy).isEqualTo(TextAndInputPrivacy.MASK_ALL)
    }

    @Test
    fun `M add custom drawable mappers W addExtensionSupport { multiple calls }`(
        @Mock mockFirstExtension: ExtensionSupport,
        @Mock mockSecondExtension: ExtensionSupport,
        @Mock mockFirstDrawableMapper: DrawableToColorMapper,
        @Mock mockSecondDrawableMapper: DrawableToColorMapper
    ) {
        // Given
        whenever(mockFirstExtension.getCustomDrawableMapper())
            .thenReturn(listOf(mockFirstDrawableMapper))

        whenever(mockSecondExtension.getCustomDrawableMapper())
            .thenReturn(listOf(mockSecondDrawableMapper))

        whenever(mockFirstExtension.name()).thenReturn("firstExtension")
        whenever(mockSecondExtension.name()).thenReturn("secondExtension")

        // When
        testedBuilder.addExtensionSupport(mockFirstExtension)
        testedBuilder.addExtensionSupport(mockSecondExtension)
        val config = testedBuilder.build()

        // Then
        assertThat(config.customDrawableMappers).containsAll(
            setOf(mockFirstDrawableMapper, mockSecondDrawableMapper)
        )
    }

    @Test
    fun `M add custom options selector detectors W addExtensionSupport { multiple calls }`(
        @Mock mockFirstExtension: ExtensionSupport,
        @Mock mockSecondExtension: ExtensionSupport,
        @Mock mockFirstOptionSelectorDetector: OptionSelectorDetector,
        @Mock mockSecondOptionSelectorDetector: OptionSelectorDetector
    ) {
        // Given
        whenever(mockFirstExtension.getOptionSelectorDetectors())
            .thenReturn(listOf(mockFirstOptionSelectorDetector))

        whenever(mockSecondExtension.getOptionSelectorDetectors())
            .thenReturn(listOf(mockSecondOptionSelectorDetector))

        whenever(mockFirstExtension.name()).thenReturn("firstExtension")
        whenever(mockSecondExtension.name()).thenReturn("secondExtension")

        // When
        testedBuilder.addExtensionSupport(mockFirstExtension)
        testedBuilder.addExtensionSupport(mockSecondExtension)
        val config = testedBuilder.build()

        // Then
        assertThat(config.customOptionSelectorDetectors).containsAll(
            setOf(mockFirstOptionSelectorDetector, mockSecondOptionSelectorDetector)
        )
    }

    @Test
    fun `M add custom view mappers W addExtensionSupport { multiple calls }`(
        @Mock mockFirstExtension: ExtensionSupport,
        @Mock mockSecondExtension: ExtensionSupport,
        @Mock mockFirstMapper: WireframeMapper<TextView>,
        @Mock mockSecondMapper: WireframeMapper<ImageView>
    ) {
        // Given
        val fakeTextMapperTypeWrapper = MapperTypeWrapper(
            TextView::class.java,
            mockFirstMapper
        )

        val fakeImageMapperTypeWrapper = MapperTypeWrapper(
            ImageView::class.java,
            mockSecondMapper
        )

        whenever(mockFirstExtension.getCustomViewMappers())
            .thenReturn(listOf(fakeTextMapperTypeWrapper))

        whenever(mockSecondExtension.getCustomViewMappers())
            .thenReturn(listOf(fakeImageMapperTypeWrapper))

        whenever(mockFirstExtension.name()).thenReturn("firstExtension")
        whenever(mockSecondExtension.name()).thenReturn("secondExtension")

        // When
        testedBuilder.addExtensionSupport(mockFirstExtension)
        testedBuilder.addExtensionSupport(mockSecondExtension)
        val config = testedBuilder.build()

        // Then
        assertThat(config.customMappers).containsAll(
            setOf(fakeTextMapperTypeWrapper, fakeImageMapperTypeWrapper)
        )
    }

    @Test
    fun `M take first value W addExtensionSupport { duplicate mappers }`(
        @Mock mockFirstExtension: ExtensionSupport,
        @Mock mockSecondExtension: ExtensionSupport,
        @Mock mockMapper: WireframeMapper<ImageView>
    ) {
        // Given
        val fakeMapperTypeWrapper = MapperTypeWrapper(
            ImageView::class.java,
            mockMapper
        )

        whenever(mockFirstExtension.getCustomViewMappers())
            .thenReturn(listOf(fakeMapperTypeWrapper))

        whenever(mockSecondExtension.getCustomViewMappers())
            .thenReturn(listOf(fakeMapperTypeWrapper))

        // When
        testedBuilder.addExtensionSupport(mockFirstExtension)
        testedBuilder.addExtensionSupport(mockSecondExtension)
        val config = testedBuilder.build()

        // Then
        assertThat(config.customMappers).isEqualTo(mockFirstExtension.getCustomViewMappers())
    }

    @Test
    fun `M warn W addExtensionSupport { duplicate mappers }`(
        @Mock mockFirstExtension: ExtensionSupport,
        @Mock mockSecondExtension: ExtensionSupport,
        @Mock mockMapper: WireframeMapper<ImageView>,
        @Mock mockLogger: InternalLogger
    ) {
        // Given
        testedBuilder = SessionReplayConfiguration.Builder(100f, mockLogger)
        val fakeMapperTypeWrapper = MapperTypeWrapper(
            ImageView::class.java,
            mockMapper
        )

        whenever(mockFirstExtension.getCustomViewMappers())
            .thenReturn(listOf(fakeMapperTypeWrapper))
        whenever(mockFirstExtension.name()).thenReturn("firstExtension")
        whenever(mockSecondExtension.name()).thenReturn("secondExtension")

        whenever(mockSecondExtension.getCustomViewMappers())
            .thenReturn(listOf(fakeMapperTypeWrapper))

        // When
        testedBuilder.addExtensionSupport(mockFirstExtension)
        testedBuilder.addExtensionSupport(mockSecondExtension)

        testedBuilder.build()

        val expected = String.format(DUPLICATE_MAPPER_DETECTED, ImageView::class.java)

        // Then
        mockLogger.verifyLog(
            target = InternalLogger.Target.MAINTAINER,
            level = InternalLogger.Level.WARN,
            message = expected
        )
    }

    @Test
    fun `M warn W addExtensionSupport { duplicate extensions }`(
        @Mock mockExtension: ExtensionSupport,
        @Mock mockSecondExtension: ExtensionSupport,
        @StringForgery fakeName: String,
        @Mock mockLogger: InternalLogger
    ) {
        // Given
        testedBuilder = SessionReplayConfiguration.Builder(100f, mockLogger)
        whenever(mockExtension.name()).thenReturn(fakeName)
        whenever(mockSecondExtension.name()).thenReturn(fakeName)
        val expected = String.format(Locale.US, DUPLICATE_EXTENSION_DETECTED, fakeName)

        // When
        testedBuilder.addExtensionSupport(mockExtension)
        testedBuilder.addExtensionSupport(mockExtension)

        // Then
        mockLogger.verifyLog(
            target = InternalLogger.Target.MAINTAINER,
            level = InternalLogger.Level.WARN,
            message = expected
        )
    }
}
