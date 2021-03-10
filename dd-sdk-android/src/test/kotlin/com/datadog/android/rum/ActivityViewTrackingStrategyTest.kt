/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Activity
import android.os.Bundle
import com.datadog.android.rum.internal.tracking.ViewLoadingTimer
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.android.rum.tracking.StubComponentPredicate
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.setFieldValue
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.MapForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ActivityViewTrackingStrategyTest :
    ActivityLifecycleTrackingStrategyTest<ActivityViewTrackingStrategy>() {

    @Mock
    lateinit var mockViewLoadingTimer: ViewLoadingTimer

    @Mock
    lateinit var mockPredicate: ComponentPredicate<Activity>

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        testedStrategy = ActivityViewTrackingStrategy(true, mockPredicate)
        testedStrategy.setFieldValue("viewLoadingTimer", mockViewLoadingTimer)
    }

    override fun createInstance(forge: Forge): ActivityViewTrackingStrategy {
        return ActivityViewTrackingStrategy(
            forge.aBool(),
            StubComponentPredicate(forge)
        )
    }

    override fun createEqualInstance(
        source: ActivityViewTrackingStrategy,
        forge: Forge
    ): ActivityViewTrackingStrategy {
        val componentPredicate = source.componentPredicate
        check(componentPredicate is StubComponentPredicate)
        return ActivityViewTrackingStrategy(
            source.trackExtras,
            StubComponentPredicate(
                componentPredicate.componentName,
                componentPredicate.name
            )
        )
    }

    override fun createUnequalInstance(
        source: ActivityViewTrackingStrategy,
        forge: Forge
    ): ActivityViewTrackingStrategy {
        val componentPredicate = source.componentPredicate
        check(componentPredicate is StubComponentPredicate)
        return ActivityViewTrackingStrategy(
            !source.trackExtras,
            StubComponentPredicate(
                componentPredicate.componentName + forge.aNumericalString(),
                componentPredicate.name + forge.aNumericalString()
            )
        )
    }

    // region Track View Loading Time

    @Test
    fun `𝕄 notify viewLoadingTimer 𝕎 onActivityCreated()`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn true

        // When
        testedStrategy.onActivityCreated(mockActivity, null)

        // Then
        verify(mockViewLoadingTimer).onCreated(mockActivity)
    }

    @Test
    fun `𝕄 notify viewLoadingTimer 𝕎 onActivityStarted()`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn true

        // When
        testedStrategy.onActivityStarted(mockActivity)

        // Then
        verify(mockViewLoadingTimer).onStartLoading(mockActivity)
    }

    @Test
    fun `𝕄 notify viewLoadingTimer 𝕎 onActivityResumed()`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn true

        // When
        testedStrategy.onActivityResumed(mockActivity)

        // Then
        verify(mockViewLoadingTimer).onFinishedLoading(mockActivity)
    }

    @Test
    fun `𝕄 notify viewLoadingTimer 𝕎 onActivityPostResumed()`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn true

        // When
        testedStrategy.onActivityPostResumed(mockActivity)

        // Then
        verify(mockViewLoadingTimer).onFinishedLoading(mockActivity)
    }

    @Test
    fun `𝕄 notify viewLoadingTimer 𝕎 onActivityPaused()`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn true

        // When
        testedStrategy.onActivityPaused(mockActivity)

        // Then
        verify(mockViewLoadingTimer).onPaused(mockActivity)
    }

    @Test
    fun `𝕄 notify viewLoadingTimer 𝕎 onActivityDestroyed()`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn true

        // When
        testedStrategy.onActivityDestroyed(mockActivity)

        // Then
        verify(mockViewLoadingTimer).onDestroyed(mockActivity)
    }

    // endregion

    // region Track View Loading Time (not tracked)

    @Test
    fun `𝕄 do nothing 𝕎 onActivityCreated() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn false

        // When
        testedStrategy.onActivityCreated(mockActivity, null)

        // Then
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    fun `𝕄 do nothing 𝕎 onActivityStarted() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn false

        // When
        testedStrategy.onActivityStarted(mockActivity)

        // Then
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    fun `𝕄 do nothing 𝕎 onActivityResumed() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn false

        // When
        testedStrategy.onActivityResumed(mockActivity)

        // Then
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    fun `𝕄 do nothing 𝕎 onActivityPostResumed() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn false

        // When
        testedStrategy.onActivityPostResumed(mockActivity)

        // Then
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    fun `𝕄 do nothing 𝕎 onActivityPaused() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn false

        // When
        testedStrategy.onActivityPaused(mockActivity)

        // Then
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    fun `𝕄 do nothing 𝕎 onActivityDestroyed() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn false

        // When
        testedStrategy.onActivityDestroyed(mockActivity)

        // Then
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    // endregion

    // region Track RUM View

    @Test
    fun `𝕄 start a RUM View event 𝕎 onActivityResumed()`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn true

        // When
        testedStrategy.onActivityResumed(mockActivity)

        // Then
        verify(mockRumMonitor).startView(
            mockActivity,
            mockActivity.resolveViewName(),
            emptyMap()
        )
    }

    @Test
    fun `𝕄 start a RUM View event 𝕎 onActivityResumed() {extra attributes}`(
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ASCII)])
        ) attributes: Map<String, String>
    ) {
        // Given
        val arguments = Bundle(attributes.size)
        attributes.forEach { (k, v) -> arguments.putString(k, v) }
        whenever(mockIntent.extras).thenReturn(arguments)
        whenever(mockActivity.intent).thenReturn(mockIntent)
        whenever(mockPredicate.accept(mockActivity)) doReturn true

        // When
        testedStrategy.onActivityResumed(mockActivity)

        // Then
        verify(mockRumMonitor).startView(
            mockActivity,
            mockActivity.resolveViewName(),
            attributes.map { (k, v) -> "view.arguments.$k" to v }.toMap()
        )
    }

    @Test
    fun `𝕄 start a RUM View event 𝕎 onActivityResumed() {extra attributes not tracked}`(
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ASCII)])
        ) attributes: Map<String, String>
    ) {
        // Given
        val arguments = Bundle(attributes.size)
        attributes.forEach { (k, v) -> arguments.putString(k, v) }
        testedStrategy = ActivityViewTrackingStrategy(false, mockPredicate)
        whenever(mockIntent.extras).thenReturn(arguments)
        whenever(mockActivity.intent).thenReturn(mockIntent)
        whenever(mockPredicate.accept(mockActivity)) doReturn true

        // When
        testedStrategy.onActivityResumed(mockActivity)

        verify(mockRumMonitor).startView(
            mockActivity,
            mockActivity.resolveViewName(),
            emptyMap()
        )
    }

    @Test
    fun `𝕄 start a RUM View event 𝕎 onActivityResumed() {custom view name}`(
        @StringForgery fakeName: String
    ) {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn true
        whenever(mockPredicate.getViewName(mockActivity)) doReturn fakeName

        // When
        testedStrategy.onActivityResumed(mockActivity)

        // Then
        verify(mockRumMonitor).startView(
            mockActivity,
            fakeName,
            emptyMap()
        )
    }

    @Test
    fun `𝕄 start a RUM View event 𝕎 onActivityResumed() {custom blank view name}`(
        @StringForgery(StringForgeryType.WHITESPACE) fakeName: String
    ) {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn true
        whenever(mockPredicate.getViewName(mockActivity)) doReturn fakeName

        // When
        testedStrategy.onActivityResumed(mockActivity)

        // Then
        verify(mockRumMonitor).startView(
            mockActivity,
            mockActivity.resolveViewName(),
            emptyMap()
        )
    }

    @Test
    fun `𝕄 stop RUM View and update loading time 𝕎 onActivityPaused()`(
        @BoolForgery firstTimeLoading: Boolean,
        @LongForgery(1L) loadingTime: Long
    ) {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn true
        val expectedLoadingType = if (firstTimeLoading) {
            ViewEvent.LoadingType.ACTIVITY_DISPLAY
        } else {
            ViewEvent.LoadingType.ACTIVITY_REDISPLAY
        }
        whenever(mockViewLoadingTimer.getLoadingTime(mockActivity)) doReturn loadingTime
        whenever(mockViewLoadingTimer.isFirstTimeLoading(mockActivity)) doReturn firstTimeLoading

        // When
        testedStrategy.onActivityPaused(mockActivity)

        // Then
        inOrder(mockRumMonitor, mockViewLoadingTimer) {
            verify(mockRumMonitor).updateViewLoadingTime(
                mockActivity,
                loadingTime,
                expectedLoadingType
            )
            verify(mockRumMonitor).stopView(mockActivity, emptyMap())
            verify(mockViewLoadingTimer).onPaused(mockActivity)
        }
    }

    // endregion

    // region Track RUM View (not tracked)

    @Test
    fun `𝕄 start a RUM View event 𝕎 onActivityResumed() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn false

        // When
        testedStrategy.onActivityResumed(mockActivity)

        // Then
        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `𝕄 update RUM View loading time 𝕎 onActivityPaused() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn false

        // When
        testedStrategy.onActivityPaused(mockActivity)

        // Then
        verifyZeroInteractions(mockRumMonitor, mockViewLoadingTimer)
    }
    // endregion

    // region internal

    private fun Any.resolveViewName(): String {
        return javaClass.canonicalName ?: javaClass.simpleName
    }

    // endregion
}
