/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.datadog.android.core.internal.utils.resolveViewName
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.NoOpRumMonitor
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.tracking.OreoFragmentLifecycleCallbacks
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FragmentViewTrackingStrategyTest {

    lateinit var testedStrategy: FragmentViewTrackingStrategy

    @Mock
    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockAndroidxActivity: FragmentActivity

    @Mock
    lateinit var mockAndroidxFragmentManager: FragmentManager

    @Mock
    lateinit var mockDefaultFragmentManager: android.app.FragmentManager

    @Mock
    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockBadContext: Context

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    // region Strategy tests

    @BeforeEach
    fun `set up`(forge: Forge) {
        GlobalRum.registerIfAbsent(mockRumMonitor)
        whenever(mockAndroidxActivity.supportFragmentManager)
            .thenReturn(mockAndroidxFragmentManager)
        whenever(mockActivity.fragmentManager)
            .thenReturn(mockDefaultFragmentManager)
        testedStrategy = FragmentViewTrackingStrategy(true)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.isRegistered.set(false)
        GlobalRum.monitor = NoOpRumMonitor()
    }

    @Test
    fun `when register it will register as lifecycle callback`() {
        // when
        testedStrategy.register(mockAppContext)

        // verify
        verify(mockAppContext).registerActivityLifecycleCallbacks(testedStrategy)
    }

    @Test
    fun `when unregister it will remove itself  as lifecycle callback`() {
        // when
        testedStrategy.unregister(mockAppContext)

        // verify
        verify(mockAppContext).unregisterActivityLifecycleCallbacks(testedStrategy)
    }

    @Test
    fun `when register called with non application context will do nothing`() {
        // when
        testedStrategy.register(mockBadContext)

        // verify
        verifyZeroInteractions(mockBadContext)
    }

    @Test
    fun `when unregister called with non application context will do nothing`() {
        // when
        testedStrategy.unregister(mockBadContext)

        // verify
        verifyZeroInteractions(mockBadContext)
    }

    @Test
    fun `will start and stop a RumViewEvent when fragment resumes and pauses in a FragmentActivity`(
        forge: Forge
    ) {
        // given
        val mockFragment: Fragment = mockFragmentWithArguments(forge)
        val expectedAttrs = mockFragment.arguments!!.toRumAttributes()
        val argumentCaptor = argumentCaptor<FragmentManager.FragmentLifecycleCallbacks>()

        // when
        testedStrategy.onActivityStarted(mockAndroidxActivity)

        // then
        verify(mockAndroidxFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )
        verifyZeroInteractions(mockDefaultFragmentManager)

        // when
        argumentCaptor.firstValue.onFragmentResumed(mockAndroidxFragmentManager, mockFragment)
        argumentCaptor.firstValue.onFragmentPaused(mockAndroidxFragmentManager, mockFragment)

        // then
        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startView(
                eq(mockFragment),
                eq(mockFragment.resolveViewName()),
                eq(expectedAttrs)
            )
            verify(mockRumMonitor).stopView(
                mockFragment
            )
        }
    }

    @Test
    fun `it will do nothing if fragment is not whitelisted`(
        forge: Forge
    ) {
        // given
        val argumentCaptor = argumentCaptor<FragmentManager.FragmentLifecycleCallbacks>()
        val mockFragment: Fragment = mockFragmentWithArguments(forge)
        testedStrategy = FragmentViewTrackingStrategy(trackArguments = true,
            supportFragmentComponentPredicate = object : ComponentPredicate<Fragment> {
                override fun accept(component: Fragment): Boolean {
                    return false
                }
            })

        // when
        testedStrategy.onActivityStarted(mockAndroidxActivity)

        // then
        verify(mockAndroidxFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )
        verifyZeroInteractions(mockDefaultFragmentManager)

        // when
        argumentCaptor.firstValue.onFragmentResumed(mockAndroidxFragmentManager, mockFragment)
        argumentCaptor.firstValue.onFragmentPaused(mockAndroidxFragmentManager, mockFragment)

        // then
        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `will not attach fragment arguments as attributes if required so in a FragmentActivity`(
        forge: Forge
    ) {
        // given
        testedStrategy = FragmentViewTrackingStrategy(false)
        val mockFragment: Fragment = mockFragmentWithArguments(forge)
        val expectedAttrs = emptyMap<String, Any?>()
        val argumentCaptor = argumentCaptor<FragmentManager.FragmentLifecycleCallbacks>()

        // when
        testedStrategy.onActivityStarted(mockAndroidxActivity)

        // then
        verify(mockAndroidxFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )
        verifyZeroInteractions(mockDefaultFragmentManager)

        // when
        argumentCaptor.firstValue.onFragmentResumed(mockAndroidxFragmentManager, mockFragment)
        argumentCaptor.firstValue.onFragmentPaused(mockAndroidxFragmentManager, mockFragment)

        // then
        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startView(
                eq(mockFragment),
                eq(mockFragment.resolveViewName()),
                eq(expectedAttrs)
            )
            verify(mockRumMonitor).stopView(
                mockFragment
            )
        }
    }

    @Test
    fun `when FragmentActivity started it will reuse same callback`() {
        // when
        testedStrategy.onActivityStarted(mockAndroidxActivity)
        testedStrategy.onActivityStarted(mockAndroidxActivity)

        // then
        argumentCaptor<FragmentManager.FragmentLifecycleCallbacks> {
            verify(
                mockAndroidxFragmentManager,
                times(2)
            ).registerFragmentLifecycleCallbacks(capture(), eq(true))
            assertThat(firstValue).isEqualTo(secondValue)
        }
    }

    @Test
    fun `when FragmentActivity stopped will unregister the right callback`() {
        // given
        testedStrategy.onActivityStarted(mockAndroidxActivity)
        val argumentCaptor = argumentCaptor<FragmentManager.FragmentLifecycleCallbacks>()
        verify(mockAndroidxFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )

        // when
        testedStrategy.onActivityStopped(mockAndroidxActivity)

        // then
        verify(mockAndroidxFragmentManager)
            .unregisterFragmentLifecycleCallbacks(argumentCaptor.firstValue)
        verifyZeroInteractions(mockDefaultFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `when base activity started will register the right callback`() {
        // when
        testedStrategy.onActivityStarted(mockActivity)

        // then
        verify(mockDefaultFragmentManager)
            .registerFragmentLifecycleCallbacks(isA<OreoFragmentLifecycleCallbacks>(), eq(true))
        verifyZeroInteractions(mockAndroidxFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `when base activity started will register the same callback`() {
        // when
        testedStrategy.onActivityStarted(mockActivity)
        testedStrategy.onActivityStarted(mockActivity)

        // then
        argumentCaptor<android.app.FragmentManager.FragmentLifecycleCallbacks> {
            verify(
                mockDefaultFragmentManager,
                times(2)
            ).registerFragmentLifecycleCallbacks(capture(), eq(true))
            assertThat(firstValue).isEqualTo(secondValue)
        }
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `will start and stop a RumViewEvent when fragment resumes and pauses in a base activity`(
        forge: Forge
    ) {
        // given
        val mockFragment: android.app.Fragment = mockDeprecatedFragmentWithArguments(forge)
        val expectedAttrs = mockFragment.arguments.toRumAttributes()
        val argumentCaptor =
            argumentCaptor<android.app.FragmentManager.FragmentLifecycleCallbacks>()

        // when
        testedStrategy.onActivityStarted(mockActivity)

        // then
        verify(mockDefaultFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )
        verifyZeroInteractions(mockAndroidxFragmentManager)

        // when
        argumentCaptor.firstValue.onFragmentResumed(mockDefaultFragmentManager, mockFragment)
        argumentCaptor.firstValue.onFragmentPaused(mockDefaultFragmentManager, mockFragment)

        // then
        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startView(
                eq(mockFragment),
                eq(mockFragment.resolveViewName()),
                eq(expectedAttrs)
            )
            verify(mockRumMonitor).stopView(
                mockFragment
            )
        }
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `it will do nothing when fragment is not whitelisted`(
        forge: Forge
    ) {
        // given
        val mockFragment: android.app.Fragment = mockDeprecatedFragmentWithArguments(forge)
        testedStrategy = FragmentViewTrackingStrategy(trackArguments = true,
            defaultFragmentComponentPredicate = object : ComponentPredicate<android.app.Fragment> {
                override fun accept(component: android.app.Fragment): Boolean {
                    return false
                }
            })
        val argumentCaptor =
            argumentCaptor<android.app.FragmentManager.FragmentLifecycleCallbacks>()

        // when
        testedStrategy.onActivityStarted(mockActivity)

        // then
        verify(mockDefaultFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )
        verifyZeroInteractions(mockAndroidxFragmentManager)

        // when
        argumentCaptor.firstValue.onFragmentResumed(mockDefaultFragmentManager, mockFragment)
        argumentCaptor.firstValue.onFragmentPaused(mockDefaultFragmentManager, mockFragment)

        // then
        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `will not attach fragment arguments as attributes if required so in a base activity`(
        forge: Forge
    ) {
        // given
        testedStrategy = FragmentViewTrackingStrategy(false)
        val expectedAttrs = emptyMap<String, Any?>()
        val mockFragment: android.app.Fragment = mockDeprecatedFragmentWithArguments(forge)
        val argumentCaptor =
            argumentCaptor<android.app.FragmentManager.FragmentLifecycleCallbacks>()

        // when
        testedStrategy.onActivityStarted(mockActivity)

        // then
        verify(mockDefaultFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )
        verifyZeroInteractions(mockAndroidxFragmentManager)

        // when
        argumentCaptor.firstValue.onFragmentResumed(mockDefaultFragmentManager, mockFragment)
        argumentCaptor.firstValue.onFragmentPaused(mockDefaultFragmentManager, mockFragment)

        // then
        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startView(
                eq(mockFragment),
                eq(mockFragment.resolveViewName()),
                eq(expectedAttrs)
            )
            verify(mockRumMonitor).stopView(
                mockFragment
            )
        }
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `when base activity stopped will unregister the right callback`() {
        // given
        testedStrategy.onActivityStarted(mockActivity)
        val argumentCaptor =
            argumentCaptor<android.app.FragmentManager.FragmentLifecycleCallbacks>()
        verify(mockDefaultFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )

        // when
        testedStrategy.onActivityStopped(mockActivity)

        // then
        verify(mockDefaultFragmentManager)
            .unregisterFragmentLifecycleCallbacks(argumentCaptor.firstValue)
        verifyZeroInteractions(mockAndroidxFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `when base activity started API below O will do nothing`() {
        // when
        testedStrategy.onActivityStarted(mockActivity)

        // then
        verifyZeroInteractions(mockAndroidxFragmentManager)
        verifyZeroInteractions(mockDefaultFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `when base activity stopped API below O will do nothing`() {
        // when
        testedStrategy.onActivityStopped(mockActivity)

        // then
        verifyZeroInteractions(mockAndroidxFragmentManager)
        verifyZeroInteractions(mockDefaultFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `it will handle well a FragmentActivity and a base Activity resumed in the same time`() {
        // given
        val androidXArgumentCaptor = argumentCaptor<FragmentManager.FragmentLifecycleCallbacks>()
        val baseArgumentCaptor =
            argumentCaptor<android.app.FragmentManager.FragmentLifecycleCallbacks>()
        // when
        testedStrategy.onActivityStarted(mockAndroidxActivity)
        testedStrategy.onActivityStarted(mockActivity)
        testedStrategy.onActivityStopped(mockAndroidxActivity)
        testedStrategy.onActivityStopped(mockActivity)

        // then
        inOrder(mockAndroidxFragmentManager, mockDefaultFragmentManager) {
            verify(mockAndroidxFragmentManager)
                .registerFragmentLifecycleCallbacks(
                    androidXArgumentCaptor.capture(), eq(true)
                )
            verify(mockDefaultFragmentManager)
                .registerFragmentLifecycleCallbacks(
                    baseArgumentCaptor.capture(), eq(true)
                )
            verify(mockAndroidxFragmentManager)
                .unregisterFragmentLifecycleCallbacks(
                    androidXArgumentCaptor.firstValue
                )
            verify(mockDefaultFragmentManager)
                .unregisterFragmentLifecycleCallbacks(
                    baseArgumentCaptor.firstValue
                )
        }
    }

    // endregion

    // region Internal

    private fun mockFragmentWithArguments(forge: Forge): Fragment {
        val arguments = forgeFragmentArguments(forge)
        val fragment: Fragment = mock()
        whenever(fragment.arguments).thenReturn(arguments)
        return fragment
    }

    private fun mockDeprecatedFragmentWithArguments(forge: Forge): android.app.Fragment {
        val arguments = forgeFragmentArguments(forge)
        val fragment: android.app.Fragment = mock()
        whenever(fragment.arguments).thenReturn(arguments)
        return fragment
    }

    private fun forgeFragmentArguments(forge: Forge): Bundle {
        val arguments = Bundle()
        for (i in 0..10) {
            val key = forge.anAlphabeticalString()
            val value = forge.anAsciiString()
            arguments.putString(key, value)
        }
        return arguments
    }

    private fun Bundle.toRumAttributes(): Map<String, Any?> {
        val attributes = mutableMapOf<String, Any?>()
        keySet().forEach {
            attributes["view.arguments.$it"] = get(it)
        }
        return attributes
    }

    // endregion
}
