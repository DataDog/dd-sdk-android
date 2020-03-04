package com.datadog.android.rum.internal.instrumentation

import com.datadog.android.rum.ActivityLifecycleTrackingStrategyTest
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesTracker
import com.datadog.android.utils.forge.Configurator
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
internal class GesturesTrackingStrategyTest : ActivityLifecycleTrackingStrategyTest() {

    @Mock
    lateinit var mockGesturesTracker: GesturesTracker

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        underTest = GesturesTrackingStrategy(mockGesturesTracker)
    }

    @Test
    fun `when activity resumed it will start tracking gestures`(forge: Forge) {
        // when
        underTest.onActivityResumed(mockActivity)
        // then
        verify(mockGesturesTracker).startTracking(mockActivity)
    }

    @Test
    fun `when activity paused it will stop tracking gestures`(forge: Forge) {
        // when
        underTest.onActivityPaused(mockActivity)
        // then
        verify(mockGesturesTracker).stopTracking(mockActivity)
    }
}
