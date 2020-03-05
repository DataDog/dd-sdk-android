package com.datadog.android.androidx.fragments

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
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
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackFragmentsAsViewsStrategyTest {
    lateinit var underTest: TrackFragmentsAsViewsStrategy
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

    @BeforeEach
    fun `set up`() {
        whenever(mockAndroidxActivity.supportFragmentManager)
            .thenReturn(mockAndroidxFragmentManager)
        whenever(mockActivity.fragmentManager)
            .thenReturn(mockDefaultFragmentManager)
        underTest = TrackFragmentsAsViewsStrategy()
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
    fun `when androidx fragment activity resumed will register the right callback`() {
        // when
        underTest.onActivityResumed(mockAndroidxActivity)

        // then
        verify(mockAndroidxFragmentManager)
            .registerFragmentLifecycleCallbacks(CompatFragmentLifecycleCallbacks, true)
        verifyZeroInteractions(mockDefaultFragmentManager)
    }

    @Test
    fun `when androidx fragment activity paused will unregister the right callback`() {
        // when
        underTest.onActivityPaused(mockAndroidxActivity)

        // then
        verify(mockAndroidxFragmentManager)
            .unregisterFragmentLifecycleCallbacks(CompatFragmentLifecycleCallbacks)
        verifyZeroInteractions(mockDefaultFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `when base activity resumed will register the right callback`() {
        // when
        underTest.onActivityResumed(mockActivity)

        // then
        verify(mockDefaultFragmentManager)
            .registerFragmentLifecycleCallbacks(DefaultFragmentLifecycleCallbacks, true)
        verifyZeroInteractions(mockAndroidxFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `when base activity paused will unregister the right callback`() {
        // when
        underTest.onActivityPaused(mockActivity)

        // then
        verify(mockDefaultFragmentManager)
            .unregisterFragmentLifecycleCallbacks(DefaultFragmentLifecycleCallbacks)
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
}
