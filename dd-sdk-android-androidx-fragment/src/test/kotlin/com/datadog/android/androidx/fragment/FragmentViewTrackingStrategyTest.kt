package com.datadog.android.androidx.fragment

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.datadog.android.androidx.fragment.internal.OreoFragmentLifecycleCallbacks
import com.datadog.android.androidx.fragment.internal.RumMonitorBasedTest
import com.datadog.android.androidx.fragment.internal.resolveViewName
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
internal class FragmentViewTrackingStrategyTest : RumMonitorBasedTest() {
    lateinit var underTest: FragmentViewTrackingStrategy

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

    // region Strategy tests

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        whenever(mockAndroidxActivity.supportFragmentManager)
            .thenReturn(mockAndroidxFragmentManager)
        whenever(mockActivity.fragmentManager)
            .thenReturn(mockDefaultFragmentManager)
        underTest = FragmentViewTrackingStrategy(true)
    }

    @Test
    fun `when register it will register as lifecycle callback`() {
        // when
        underTest.register(mockAppContext)

        // verify
        verify(mockAppContext).registerActivityLifecycleCallbacks(underTest)
    }

    @Test
    fun `when unregister it will remove itself  as lifecycle callback`() {
        // when
        underTest.unregister(mockAppContext)

        // verify
        verify(mockAppContext).unregisterActivityLifecycleCallbacks(underTest)
    }

    @Test
    fun `when register called with non application context will do nothing`() {
        // when
        underTest.register(mockBadContext)

        // verify
        verifyZeroInteractions(mockBadContext)
    }

    @Test
    fun `when unregister called with non application context will do nothing`() {
        // when
        underTest.unregister(mockBadContext)

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
        underTest.onActivityResumed(mockAndroidxActivity)

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
    fun `will not attach fragment arguments as attributes if required so in a FragmentActivity`(
        forge: Forge
    ) {
        // given
        underTest = FragmentViewTrackingStrategy(false)
        val mockFragment: Fragment = mockFragmentWithArguments(forge)
        val expectedAttrs = emptyMap<String, Any?>()
        val argumentCaptor = argumentCaptor<FragmentManager.FragmentLifecycleCallbacks>()

        // when
        underTest.onActivityResumed(mockAndroidxActivity)

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
    fun `when FragmentActivity resumed it will reuse same callback`() {
        // when
        underTest.onActivityResumed(mockAndroidxActivity)
        underTest.onActivityResumed(mockAndroidxActivity)

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
    fun `when FragmentActivity paused will unregister the right callback`() {
        // given
        underTest.onActivityResumed(mockAndroidxActivity)
        val argumentCaptor = argumentCaptor<FragmentManager.FragmentLifecycleCallbacks>()
        verify(mockAndroidxFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )

        // when
        underTest.onActivityPaused(mockAndroidxActivity)

        // then
        verify(mockAndroidxFragmentManager)
            .unregisterFragmentLifecycleCallbacks(argumentCaptor.firstValue)
        verifyZeroInteractions(mockDefaultFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `when base activity resumed will register the right callback`() {
        // when
        underTest.onActivityResumed(mockActivity)

        // then
        verify(mockDefaultFragmentManager)
            .registerFragmentLifecycleCallbacks(isA<OreoFragmentLifecycleCallbacks>(), eq(true))
        verifyZeroInteractions(mockAndroidxFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `when base activity resumed will register the same callback`() {
        // when
        underTest.onActivityResumed(mockActivity)
        underTest.onActivityResumed(mockActivity)

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
        underTest.onActivityResumed(mockActivity)

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
    fun `will not attach fragment arguments as attributes if required so in a base activity`(
        forge: Forge
    ) {
        // given
        underTest = FragmentViewTrackingStrategy(false)
        val expectedAttrs = emptyMap<String, Any?>()
        val mockFragment: android.app.Fragment = mockDeprecatedFragmentWithArguments(forge)
        val argumentCaptor =
            argumentCaptor<android.app.FragmentManager.FragmentLifecycleCallbacks>()

        // when
        underTest.onActivityResumed(mockActivity)

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
    fun `when base activity paused will unregister the right callback`() {
        // given
        underTest.onActivityResumed(mockActivity)
        val argumentCaptor =
            argumentCaptor<android.app.FragmentManager.FragmentLifecycleCallbacks>()
        verify(mockDefaultFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )

        // when
        underTest.onActivityPaused(mockActivity)

        // then
        verify(mockDefaultFragmentManager)
            .unregisterFragmentLifecycleCallbacks(argumentCaptor.firstValue)
        verifyZeroInteractions(mockAndroidxFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `when base activity resumed API below O will do nothing`() {
        // when
        underTest.onActivityResumed(mockActivity)

        // then
        verifyZeroInteractions(mockAndroidxFragmentManager)
        verifyZeroInteractions(mockDefaultFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `when base activity paused API below O will do nothing`() {
        // when
        underTest.onActivityPaused(mockActivity)

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
        underTest.onActivityResumed(mockAndroidxActivity)
        underTest.onActivityResumed(mockActivity)
        underTest.onActivityPaused(mockAndroidxActivity)
        underTest.onActivityPaused(mockActivity)

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
        val arguments = arguments(forge)
        val fragment: Fragment = mock()
        whenever(fragment.arguments).thenReturn(arguments)
        return fragment
    }

    private fun mockDeprecatedFragmentWithArguments(forge: Forge): android.app.Fragment {
        val arguments = arguments(forge)
        val fragment: android.app.Fragment = mock()
        whenever(fragment.arguments).thenReturn(arguments)
        return fragment
    }

    private fun arguments(forge: Forge): Bundle {
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
