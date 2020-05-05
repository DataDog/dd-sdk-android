/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.os.Bundle
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.FragmentViewTrackingStrategy
import com.datadog.android.rum.tracking.MixedViewTrackingStrategy
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.verify
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class MixedViewTrackingStrategyTest : ActivityLifecycleTrackingStrategyTest() {

    @Mock
    lateinit var mockedActivityViewTrackingStrategy: ActivityViewTrackingStrategy

    @Mock
    lateinit var mockedFragmentViewTrackingStrategy: FragmentViewTrackingStrategy

    @Mock
    lateinit var mockedBundle: Bundle

    // region tests

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        underTest =
            MixedViewTrackingStrategy(
                mockedActivityViewTrackingStrategy,
                mockedFragmentViewTrackingStrategy
            )
    }

    @Test
    fun `when created will delegate to the bundled strategies`(
        forge: Forge
    ) {

        // whenever
        underTest.onActivityCreated(mockActivity, mockedBundle)

        // then
        inOrder(mockedActivityViewTrackingStrategy, mockedFragmentViewTrackingStrategy) {
            verify(mockedActivityViewTrackingStrategy).onActivityCreated(mockActivity, mockedBundle)
            verify(mockedFragmentViewTrackingStrategy).onActivityCreated(mockActivity, mockedBundle)
        }
    }

    @Test
    fun `when destroyed will delegate to the bundled strategies`(
        forge: Forge
    ) {
        // whenever
        underTest.onActivityDestroyed(mockActivity)

        // then
        inOrder(mockedActivityViewTrackingStrategy, mockedFragmentViewTrackingStrategy) {
            verify(mockedActivityViewTrackingStrategy).onActivityDestroyed(mockActivity)
            verify(mockedFragmentViewTrackingStrategy).onActivityDestroyed(mockActivity)
        }
    }

    @Test
    fun `when started will delegate to the bundled strategies`(
        forge: Forge
    ) {

        // whenever
        underTest.onActivityStarted(mockActivity)

        // then
        inOrder(mockedActivityViewTrackingStrategy, mockedFragmentViewTrackingStrategy) {
            verify(mockedActivityViewTrackingStrategy).onActivityStarted(mockActivity)
            verify(mockedFragmentViewTrackingStrategy).onActivityStarted(mockActivity)
        }
    }

    @Test
    fun `when stopped will delegate to the bundled strategies`(
        forge: Forge
    ) {
        // whenever
        underTest.onActivityStopped(mockActivity)

        // then
        inOrder(mockedActivityViewTrackingStrategy, mockedFragmentViewTrackingStrategy) {
            verify(mockedActivityViewTrackingStrategy).onActivityStopped(mockActivity)
            verify(mockedFragmentViewTrackingStrategy).onActivityStopped(mockActivity)
        }
    }

    @Test
    fun `when resumed will delegate to the bundled strategies`(
        forge: Forge
    ) {

        // whenever
        underTest.onActivityResumed(mockActivity)

        // then
        inOrder(mockedActivityViewTrackingStrategy, mockedFragmentViewTrackingStrategy) {
            verify(mockedActivityViewTrackingStrategy).onActivityResumed(mockActivity)
            verify(mockedFragmentViewTrackingStrategy).onActivityResumed(mockActivity)
        }
    }

    @Test
    fun `when paused will delegate to the bundled strategies`(
        forge: Forge
    ) {
        // whenever
        underTest.onActivityPaused(mockActivity)

        // then
        inOrder(mockedActivityViewTrackingStrategy, mockedFragmentViewTrackingStrategy) {
            verify(mockedActivityViewTrackingStrategy).onActivityPaused(mockActivity)
            verify(mockedFragmentViewTrackingStrategy).onActivityPaused(mockActivity)
        }
    }

    // endregion
}
