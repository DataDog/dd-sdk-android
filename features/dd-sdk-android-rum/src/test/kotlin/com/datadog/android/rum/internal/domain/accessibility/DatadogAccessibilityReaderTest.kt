/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.accessibility

import android.app.ActivityManager
import android.content.ContentResolver
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.provider.Settings
import android.provider.Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED
import android.view.accessibility.AccessibilityManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.domain.accessibility.Accessibility.Companion.CLOSED_CAPTIONING_ENABLED_KEY
import com.datadog.android.rum.internal.domain.accessibility.Accessibility.Companion.COLOR_INVERSION_ENABLED_KEY
import com.datadog.android.rum.internal.domain.accessibility.Accessibility.Companion.REDUCED_ANIMATIONS_ENABLED_KEY
import com.datadog.android.rum.internal.domain.accessibility.Accessibility.Companion.SCREEN_PINNING_ENABLED_KEY
import com.datadog.android.rum.internal.domain.accessibility.Accessibility.Companion.SCREEN_READER_ENABLED_KEY
import com.datadog.android.rum.internal.domain.accessibility.Accessibility.Companion.TEXT_SIZE_KEY
import com.datadog.android.rum.internal.domain.accessibility.DatadogAccessibilityReader.Companion.CAPTIONING_ENABLED_KEY
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogAccessibilityReaderTest {
    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockAccessibilityManager: AccessibilityManager

    @Mock
    lateinit var mockActivityManager: ActivityManager

    @Mock
    lateinit var mockResources: Resources

    @Mock
    lateinit var mockConfiguration: Configuration

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSecureWrapper: SecureWrapper

    @Mock
    lateinit var mockGlobalWrapper: GlobalWrapper

    @Mock
    lateinit var mockContentResolver: ContentResolver

    private lateinit var testedReader: DatadogAccessibilityReader

    @BeforeEach
    fun setup() {
        whenever(mockContext.contentResolver) doReturn mockContentResolver
        whenever(mockResources.configuration) doReturn mockConfiguration

        testedReader = DatadogAccessibilityReader(
            internalLogger = mockInternalLogger,
            applicationContext = mockContext,
            resources = mockResources,
            activityManager = mockActivityManager,
            accessibilityManager = mockAccessibilityManager,
            secureWrapper = mockSecureWrapper,
            globalWrapper = mockGlobalWrapper
        )
    }

    @Test
    fun `M return empty state W getState { accessibility manager is null }`() {
        // Given
        testedReader = DatadogAccessibilityReader(
            internalLogger = mockInternalLogger,
            applicationContext = mockContext,
            resources = mockResources,
            activityManager = mockActivityManager,
            accessibilityManager = null
        )

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result).isEqualTo(Accessibility.EMPTY_STATE.toMap())
    }

    @Test
    fun `M return complete accessibility state W getState { all values available }`(
        @FloatForgery(min = 0.5f, max = 3.0f) textSize: Float,
        @BoolForgery isScreenReaderEnabled: Boolean,
        @BoolForgery isColorInversionEnabled: Boolean,
        @BoolForgery isClosedCaptioningEnabled: Boolean,
        @BoolForgery isReducedAnimationsEnabled: Boolean,
        @BoolForgery isScreenPinningEnabled: Boolean
    ) {
        // Given
        mockConfiguration.fontScale = textSize
        whenever(mockAccessibilityManager.isTouchExplorationEnabled) doReturn isScreenReaderEnabled

        whenever(mockActivityManager.lockTaskModeState) doReturn if (isScreenPinningEnabled) {
            ActivityManager.LOCK_TASK_MODE_LOCKED
        } else {
            ActivityManager.LOCK_TASK_MODE_NONE
        }
        @Suppress("DEPRECATION")
        whenever(mockActivityManager.isInLockTaskMode) doReturn isScreenPinningEnabled

        whenever(
            mockSecureWrapper.getInt(
                internalLogger = mockInternalLogger,
                applicationContext = mockContext,
                key = ACCESSIBILITY_DISPLAY_INVERSION_ENABLED
            )
        ) doReturn isColorInversionEnabled

        whenever(
            mockSecureWrapper.getInt(
                internalLogger = mockInternalLogger,
                applicationContext = mockContext,
                key = CAPTIONING_ENABLED_KEY
            )
        ) doReturn isClosedCaptioningEnabled

        val animationDurationValue = if (isReducedAnimationsEnabled) 0.0f else 1.0f
        whenever(
            mockGlobalWrapper.getFloat(mockInternalLogger, mockContext, Settings.Global.ANIMATOR_DURATION_SCALE)
        ) doReturn animationDurationValue

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result[TEXT_SIZE_KEY]).isEqualTo(textSize)
        assertThat(result[SCREEN_READER_ENABLED_KEY]).isEqualTo(isScreenReaderEnabled)
        assertThat(result[SCREEN_PINNING_ENABLED_KEY]).isEqualTo(isScreenPinningEnabled)
        assertThat(result[COLOR_INVERSION_ENABLED_KEY]).isEqualTo(isColorInversionEnabled)
        assertThat(result[CLOSED_CAPTIONING_ENABLED_KEY]).isEqualTo(isClosedCaptioningEnabled)
        assertThat(result[REDUCED_ANIMATIONS_ENABLED_KEY]).isEqualTo(isReducedAnimationsEnabled)
    }

    // region Text Size Tests
    @Test
    fun `M return null text size W getState { resources is null }`() {
        // Given
        testedReader = DatadogAccessibilityReader(
            internalLogger = mockInternalLogger,
            applicationContext = mockContext,
            resources = null,
            activityManager = mockActivityManager,
            accessibilityManager = mockAccessibilityManager
        )

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result[TEXT_SIZE_KEY]).isNull()
    }

    @Test
    fun `M return null text size W getState { resources configuration is null }`() {
        // Given
        whenever(mockResources.configuration) doReturn null

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result[TEXT_SIZE_KEY]).isNull()
    }

    @Test
    fun `M return text size W getState { valid font scale }`(
        @FloatForgery(min = 0.5f, max = 3.0f) fontScale: Float
    ) {
        // Given
        mockConfiguration.fontScale = fontScale

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result[TEXT_SIZE_KEY]).isEqualTo(fontScale)
    }
    // endregion

    // region Screen Reader Tests
    @Test
    fun `M return screen reader state W getState { touch exploration enabled }`(
        @BoolForgery isEnabled: Boolean
    ) {
        // Given
        whenever(mockAccessibilityManager.isTouchExplorationEnabled) doReturn isEnabled

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result[SCREEN_READER_ENABLED_KEY]).isEqualTo(isEnabled)
    }
    // endregion

    // region Screen Pinning Tests
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Test
    fun `M return screen pinning state W getState { api below 23 }`(
        @BoolForgery lockState: Boolean
    ) {
        // Given
        @Suppress("DEPRECATION")
        whenever(mockActivityManager.isInLockTaskMode) doReturn lockState

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result[SCREEN_PINNING_ENABLED_KEY]).isEqualTo(lockState)
    }

    @TestTargetApi(Build.VERSION_CODES.M)
    @Test
    fun `M return true for screen pinning W getState { lock task mode is LOCKED }`() {
        // Given
        whenever(mockActivityManager.lockTaskModeState) doReturn ActivityManager.LOCK_TASK_MODE_LOCKED

        // When
        val result = testedReader.getState()
        val isScreenPinningEnabled = result[SCREEN_PINNING_ENABLED_KEY] as Boolean

        // Then
        assertThat(isScreenPinningEnabled).isTrue()
    }

    @TestTargetApi(Build.VERSION_CODES.M)
    @Test
    fun `M return true for screen pinning W getState { lock task mode is PINNED }`() {
        // Given
        whenever(mockActivityManager.lockTaskModeState) doReturn ActivityManager.LOCK_TASK_MODE_PINNED

        // When
        val result = testedReader.getState()
        val isScreenPinningEnabled = result[SCREEN_PINNING_ENABLED_KEY] as Boolean

        // Then
        assertThat(isScreenPinningEnabled).isTrue()
    }

    @TestTargetApi(Build.VERSION_CODES.M)
    @Test
    fun `M return false for screen pinning W getState { lock task mode is NONE }`() {
        // Given
        whenever(mockActivityManager.lockTaskModeState) doReturn ActivityManager.LOCK_TASK_MODE_NONE

        // When
        val result = testedReader.getState()
        val isScreenPinningEnabled = result[SCREEN_PINNING_ENABLED_KEY] as Boolean

        // Then
        assertThat(isScreenPinningEnabled).isFalse()
    }

    @Test
    fun `M return null for screen pinning W getState { activity manager is null }`() {
        // Given
        testedReader = DatadogAccessibilityReader(
            internalLogger = mockInternalLogger,
            applicationContext = mockContext,
            resources = mockResources,
            activityManager = null,
            accessibilityManager = mockAccessibilityManager
        )

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result[SCREEN_PINNING_ENABLED_KEY]).isNull()
    }
    // endregion

    // region Color Inversion Tests
    @Test
    fun `M return color inversion state W getState { setting available }`(
        @BoolForgery isEnabled: Boolean
    ) {
        // Given
        whenever(
            mockSecureWrapper.getInt(
                internalLogger = mockInternalLogger,
                applicationContext = mockContext,
                key = ACCESSIBILITY_DISPLAY_INVERSION_ENABLED
            )
        ) doReturn isEnabled

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result[COLOR_INVERSION_ENABLED_KEY]).isEqualTo(isEnabled)
    }

    @Test
    fun `M return null color inversion W getState { SettingNotFoundException thrown }`() {
        // Given
        whenever(
            mockSecureWrapper.getInt(
                internalLogger = mockInternalLogger,
                applicationContext = mockContext,
                key = ACCESSIBILITY_DISPLAY_INVERSION_ENABLED
            )
        ) doReturn null

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result[COLOR_INVERSION_ENABLED_KEY]).isNull()
    }
    // endregion

    // region Closed Captioning Tests
    @Test
    fun `M return closed captioning state W getState { setting available }`(
        @BoolForgery isEnabled: Boolean
    ) {
        // Given
        whenever(
            mockSecureWrapper.getInt(
                internalLogger = mockInternalLogger,
                applicationContext = mockContext,
                key = CAPTIONING_ENABLED_KEY
            )
        ) doReturn isEnabled

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result[CLOSED_CAPTIONING_ENABLED_KEY]).isEqualTo(isEnabled)
    }

    @Test
    fun `M return null closed captioning W getState { failed to get secure int }`() {
        // Given
        whenever(
            mockSecureWrapper.getInt(
                internalLogger = mockInternalLogger,
                applicationContext = mockContext,
                key = CAPTIONING_ENABLED_KEY
            )
        ) doReturn null

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result[CLOSED_CAPTIONING_ENABLED_KEY]).isNull()
    }
    // endregion

    // region Reduced Animations Tests
    @Test
    fun `M return true for reduced animations W getState { enabled }`() {
        // Given
        whenever(
            mockGlobalWrapper.getFloat(mockInternalLogger, mockContext, Settings.Global.ANIMATOR_DURATION_SCALE)
        ) doReturn 0.0f

        // When
        val result = testedReader.getState()
        val isReducedAnimations = result[REDUCED_ANIMATIONS_ENABLED_KEY] as Boolean

        // Then
        assertThat(isReducedAnimations).isTrue()
    }

    @Test
    fun `M return false for reduced animations W getState { not enabled }`() {
        // Given
        whenever(
            mockGlobalWrapper.getFloat(mockInternalLogger, mockContext, Settings.Global.ANIMATOR_DURATION_SCALE)
        ) doReturn 1.0f

        // When
        val result = testedReader.getState()
        val isReducedAnimations = result[REDUCED_ANIMATIONS_ENABLED_KEY] as Boolean

        // Then
        assertThat(isReducedAnimations).isFalse()
    }

    @Test
    fun `M return null for reduced animations W getState { null value }`() {
        // Given
        whenever(
            mockGlobalWrapper.getFloat(mockInternalLogger, mockContext, Settings.Global.ANIMATOR_DURATION_SCALE)
        ) doReturn null

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result[REDUCED_ANIMATIONS_ENABLED_KEY]).isEqualTo(null)
    }
    // endregion

    // region Edge Cases
    @Test
    fun `M return partial state W getState { some managers are null }`() {
        // Given
        testedReader = DatadogAccessibilityReader(
            internalLogger = mockInternalLogger,
            applicationContext = mockContext,
            resources = null,
            activityManager = null,
            accessibilityManager = mockAccessibilityManager
        )
        whenever(mockAccessibilityManager.isTouchExplorationEnabled) doReturn true

        // When
        val result = testedReader.getState()
        val isScreenReaderEnabled = result[SCREEN_READER_ENABLED_KEY] as Boolean

        // Then
        assertThat(result[TEXT_SIZE_KEY]).isNull()
        assertThat(result[SCREEN_PINNING_ENABLED_KEY]).isNull()
        assertThat(isScreenReaderEnabled).isTrue()
        // Settings-related values should still be attempted
        assertThat(result.containsKey(COLOR_INVERSION_ENABLED_KEY)).isTrue()
        assertThat(result.containsKey(CLOSED_CAPTIONING_ENABLED_KEY)).isTrue()
        assertThat(result.containsKey(REDUCED_ANIMATIONS_ENABLED_KEY)).isTrue()
    }

    @Test
    fun `M return expected map structure W getState { normal operation }`() {
        // Given
        mockConfiguration.fontScale = 1.2f
        whenever(mockAccessibilityManager.isTouchExplorationEnabled) doReturn true
        whenever(mockActivityManager.lockTaskModeState) doReturn ActivityManager.LOCK_TASK_MODE_NONE

        // When
        val result = testedReader.getState()
        val isScreenReaderEnabled = result[SCREEN_READER_ENABLED_KEY] as Boolean
        val isScreenPinningEnabled = result[SCREEN_PINNING_ENABLED_KEY] as Boolean

        // Then
        assertThat(result).containsKeys(
            TEXT_SIZE_KEY,
            SCREEN_READER_ENABLED_KEY,
            SCREEN_PINNING_ENABLED_KEY,
            COLOR_INVERSION_ENABLED_KEY,
            CLOSED_CAPTIONING_ENABLED_KEY,
            REDUCED_ANIMATIONS_ENABLED_KEY
        )
        assertThat(result[TEXT_SIZE_KEY]).isEqualTo(1.2f)
        assertThat(isScreenReaderEnabled).isTrue()
        assertThat(isScreenPinningEnabled).isFalse()
    }

    @Test
    fun `M exclude null values from map W getState { some values are null }`() {
        // Given
        whenever(mockAccessibilityManager.isTouchExplorationEnabled) doReturn false
        testedReader = DatadogAccessibilityReader(
            internalLogger = mockInternalLogger,
            applicationContext = mockContext,
            resources = null,
            activityManager = null,
            accessibilityManager = mockAccessibilityManager,
            secureWrapper = mockSecureWrapper,
            globalWrapper = mockGlobalWrapper
        )

        // Settings methods will return null due to exceptions
        whenever(
            mockSecureWrapper.getInt(
                internalLogger = mockInternalLogger,
                applicationContext = mockContext,
                key = ACCESSIBILITY_DISPLAY_INVERSION_ENABLED
            )
        ) doReturn null

        // When
        val result = testedReader.getState()
        val isScreenReaderEnabled = result[SCREEN_READER_ENABLED_KEY] as Boolean

        // Then
        assertThat(result).doesNotContainKeys(TEXT_SIZE_KEY, SCREEN_PINNING_ENABLED_KEY)
        assertThat(result).containsKey(SCREEN_READER_ENABLED_KEY)
        assertThat(isScreenReaderEnabled).isFalse()
    }
    // endregion

    // region multithreading

    @Test
    fun `M have only a single cache miss W getState { concurrent access to cached value }`() {
        // Given
        val numThreads = 10
        val executor = Executors.newFixedThreadPool(numThreads)
        val results = ConcurrentLinkedQueue<Map<String, Any>>()

        // When
        val tasks = (1..numThreads).map {
            executor.submit {
                results.add(testedReader.getState())
            }
        }

        // Wait for all threads to complete
        tasks.forEach { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        // Then
        assertThat(testedReader.numCacheMisses()).isEqualTo(1)
    }

    // endregion
}
