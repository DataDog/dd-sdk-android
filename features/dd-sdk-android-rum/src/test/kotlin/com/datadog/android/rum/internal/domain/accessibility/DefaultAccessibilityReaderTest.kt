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
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.provider.Settings
import android.provider.Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener
import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.domain.accessibility.DefaultAccessibilityReader.Companion.CAPTIONING_ENABLED_KEY
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.atomic.AtomicLong

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DefaultAccessibilityReaderTest {
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

    @Mock
    lateinit var mockHandler: Handler

    private lateinit var testedReader: DefaultAccessibilityReader

    @BeforeEach
    fun setup() {
        whenever(mockContext.contentResolver) doReturn mockContentResolver
        whenever(mockResources.configuration) doReturn mockConfiguration

        setupDefaultMockBehavior()

        // Create reader with default setup - individual tests can recreate if they need specific mocks
        testedReader = createReader()
    }

    private fun createReader(): DefaultAccessibilityReader {
        return DefaultAccessibilityReader(
            internalLogger = mockInternalLogger,
            applicationContext = mockContext,
            resources = mockResources,
            activityManager = mockActivityManager,
            accessibilityManager = mockAccessibilityManager,
            secureWrapper = mockSecureWrapper,
            globalWrapper = mockGlobalWrapper,
            handler = mockHandler
        )
    }

    private fun setupDefaultMockBehavior() {
        // Default configuration
        mockConfiguration.fontScale = 1.0f

        // Default accessibility manager behavior
        whenever(mockAccessibilityManager.isTouchExplorationEnabled) doReturn false
        whenever(mockAccessibilityManager.addTouchExplorationStateChangeListener(any())) doReturn true
        whenever(mockAccessibilityManager.removeTouchExplorationStateChangeListener(any())) doReturn true

        // Default activity manager behavior
        whenever(mockActivityManager.lockTaskModeState) doReturn ActivityManager.LOCK_TASK_MODE_NONE
        @Suppress("DEPRECATION")
        whenever(mockActivityManager.isInLockTaskMode) doReturn false

        // Mock ContentResolver behavior (called during lazy initialization)
        doNothing().whenever(mockContentResolver).registerContentObserver(any(), any(), any())
        doNothing().whenever(mockContentResolver).unregisterContentObserver(any())

        // Mock Context behavior for component callbacks (called during lazy initialization)
        doNothing().whenever(mockContext).registerComponentCallbacks(any())
        doNothing().whenever(mockContext).unregisterComponentCallbacks(any())

        // Default secure wrapper behavior
        whenever(
            mockSecureWrapper.getInt(
                internalLogger = any(),
                applicationContext = any(),
                key = any()
            )
        ) doReturn null

        // Default global wrapper behavior
        whenever(
            mockGlobalWrapper.getFloat(
                internalLogger = any(),
                applicationContext = any(),
                key = any()
            )
        ) doReturn null
    }

    @Test
    fun `M return state with initial accessibility values W getState { accessibility manager is null }`() {
        // Given
        testedReader = DefaultAccessibilityReader(
            internalLogger = mockInternalLogger,
            applicationContext = mockContext,
            resources = mockResources,
            activityManager = mockActivityManager,
            accessibilityManager = null,
            secureWrapper = mockSecureWrapper,
            globalWrapper = mockGlobalWrapper,
            handler = mockHandler
        )

        // When
        val result = testedReader.getState()

        assertThat(result.isScreenReaderEnabled).isNull()
        assertThat(result.isClosedCaptioningEnabled).isNull()
        assertThat(result.isReducedAnimationsEnabled).isNull()
        assertThat(result.isScreenPinningEnabled).isFalse()
        assertThat(result.isColorInversionEnabled).isNull()
        assertThat(result.textSize).isEqualTo("1.0")
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
                internalLogger = any(),
                applicationContext = any(),
                key = eq(ACCESSIBILITY_DISPLAY_INVERSION_ENABLED)
            )
        ) doReturn isColorInversionEnabled

        whenever(
            mockSecureWrapper.getInt(
                internalLogger = any(),
                applicationContext = any(),
                key = eq(CAPTIONING_ENABLED_KEY)
            )
        ) doReturn isClosedCaptioningEnabled

        val animationDurationValue = if (isReducedAnimationsEnabled) 0.0f else 1.0f
        whenever(
            mockGlobalWrapper.getFloat(
                internalLogger = any(),
                applicationContext = any(),
                key = eq(Settings.Global.ANIMATOR_DURATION_SCALE)
            )
        ) doReturn animationDurationValue

        testedReader = createReader()

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result.textSize).isEqualTo(textSize.toString())
        assertThat(result.isScreenReaderEnabled).isEqualTo(isScreenReaderEnabled)
        assertThat(result.isScreenPinningEnabled).isEqualTo(isScreenPinningEnabled)
        assertThat(result.isColorInversionEnabled).isEqualTo(isColorInversionEnabled)
        assertThat(result.isClosedCaptioningEnabled).isEqualTo(isClosedCaptioningEnabled)
        assertThat(result.isReducedAnimationsEnabled).isEqualTo(isReducedAnimationsEnabled)
    }

    // region Text Size Tests

    @Test
    fun `M return text size W getState { valid font scale }`(
        @FloatForgery(min = 0.5f, max = 3.0f) fontScale: Float
    ) {
        // Given
        mockConfiguration.fontScale = fontScale

        testedReader = createReader()

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result.textSize).isEqualTo(fontScale.toString())
    }
    // endregion

    // region Screen Reader Tests
    @Test
    fun `M return screen reader state W getState { touch exploration enabled }`() {
        // Given
        whenever(mockAccessibilityManager.isTouchExplorationEnabled) doReturn true

        testedReader = createReader()

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result.isScreenReaderEnabled as Boolean).isTrue()
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
        assertThat(result.isScreenPinningEnabled).isEqualTo(lockState)
    }

    @TestTargetApi(Build.VERSION_CODES.M)
    @Test
    fun `M return true for screen pinning W getState { lock task mode is LOCKED }`() {
        // Given
        whenever(mockActivityManager.lockTaskModeState) doReturn ActivityManager.LOCK_TASK_MODE_LOCKED

        testedReader = createReader()

        // When
        val result = testedReader.getState()
        val isScreenPinningEnabled = result.isScreenPinningEnabled as Boolean

        // Then
        assertThat(isScreenPinningEnabled).isTrue()
    }

    @TestTargetApi(Build.VERSION_CODES.M)
    @Test
    fun `M return true for screen pinning W getState { lock task mode is PINNED }`() {
        // Given
        whenever(mockActivityManager.lockTaskModeState) doReturn ActivityManager.LOCK_TASK_MODE_PINNED

        testedReader = createReader()

        // When
        val result = testedReader.getState()
        val isScreenPinningEnabled = result.isScreenPinningEnabled as Boolean

        // Then
        assertThat(isScreenPinningEnabled).isTrue()
    }

    @TestTargetApi(Build.VERSION_CODES.M)
    @Test
    fun `M return false for screen pinning W getState { lock task mode is NONE }`() {
        // Given
        whenever(mockActivityManager.lockTaskModeState) doReturn ActivityManager.LOCK_TASK_MODE_NONE

        testedReader = createReader()

        // When
        val result = testedReader.getState()
        val isScreenPinningEnabled = result.isScreenPinningEnabled as Boolean

        // Then
        assertThat(isScreenPinningEnabled).isFalse()
    }

    @Test
    fun `M return null for screen pinning W getState { activity manager is null }`() {
        // Given
        testedReader = DefaultAccessibilityReader(
            internalLogger = mockInternalLogger,
            applicationContext = mockContext,
            resources = mockResources,
            activityManager = null,
            accessibilityManager = mockAccessibilityManager,
            secureWrapper = mockSecureWrapper,
            globalWrapper = mockGlobalWrapper,
            handler = mockHandler
        )

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result.isScreenPinningEnabled).isNull()
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

        testedReader = createReader()

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result.isColorInversionEnabled).isEqualTo(isEnabled)
    }

    @Test
    fun `M return null color inversion W getState { got null from secureInt }`() {
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
        assertThat(result.isColorInversionEnabled).isNull()
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

        testedReader = createReader()

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result.isClosedCaptioningEnabled).isEqualTo(isEnabled)
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
        assertThat(result.isClosedCaptioningEnabled).isNull()
    }
    // endregion

    // region Reduced Animations Tests
    @Test
    fun `M return true for reduced animations W getState { enabled }`() {
        // Given
        whenever(
            mockGlobalWrapper.getFloat(
                internalLogger = mockInternalLogger,
                applicationContext = mockContext,
                key = Settings.Global.ANIMATOR_DURATION_SCALE
            )
        ) doReturn 0.0f

        testedReader = createReader()

        // When
        val result = testedReader.getState()
        val isReducedAnimations = result.isReducedAnimationsEnabled as Boolean

        // Then
        assertThat(isReducedAnimations).isTrue()
    }

    @Test
    fun `M return false for reduced animations W getState { not enabled }`() {
        // Given
        whenever(
            mockGlobalWrapper.getFloat(
                internalLogger = mockInternalLogger,
                applicationContext = mockContext,
                key = Settings.Global.ANIMATOR_DURATION_SCALE
            )
        ) doReturn 1.0f

        testedReader = createReader()

        // When
        val result = testedReader.getState()
        val isReducedAnimations = result.isReducedAnimationsEnabled as Boolean

        // Then
        assertThat(isReducedAnimations).isFalse()
    }

    @Test
    fun `M return null for reduced animations W getState { null value }`() {
        // Given
        whenever(
            mockGlobalWrapper.getFloat(
                internalLogger = mockInternalLogger,
                applicationContext = mockContext,
                key = Settings.Global.ANIMATOR_DURATION_SCALE
            )
        ) doReturn null

        // When
        val result = testedReader.getState()

        // Then
        assertThat(result.isReducedAnimationsEnabled).isNull()
    }
    // endregion

    // region ComponentCallbacks Tests
    @Test
    fun `M update text size W onConfigurationChanged { font scale changes }`(
        @FloatForgery(min = 0.5f, max = 3.0f) newFontScale: Float
    ) {
        // Given
        val originalFontScale = 1.0f
        mockConfiguration.fontScale = originalFontScale

        // Establish initial state
        val initialResult = testedReader.getState()
        assertThat(initialResult.textSize).isEqualTo(originalFontScale.toString())

        // Change configuration
        val newConfiguration = Configuration().apply { fontScale = newFontScale }
        whenever(mockResources.configuration) doReturn newConfiguration

        // When
        testedReader.onConfigurationChanged(newConfiguration)

        // Then
        val result = testedReader.getState()
        assertThat(result.textSize).isEqualTo(newFontScale.toString())
    }

    @Test
    fun `M not update state W onConfigurationChanged { font scale unchanged }`(
        @FloatForgery(min = 0.5f, max = 3.0f) fontScale: Float
    ) {
        // Given
        mockConfiguration.fontScale = fontScale

        testedReader = createReader()
        val initialResult = testedReader.getState()

        val sameConfiguration = Configuration().apply { this.fontScale = fontScale }

        // When
        testedReader.onConfigurationChanged(sameConfiguration)

        // Then
        val result = testedReader.getState()
        assertThat(result.textSize).isEqualTo(fontScale.toString())
        assertThat(result).isEqualTo(initialResult)
    }

    @Test
    fun `M do nothing W onLowMemory { called }`() {
        // When/Then - should not throw
        @Suppress("DEPRECATION")
        testedReader.onLowMemory()

        // Verify state is still accessible
        val result = testedReader.getState()
        assertThat(result).isNotNull()
    }
    // endregion

    // region Cleanup Tests
    @Test
    fun `M unregister all listeners W cleanup { called }`() {
        // Given
        testedReader.getState()

        // When
        testedReader.cleanup()

        // Then
        verify(mockAccessibilityManager).removeTouchExplorationStateChangeListener(any())
        verify(mockContentResolver, times(3)).unregisterContentObserver(any())
        verify(mockContext).unregisterComponentCallbacks(testedReader)
    }

    @Test
    fun `M handle null accessibility manager W cleanup { accessibility manager is null }`() {
        // Given
        testedReader = DefaultAccessibilityReader(
            internalLogger = mockInternalLogger,
            applicationContext = mockContext,
            resources = mockResources,
            activityManager = mockActivityManager,
            accessibilityManager = null,
            secureWrapper = mockSecureWrapper,
            globalWrapper = mockGlobalWrapper,
            handler = mockHandler
        )

        testedReader.getState()

        // When/Then - does not throw
        testedReader.cleanup()

        verify(mockContentResolver, times(3)).unregisterContentObserver(any())
        verify(mockContext).unregisterComponentCallbacks(testedReader)
    }
    // endregion

    // region Polling Behavior Tests
    @Test
    fun `M not update screen pinning W getState { multiple calls within poll threshold }`() {
        // Given
        whenever(mockActivityManager.lockTaskModeState) doReturn ActivityManager.LOCK_TASK_MODE_NONE

        // First call to establish baseline
        val firstResult = testedReader.getState()
        assertThat(firstResult.isScreenPinningEnabled as Boolean).isFalse()

        // Change the underlying state
        whenever(mockActivityManager.lockTaskModeState) doReturn ActivityManager.LOCK_TASK_MODE_LOCKED

        // When - Second call immediately (within poll threshold)
        val secondResult = testedReader.getState()

        // Then - State should NOT be updated since polling doesn't happen within threshold
        assertThat(secondResult.isScreenPinningEnabled as Boolean).isFalse()
    }

    // endregion

    // region Listener Tests
    @Test
    fun `M update state W displayInversionListener onChange { display inversion changes }`() {
        // Given
        whenever(
            mockSecureWrapper.getInt(
                internalLogger = any(),
                applicationContext = any(),
                key = eq(ACCESSIBILITY_DISPLAY_INVERSION_ENABLED)
            )
        ) doReturn true

        testedReader.getState()

        // When
        val listenerField = testedReader.javaClass.getDeclaredField("displayInversionListener")
        listenerField.isAccessible = true
        val listener = listenerField.get(testedReader) as ContentObserver
        listener.onChange(false, null)

        // Then
        val result = testedReader.getState()
        assertThat(result.isColorInversionEnabled as Boolean).isTrue()
    }

    @Test
    fun `M update state W captioningListener onChange { captioning changes }`() {
        // Given
        whenever(
            mockSecureWrapper.getInt(
                internalLogger = any(),
                applicationContext = any(),
                key = eq(CAPTIONING_ENABLED_KEY)
            )
        ) doReturn true

        testedReader.getState()

        // When
        val listenerField = testedReader.javaClass.getDeclaredField("captioningListener")
        listenerField.isAccessible = true
        val listener = listenerField.get(testedReader) as ContentObserver
        listener.onChange(false, null)

        // Then
        val result = testedReader.getState()
        assertThat(result.isClosedCaptioningEnabled as Boolean).isTrue()
    }

    @Test
    fun `M update state W animationDurationListener onChange { animation duration changes }`() {
        // Given
        whenever(
            mockGlobalWrapper.getFloat(
                internalLogger = any(),
                applicationContext = any(),
                key = eq(Settings.Global.ANIMATOR_DURATION_SCALE)
            )
        ) doReturn 0.0f

        testedReader.getState()

        // When
        val listenerField = testedReader.javaClass.getDeclaredField("animationDurationListener")
        listenerField.isAccessible = true
        val listener = listenerField.get(testedReader) as ContentObserver
        listener.onChange(false, null)

        // Then
        val result = testedReader.getState()
        assertThat(result.isReducedAnimationsEnabled as Boolean).isTrue()
    }

    @Test
    fun `M update state W touchListener triggered { touch exploration changes }`() {
        // Given
        whenever(mockAccessibilityManager.isTouchExplorationEnabled) doReturn true

        testedReader.getState()

        // When
        val listenerField = testedReader.javaClass.getDeclaredField("touchListener")
        listenerField.isAccessible = true
        val listener = listenerField.get(testedReader) as TouchExplorationStateChangeListener
        listener.onTouchExplorationStateChanged(true)

        // Then
        val result = testedReader.getState()
        assertThat(result.isScreenReaderEnabled as Boolean).isTrue()
    }
    // endregion

    // region Polling Threshold Tests
    @Test
    fun `M update lastPollTime W getState { after threshold exceeded }`() {
        // Given
        whenever(mockActivityManager.lockTaskModeState) doReturn ActivityManager.LOCK_TASK_MODE_NONE

        // Initialize
        testedReader.getState()

        val lastPollTimeField = testedReader.javaClass.getDeclaredField("lastPollTime")
        lastPollTimeField.isAccessible = true
        val lastPollTime = lastPollTimeField.get(testedReader) as AtomicLong
        val oldTime = System.currentTimeMillis() - 31_000
        lastPollTime.set(oldTime)

        // When - Call after threshold exceeded
        val currentTimeBefore = System.currentTimeMillis()
        testedReader.getState()
        val currentTimeAfter = System.currentTimeMillis()

        // Then - lastPollTime should be updated to current time (within reasonable range)
        val newPollTime = lastPollTime.get()
        assertThat(newPollTime).isGreaterThanOrEqualTo(currentTimeBefore)
        assertThat(newPollTime).isLessThanOrEqualTo(currentTimeAfter)
        assertThat(newPollTime).isGreaterThan(oldTime)
    }

    @Test
    fun `M return consistent state W getState { after listener updates }`() {
        // Given
        whenever(
            mockSecureWrapper.getInt(
                internalLogger = any(),
                applicationContext = any(),
                key = any()
            )
        ) doReturn false

        val initialResult = testedReader.getState()

        whenever(
            mockSecureWrapper.getInt(
                internalLogger = any(),
                applicationContext = any(),
                key = eq(ACCESSIBILITY_DISPLAY_INVERSION_ENABLED)
            )
        ) doReturn true

        // When
        val listenerField = testedReader.javaClass.getDeclaredField("displayInversionListener")
        listenerField.isAccessible = true
        val listener = listenerField.get(testedReader) as ContentObserver
        listener.onChange(false, null)

        // Then
        val updatedResult = testedReader.getState()
        assertThat(updatedResult.isColorInversionEnabled as Boolean).isTrue()
        assertThat(updatedResult.isColorInversionEnabled).isNotEqualTo(initialResult.isColorInversionEnabled)
    }
    // endregion
}
