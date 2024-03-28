/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Activity
import android.os.Bundle
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.android.rum.tracking.StubComponentPredicate
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.forge.anException
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.AdvancedForgery
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
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
    lateinit var mockPredicate: ComponentPredicate<Activity>

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        testedStrategy = ActivityViewTrackingStrategy(true, mockPredicate)
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

    // region Track RUM View

    @Test
    fun `𝕄 start a RUM View event 𝕎 onActivityResumed()`() {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        whenever(mockPredicate.accept(mockActivity)) doReturn true
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        // When
        testedStrategy.onActivityResumed(mockActivity)

        // Then
        verify(rumMonitor.mockInstance).startView(
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
        ) extras: Map<String, String>,
        @StringForgery action: String,
        @StringForgery uri: String
    ) {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        val arguments = Bundle(extras.size)
        extras.forEach { (k, v) -> arguments.putString(k, v) }
        whenever(mockIntent.extras).thenReturn(arguments)
        whenever(mockIntent.action).thenReturn(action)
        whenever(mockIntent.dataString).thenReturn(uri)
        whenever(mockActivity.intent).thenReturn(mockIntent)
        whenever(mockPredicate.accept(mockActivity)) doReturn true
        val expectedAttributes = extras.map { (k, v) -> "view.arguments.$k" to v }
            .toMutableMap()
        expectedAttributes["view.intent.action"] = action
        expectedAttributes["view.intent.uri"] = uri
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        // When
        testedStrategy.onActivityResumed(mockActivity)

        // Then
        verify(rumMonitor.mockInstance).startView(
            mockActivity,
            mockActivity.resolveViewName(),
            expectedAttributes
        )
    }

    @Test
    fun `𝕄 start a RUM View event 𝕎 onActivityResumed() { getting intent extras throws }`(
        @StringForgery action: String,
        @StringForgery uri: String,
        forge: Forge
    ) {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        whenever(mockIntent.extras).thenThrow(forge.anException())
        whenever(mockIntent.action).thenReturn(action)
        whenever(mockIntent.dataString).thenReturn(uri)
        whenever(mockActivity.intent).thenReturn(mockIntent)
        whenever(mockPredicate.accept(mockActivity)) doReturn true
        val expectedAttributes = mapOf("view.intent.action" to action, "view.intent.uri" to uri)
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        // When
        testedStrategy.onActivityResumed(mockActivity)

        // Then
        verify(rumMonitor.mockInstance).startView(
            mockActivity,
            mockActivity.resolveViewName(),
            expectedAttributes
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
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        whenever(mockIntent.extras).thenReturn(arguments)
        whenever(mockActivity.intent).thenReturn(mockIntent)
        whenever(mockPredicate.accept(mockActivity)) doReturn true
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        // When
        testedStrategy.onActivityResumed(mockActivity)

        verify(rumMonitor.mockInstance).startView(
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
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        whenever(mockPredicate.accept(mockActivity)) doReturn true
        whenever(mockPredicate.getViewName(mockActivity)) doReturn fakeName
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        // When
        testedStrategy.onActivityResumed(mockActivity)

        // Then
        verify(rumMonitor.mockInstance).startView(
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
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        whenever(mockPredicate.accept(mockActivity)) doReturn true
        whenever(mockPredicate.getViewName(mockActivity)) doReturn fakeName
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        // When
        testedStrategy.onActivityResumed(mockActivity)

        // Then
        verify(rumMonitor.mockInstance).startView(
            mockActivity,
            mockActivity.resolveViewName(),
            emptyMap()
        )
    }

    @Test
    fun `𝕄 not stop RUM View 𝕎 onActivityPaused() { first display }`() {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        whenever(mockPredicate.accept(mockActivity)) doReturn true
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        // When
        testedStrategy.onActivityPaused(mockActivity)
        Thread.sleep(250)

        // Then
        verify(rumMonitor.mockInstance, never()).stopView(mockActivity, emptyMap())
    }

    @Test
    fun `𝕄 not stop RUM View 𝕎 onActivityPaused() { redisplay }`() {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        whenever(mockPredicate.accept(mockActivity)) doReturn true
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        // When
        testedStrategy.onActivityPaused(mockActivity)
        Thread.sleep(250)

        // Then
        verify(rumMonitor.mockInstance, never()).stopView(mockActivity, emptyMap())
    }

    @Test
    fun `𝕄 stop RUM View 𝕎 onActivityStopped() { first display }`() {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        whenever(mockPredicate.accept(mockActivity)) doReturn true
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        // When
        testedStrategy.onActivityStopped(mockActivity)
        Thread.sleep(250)

        // Then
        verify(rumMonitor.mockInstance).stopView(mockActivity, emptyMap())
    }

    @Test
    fun `𝕄 stop RUM View 𝕎 onActivityStopped() { redisplay }`() {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        whenever(mockPredicate.accept(mockActivity)) doReturn true
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        // When
        testedStrategy.onActivityStopped(mockActivity)
        Thread.sleep(250)

        // Then
        verify(rumMonitor.mockInstance).stopView(mockActivity, emptyMap())
    }

    // endregion

    // region Track RUM View (not tracked)

    @Test
    fun `𝕄 start a RUM View event 𝕎 onActivityResumed() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn false
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        // When
        testedStrategy.onActivityResumed(mockActivity)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `𝕄 update RUM View 𝕎 onActivityPaused() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockActivity)) doReturn false
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        // When
        testedStrategy.onActivityPaused(mockActivity)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }
    // endregion

    // region internal

    private fun Any.resolveViewName(): String {
        return javaClass.canonicalName ?: javaClass.simpleName
    }

    private fun <K, V> Iterable<Pair<K, V>>.toMutableMap(): MutableMap<K, V> {
        return toMap(mutableMapOf())
    }

    // endregion
}
