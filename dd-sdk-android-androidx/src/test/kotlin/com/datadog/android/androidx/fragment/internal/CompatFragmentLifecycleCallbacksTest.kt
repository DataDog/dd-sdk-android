package com.datadog.android.androidx.fragment.internal

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.datadog.android.androidx.fragment.internal.utils.mockRumMonitor
import com.datadog.android.androidx.fragment.internal.utils.resetRumMonitorToDefaults
import com.datadog.android.rum.RumMonitor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
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
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CompatFragmentLifecycleCallbacksTest {
    lateinit var underTest: CompatFragmentLifecycleCallbacks

    @Mock
    lateinit var mockFragment: TestFragment1

    @Mock
    lateinit var mockFragmentActivity: FragmentActivity

    @Mock
    lateinit var mockFragmentManager: FragmentManager

    lateinit var attributesMap: Map<String, Any?>

    lateinit var mockRumMonitor: RumMonitor

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockRumMonitor = mockRumMonitor()
        whenever(mockFragmentActivity.supportFragmentManager).thenReturn(mockFragmentManager)
        attributesMap = forge.aMap { forge.aString() to forge.aString() }
        underTest = CompatFragmentLifecycleCallbacks { attributesMap }
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
    fun `will register the callback to fragment manager when required`() {
        // when
        underTest.register(mockFragmentActivity)

        // then
        verify(mockFragmentManager).registerFragmentLifecycleCallbacks(underTest, true)
    }

    @Test
    fun `will unregister the callback from the fragment manager when required`() {
        // when
        underTest.unregister(mockFragmentActivity)

        // then
        verify(mockFragmentManager).unregisterFragmentLifecycleCallbacks(underTest)
    }

    internal open class TestFragment1 : Fragment()
}
