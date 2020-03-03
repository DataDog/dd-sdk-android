package com.datadog.android.rum.internal.instrumentation

import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ActivityTrackingStrategyTest : TrackingStrategyTest() {

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
