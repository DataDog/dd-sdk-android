package com.datadog.android.androidx.fragment.internal

import android.app.Activity
import android.app.Fragment
import android.app.FragmentManager
import android.os.Build
import com.datadog.android.androidx.fragment.internal.utils.mockRumMonitor
import com.datadog.android.androidx.fragment.internal.utils.resetRumMonitorToDefaults
import com.datadog.android.rum.RumMonitor
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
internal class OreoFragmentLifecycleCallbacksTest {
    lateinit var underTest: OreoFragmentLifecycleCallbacks

    @Mock
    lateinit var mockFragment: TestFragment1

    @Mock
    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockFragmentManager: FragmentManager

    lateinit var attributesMap: Map<String, Any?>

    lateinit var mockRumMonitor: RumMonitor

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockRumMonitor = mockRumMonitor()
        whenever(mockActivity.fragmentManager).thenReturn(mockFragmentManager)
        attributesMap = forge.aMap { forge.aString() to forge.aString() }
        underTest = OreoFragmentLifecycleCallbacks { attributesMap }
    }

    @AfterEach
    fun `tear down`() {
        resetRumMonitorToDefaults()
    }

    @Test
    fun `when fragment resumed it will start a view event`(forge: Forge) {
        // when
        underTest.onFragmentResumed(mock(), mockFragment)
        // then
        verify(mockRumMonitor).startView(
            eq(mockFragment),
            eq(mockFragment.resolveViewName()),
            eq(attributesMap)
        )
    }

    @Test
    fun `when fragment paused it will stop a view event`(forge: Forge) {
        // when
        underTest.onFragmentPaused(mock(), mockFragment)
        // then
        verify(mockRumMonitor).stopView(
            eq(mockFragment),
            eq(emptyMap())
        )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `it will register the callback to fragment manager on O`() {
        // when
        underTest.register(mockActivity)

        // then
        verify(mockFragmentManager).registerFragmentLifecycleCallbacks(underTest, true)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `it will unregister the callback from fragment manager on O`() {
        // when
        underTest.unregister(mockActivity)

        // then
        verify(mockFragmentManager).unregisterFragmentLifecycleCallbacks(underTest)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `it will do nothing when calling register on M`() {
        // when
        underTest.register(mockActivity)

        // then
        verifyZeroInteractions(mockFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `it will do nothing when calling unregister on M`() {
        // when
        underTest.unregister(mockActivity)

        // then
        verifyZeroInteractions(mockFragmentManager)
    }

    internal open class TestFragment1 : Fragment()
}
