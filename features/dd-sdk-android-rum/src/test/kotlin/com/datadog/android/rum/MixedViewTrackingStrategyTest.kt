/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.os.Bundle
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.FragmentViewTrackingStrategy
import com.datadog.android.rum.tracking.MixedViewTrackingStrategy
import com.datadog.android.rum.tracking.StubComponentPredicate
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class MixedViewTrackingStrategyTest :
    ActivityLifecycleTrackingStrategyTest<MixedViewTrackingStrategy>() {

    @Mock
    lateinit var mockActivityViewTrackingStrategy: ActivityViewTrackingStrategy

    @Mock
    lateinit var mockFragmentViewTrackingStrategy: FragmentViewTrackingStrategy

    @Mock
    lateinit var mockBundle: Bundle

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    // region tests

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        testedStrategy =
            MixedViewTrackingStrategy(
                mockActivityViewTrackingStrategy,
                mockFragmentViewTrackingStrategy
            )
    }

    @Test
    fun `when registered will delegate to the bundled strategies`() {
        // Whenever
        testedStrategy.register(mockSdkCore, mockAppContext)

        // Then
        verify(mockActivityViewTrackingStrategy).register(mockSdkCore, mockAppContext)
        verify(mockFragmentViewTrackingStrategy).register(mockSdkCore, mockAppContext)
    }

    @Test
    fun `when unregistered will delegate to the bundled strategies`() {
        // Whenever
        testedStrategy.unregister(mockAppContext)

        // Then
        verify(mockActivityViewTrackingStrategy).unregister(mockAppContext)
        verify(mockFragmentViewTrackingStrategy).unregister(mockAppContext)
    }

    @Test
    fun `when activity created will not delegate to the bundled strategies`() {
        // Whenever
        testedStrategy.onActivityCreated(mockActivity, mockBundle)

        // Then
        verifyNoInteractions(mockActivityViewTrackingStrategy, mockFragmentViewTrackingStrategy)
    }

    @Test
    fun `when activity destroyed will not delegate to the bundled strategies`() {
        // Whenever
        testedStrategy.onActivityDestroyed(mockActivity)

        // Then
        verifyNoInteractions(mockActivityViewTrackingStrategy, mockFragmentViewTrackingStrategy)
    }

    @Test
    fun `when activity started will not delegate to the bundled strategies`() {
        // Whenever
        testedStrategy.onActivityStarted(mockActivity)

        // Then
        verifyNoInteractions(mockActivityViewTrackingStrategy, mockFragmentViewTrackingStrategy)
    }

    @Test
    fun `when activity stopped will not delegate to the bundled strategies`() {
        // Whenever
        testedStrategy.onActivityStopped(mockActivity)

        // Then
        verifyNoInteractions(mockActivityViewTrackingStrategy, mockFragmentViewTrackingStrategy)
    }

    @Test
    fun `when activity resumed will not delegate to the bundled strategies`() {
        // Whenever
        testedStrategy.onActivityResumed(mockActivity)

        // Then
        verifyNoInteractions(mockActivityViewTrackingStrategy, mockFragmentViewTrackingStrategy)
    }

    @Test
    fun `when activity paused will not delegate to the bundled strategies`() {
        // Whenever
        testedStrategy.onActivityPaused(mockActivity)

        // Then
        verifyNoInteractions(mockActivityViewTrackingStrategy, mockFragmentViewTrackingStrategy)
    }

    // endregion

    override fun createInstance(forge: Forge): MixedViewTrackingStrategy {
        return MixedViewTrackingStrategy(
            forge.aBool(),
            StubComponentPredicate(forge),
            StubComponentPredicate(forge),
            StubComponentPredicate(forge)
        )
    }

    override fun createEqualInstance(
        source: MixedViewTrackingStrategy,
        forge: Forge
    ): MixedViewTrackingStrategy {
        val trackExtras = source.activityViewTrackingStrategy.trackExtras &&
            source.fragmentViewTrackingStrategy.trackArguments
        val activityCP = source.activityViewTrackingStrategy.componentPredicate
        val supportCP = source.fragmentViewTrackingStrategy.supportFragmentComponentPredicate
        val defaultCP = source.fragmentViewTrackingStrategy.defaultFragmentComponentPredicate
        return MixedViewTrackingStrategy(trackExtras, activityCP, supportCP, defaultCP)
    }

    override fun createUnequalInstance(
        source: MixedViewTrackingStrategy,
        forge: Forge
    ): MixedViewTrackingStrategy? {
        return MixedViewTrackingStrategy(
            !source.activityViewTrackingStrategy.trackExtras,
            StubComponentPredicate(forge, useAlpha = false),
            StubComponentPredicate(forge, useAlpha = false),
            StubComponentPredicate(forge, useAlpha = false)
        )
    }
}
