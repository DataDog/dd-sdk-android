package com.datadog.android.support.fragment

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
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
class FragmentViewTrackingStrategyTest {
    lateinit var underTest: FragmentViewTrackingStrategy
    @Mock
    lateinit var mockActivity: Activity
    @Mock
    lateinit var mockAndroidSupportActivity: FragmentActivity
    @Mock
    lateinit var mockAndroidSupportFragmentManager: FragmentManager
    @Mock
    lateinit var mockDefaultFragmentManager: android.app.FragmentManager
    @Mock
    lateinit var mockAppContext: Application
    @Mock
    lateinit var mockBadContext: Context

    @BeforeEach
    fun `set up`() {
        whenever(mockAndroidSupportActivity.supportFragmentManager)
            .thenReturn(mockAndroidSupportFragmentManager)
        whenever(mockActivity.fragmentManager)
            .thenReturn(mockDefaultFragmentManager)
        underTest = FragmentViewTrackingStrategy()
    }

    // region ActivityLifecycleTrackingStrategy

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

    // endregion

    // region FragmentViewTrackingStrategy

    @Test
    fun `when android support fragment activity resumed will register the right callback`() {
        // when
        underTest.onActivityResumed(mockAndroidSupportActivity)

        // then
        verify(mockAndroidSupportFragmentManager)
            .registerFragmentLifecycleCallbacks(CompatFragmentLifecycleCallbacks, true)
        verifyZeroInteractions(mockDefaultFragmentManager)
    }

    @Test
    fun `when android support fragment activity paused will unregister the right callback`() {
        // when
        underTest.onActivityPaused(mockAndroidSupportActivity)

        // then
        verify(mockAndroidSupportFragmentManager)
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
            .registerFragmentLifecycleCallbacks(OreoFragmentLifecycleCallbacks, true)
        verifyZeroInteractions(mockAndroidSupportFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `when base activity paused will unregister the right callback`() {
        // when
        underTest.onActivityPaused(mockActivity)

        // then
        verify(mockDefaultFragmentManager)
            .unregisterFragmentLifecycleCallbacks(OreoFragmentLifecycleCallbacks)
        verifyZeroInteractions(mockAndroidSupportFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `when base activity resumed API below O will do nothing`() {
        // when
        underTest.onActivityResumed(mockActivity)

        // then
        verifyZeroInteractions(mockAndroidSupportFragmentManager)
        verifyZeroInteractions(mockDefaultFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `when base activity paused API below O will do nothing`() {
        // when
        underTest.onActivityPaused(mockActivity)

        // then
        verifyZeroInteractions(mockAndroidSupportFragmentManager)
        verifyZeroInteractions(mockDefaultFragmentManager)
    }

    // endregion
}
