/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Activity
import android.os.Bundle
import com.datadog.android.rum.internal.domain.model.ViewEvent
import com.datadog.android.rum.internal.tracking.ViewLoadingTimer
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.setFieldValue
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
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
internal class ActivityViewTrackingStrategyTest : ActivityLifecycleTrackingStrategyTest() {

    @Mock
    lateinit var mockViewLoadingTimer: ViewLoadingTimer

    // region tests

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        underTest =
            ActivityViewTrackingStrategy(true)
        underTest.setFieldValue("viewLoadingTimer", mockViewLoadingTimer)
    }

    @Test
    fun `when created will notify the viewLoadingTimer`(forge: Forge) {
        // when
        underTest.onActivityCreated(mockActivity, null)
        // then
        verify(mockViewLoadingTimer).onCreated(mockActivity)
    }

    @Test
    fun `when created will do nothing if activity not whitelisted`(forge: Forge) {
        // given
        underTest = ActivityViewTrackingStrategy(true, componentPredicate = object :
            ComponentPredicate<Activity> {
            override fun accept(component: Activity): Boolean {
                return false
            }
        })
        // when
        underTest.onActivityCreated(mockActivity, null)
        // then
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    fun `when started will notify the viewLoadingTimer for startLoading`() {
        // whenever
        underTest.onActivityStarted(mockActivity)

        // then
        verify(mockViewLoadingTimer).onStartLoading(mockActivity)
    }

    @Test
    fun `when started and activity not whitelisted will do nothing`() {
        // given
        underTest = ActivityViewTrackingStrategy(trackExtras = false,
            componentPredicate = object :
                ComponentPredicate<Activity> {
                override fun accept(component: Activity): Boolean {
                    return false
                }
            })

        // whenever
        underTest.onActivityStarted(mockActivity)

        // then
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    fun `when resumed it will start a view event`(forge: Forge) {
        // when
        underTest.onActivityResumed(mockActivity)
        // then
        verify(mockRumMonitor).startView(
            eq(mockActivity),
            eq(mockActivity.resolveViewName()),
            eq(emptyMap())
        )
    }

    @Test
    fun `when resumed will start a view event with intent extras as attributes`(
        forge: Forge
    ) {
        // given
        val arguments = Bundle()
        val expectedAttrs = mutableMapOf<String, Any?>()
        for (i in 0..10) {
            val key = forge.anAlphabeticalString()
            val value = forge.anAsciiString()
            arguments.putString(key, value)
            expectedAttrs["view.arguments.$key"] = value
        }
        whenever(mockIntent.extras).thenReturn(arguments)
        whenever(mockActivity.intent).thenReturn(mockIntent)

        // whenever
        underTest.onActivityResumed(mockActivity)

        verify(mockRumMonitor).startView(
            eq(mockActivity),
            eq(mockActivity.resolveViewName()),
            eq(expectedAttrs)
        )
    }

    @Test
    fun `when resumed and not tracking intent extras will send empty attributes`(
        forge: Forge
    ) {
        // given
        underTest =
            ActivityViewTrackingStrategy(false)
        val arguments = Bundle()
        for (i in 0..10) {
            val key = forge.anAlphabeticalString()
            val value = forge.anAsciiString()
            arguments.putString(key, value)
        }
        whenever(mockIntent.extras).thenReturn(arguments)
        whenever(mockActivity.intent).thenReturn(mockIntent)

        // whenever
        underTest.onActivityResumed(mockActivity)

        verify(mockRumMonitor).startView(
            eq(mockActivity),
            eq(mockActivity.resolveViewName()),
            eq(emptyMap())
        )
    }

    @Test
    fun `when resumed will do nothing if activity is not whitelisted`() {
        // given
        underTest = ActivityViewTrackingStrategy(trackExtras = false,
            componentPredicate = object :
                ComponentPredicate<Activity> {
                override fun accept(component: Activity): Boolean {
                    return false
                }
            })

        // whenever
        underTest.onActivityResumed(mockActivity)

        // then
        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `when postResumed will notify the viewLoadingTimer for stopLoading`() {
        // whenever
        underTest.onActivityPostResumed(mockActivity)

        // then
        verify(mockViewLoadingTimer).onFinishedLoading(mockActivity)
    }

    @Test
    fun `when postResumed and activity not whitelisted will do nothing`() {
        // given
        underTest = ActivityViewTrackingStrategy(trackExtras = false,
            componentPredicate = object :
                ComponentPredicate<Activity> {
                override fun accept(component: Activity): Boolean {
                    return false
                }
            })

        // whenever
        underTest.onActivityPostResumed(mockActivity)

        // then
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    fun `when paused it will update the view loading time and stop it in this order`(forge: Forge) {
        // given
        val expectedLoadingTime = forge.aLong()
        val firsTimeLoading = forge.aBool()
        val expectedLoadingType =
            if (firsTimeLoading) {
                ViewEvent.LoadingType.ACTIVITY_DISPLAY
            } else {
                ViewEvent.LoadingType.ACTIVITY_REDISPLAY
            }
        whenever(mockViewLoadingTimer.getLoadingTime(mockActivity))
            .thenReturn(expectedLoadingTime)
        whenever(mockViewLoadingTimer.isFirstTimeLoading(mockActivity))
            .thenReturn(firsTimeLoading)

        // when
        underTest.onActivityPaused(mockActivity)

        // then
        inOrder(mockRumMonitor, mockViewLoadingTimer) {
            verify(mockRumMonitor).updateViewLoadingTime(
                mockActivity,
                expectedLoadingTime,
                expectedLoadingType
            )
            verify(mockRumMonitor).stopView(mockActivity, emptyMap())
            verify(mockViewLoadingTimer).onPaused(mockActivity)
        }
    }

    @Test
    fun `when paused will do nothing if activity is not whitelisted`() {
        // given
        underTest = ActivityViewTrackingStrategy(trackExtras = false,
            componentPredicate = object :
                ComponentPredicate<Activity> {
                override fun accept(component: Activity): Boolean {
                    return false
                }
            })

        // whenever
        underTest.onActivityPaused(mockActivity)

        // then
        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `when activity destroyed will notify the viewLoadingTimer for onDestroy`() {
        // whenever
        underTest.onActivityDestroyed(mockActivity)

        // then
        verify(mockViewLoadingTimer).onDestroyed(mockActivity)
    }

    @Test
    fun `when activity destroyed and not whitelisted will do nothing`() {
        // given
        underTest = ActivityViewTrackingStrategy(trackExtras = false,
            componentPredicate = object :
                ComponentPredicate<Activity> {
                override fun accept(component: Activity): Boolean {
                    return false
                }
            })

        // whenever
        underTest.onActivityDestroyed(mockActivity)

        // then
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    // endregion

    // region internal

    private fun Any.resolveViewName(): String {
        return javaClass.canonicalName ?: javaClass.simpleName
    }

    // endregion
}
