package com.datadog.android.rum.internal.instrumentation

import android.app.Activity
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.monitor.NoOpRumMonitor
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
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
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal open class TrackingStrategiesTest {
    lateinit var underTest: TrackingStrategy
    @Mock
    lateinit var mockRumMonitor: RumMonitor
    lateinit var mockActivity: Activity

    @BeforeEach
    open fun `set up`(forge: Forge) {
        GlobalRum.registerIfAbsent(mockRumMonitor)
        val mockActivity1 = mock<Test1Activity>()
        val mockActivity2 = mock<Test2Activity>()
        val mockActivity3 = mock<Test3Activity>()
        mockActivity = forge.anElementFrom(mockActivity1, mockActivity2, mockActivity3)
        underTest = TrackingStrategy.ActivityTrackingStrategy
    }

    @AfterEach
    open fun `tear down`() {
        GlobalRum.monitor = NoOpRumMonitor()
        GlobalRum.isRegistered.set(false)
    }

    internal class Test1Activity() : Activity()
    internal class Test2Activity() : Activity()
    internal class Test3Activity() : Activity()
}

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ActivityTrackingStrategyTest : TrackingStrategiesTest() {

    @Test
    fun `when resumed it will start a view event`(forge: Forge) {
        // when
        underTest.onActivityResumed(mockActivity)
        // then
        verify(mockRumMonitor).startView(
            eq(mockActivity),
            eq(mockActivity::class.java.canonicalName!!),
            eq(emptyMap())
        )
    }

    @Test
    fun `when paused it will stop a view event`(forge: Forge) {
        // when
        underTest.onActivityPaused(mockActivity)
        // then
        verify(mockRumMonitor).stopView(
            eq(mockActivity),
            eq(emptyMap())
        )
    }
}
