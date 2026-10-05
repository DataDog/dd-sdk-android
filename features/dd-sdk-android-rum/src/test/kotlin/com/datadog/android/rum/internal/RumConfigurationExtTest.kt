/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import com.datadog.android.core.internal.remote.model.RemoteConfiguration
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.configuration.SlowFramesConfiguration
import com.datadog.android.rum.configuration.VitalsUpdateFrequency
import com.datadog.android.rum.internal.instrumentation.MainLooperLongTaskStrategy
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
internal class RumConfigurationExtTest {

    private lateinit var fakeApplicationId: String
    private lateinit var testedRumConfiguration: RumConfiguration

    @BeforeEach
    fun setUp(forge: Forge) {
        fakeApplicationId = forge.aStringMatching("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        testedRumConfiguration = RumConfiguration.Builder(fakeApplicationId).build()
    }

    // region null RC

    @Test
    fun `M return config unchanged W applyRemoteConfiguration { null RC }`() {
        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(null)

        // Then
        assertThat(result).isEqualTo(testedRumConfiguration)
    }

    @Test
    fun `M return config unchanged W applyRemoteConfiguration { RC with null rum }`() {
        // Given
        val fakeRc = RemoteConfiguration(rum = null)

        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result).isEqualTo(testedRumConfiguration)
    }

    // endregion

    // region telemetrySampleRate

    @Test
    fun `M override telemetrySampleRate W applyRemoteConfiguration { RC provides telemetrySampleRate }`(
        @FloatForgery(min = 0f, max = 100f) fakeSampleRate: Float,
        @StringForgery fakeAppId: String
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                telemetrySampleRate = fakeSampleRate
            )
        )

        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.telemetrySampleRate).isEqualTo(fakeSampleRate)
    }

    @Test
    fun `M keep existing telemetrySampleRate W applyRemoteConfiguration { RC omits telemetrySampleRate }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(applicationId = fakeAppId)
        )
        val expectedRate = testedRumConfiguration.featureConfiguration.telemetrySampleRate

        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.telemetrySampleRate).isEqualTo(expectedRate)
    }

    // endregion

    // region trackAnonymousUser

    @Test
    fun `M override trackAnonymousUser W applyRemoteConfiguration { RC provides trackAnonymousUser=true }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val baseline = RumConfiguration.Builder(fakeApplicationId)
            .trackAnonymousUser(false)
            .build()
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                trackAnonymousUser = true
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.trackAnonymousUser).isTrue()
    }

    @Test
    fun `M override trackAnonymousUser W applyRemoteConfiguration { RC provides trackAnonymousUser=false }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val baseline = RumConfiguration.Builder(fakeApplicationId)
            .trackAnonymousUser(true)
            .build()
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                trackAnonymousUser = false
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.trackAnonymousUser).isFalse()
    }

    @Test
    fun `M keep existing trackAnonymousUser W applyRemoteConfiguration { RC omits trackAnonymousUser }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(applicationId = fakeAppId)
        )
        val expectedValue = testedRumConfiguration.featureConfiguration.trackAnonymousUser

        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.trackAnonymousUser).isEqualTo(expectedValue)
    }

    // endregion

    // region trackUserInteractions

    @Test
    fun `M override userActionTracking W applyRemoteConfiguration { RC provides trackUserInteractions=false }`(
        @StringForgery fakeAppId: String
    ) {
        // Given - default builder has userActionTracking=true
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                trackUserInteractions = false
            )
        )

        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.userActionTracking).isFalse()
    }

    @Test
    fun `M override userActionTracking W applyRemoteConfiguration { RC provides trackUserInteractions=true }`(
        @StringForgery fakeAppId: String
    ) {
        // Given - developer disabled user interaction tracking
        val baseline = RumConfiguration.Builder(fakeApplicationId)
            .disableUserInteractionTracking()
            .build()
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                trackUserInteractions = true
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.userActionTracking).isTrue()
    }

    @Test
    fun `M keep existing userActionTracking W applyRemoteConfiguration { RC omits trackUserInteractions }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(applicationId = fakeAppId)
        )
        val expectedValue = testedRumConfiguration.featureConfiguration.userActionTracking

        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.userActionTracking).isEqualTo(expectedValue)
    }

    // endregion

    // region trackBackgroundEvents

    @Test
    fun `M override backgroundEventTracking W applyRemoteConfiguration { RC provides trackBackgroundEvents=true }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val baseline = RumConfiguration.Builder(fakeApplicationId)
            .trackBackgroundEvents(false)
            .build()
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                trackBackgroundEvents = true
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.backgroundEventTracking).isTrue()
    }

    @Test
    fun `M override backgroundEventTracking W applyRemoteConfiguration { RC provides trackBackgroundEvents=false }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val baseline = RumConfiguration.Builder(fakeApplicationId)
            .trackBackgroundEvents(true)
            .build()
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                trackBackgroundEvents = false
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.backgroundEventTracking).isFalse()
    }

    // endregion

    // region trackFrustrations

    @Test
    fun `M override trackFrustrations W applyRemoteConfiguration { RC provides trackFrustrations=false }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val baseline = RumConfiguration.Builder(fakeApplicationId)
            .trackFrustrations(true)
            .build()
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                trackFrustrations = false
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.trackFrustrations).isFalse()
    }

    @Test
    fun `M override trackFrustrations W applyRemoteConfiguration { RC provides trackFrustrations=true }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val baseline = RumConfiguration.Builder(fakeApplicationId)
            .trackFrustrations(false)
            .build()
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                trackFrustrations = true
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.trackFrustrations).isTrue()
    }

    // endregion

    // region trackNonFatalAnrs

    @Test
    fun `M override trackNonFatalAnrs W applyRemoteConfiguration { RC provides trackNonFatalAnrs=true }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val baseline = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .build()
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                trackNonFatalAnrs = true
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.trackNonFatalAnrs).isTrue()
    }

    @Test
    fun `M override trackNonFatalAnrs W applyRemoteConfiguration { RC provides trackNonFatalAnrs=false }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val baseline = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(true)
            .build()
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                trackNonFatalAnrs = false
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.trackNonFatalAnrs).isFalse()
    }

    // endregion

    // region vitalsUpdateFrequency

    @Test
    fun `M override vitalsMonitorUpdateFrequency W applyRemoteConfiguration { RC vitalsUpdateFrequency FREQUENT }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                vitalsUpdateFrequency = RemoteConfiguration.VitalsUpdateFrequency.FREQUENT
            )
        )

        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.vitalsMonitorUpdateFrequency)
            .isEqualTo(VitalsUpdateFrequency.FREQUENT)
    }

    @Test
    fun `M override vitalsMonitorUpdateFrequency W applyRemoteConfiguration { RC vitalsUpdateFrequency AVERAGE }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                vitalsUpdateFrequency = RemoteConfiguration.VitalsUpdateFrequency.AVERAGE
            )
        )

        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.vitalsMonitorUpdateFrequency)
            .isEqualTo(VitalsUpdateFrequency.AVERAGE)
    }

    @Test
    fun `M override vitalsMonitorUpdateFrequency W applyRemoteConfiguration { RC provides vitalsUpdateFrequency RARE }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                vitalsUpdateFrequency = RemoteConfiguration.VitalsUpdateFrequency.RARE
            )
        )

        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.vitalsMonitorUpdateFrequency)
            .isEqualTo(VitalsUpdateFrequency.RARE)
    }

    @Test
    fun `M override vitalsMonitorUpdateFrequency W applyRemoteConfiguration { RC vitalsUpdateFrequency NEVER }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                vitalsUpdateFrequency = RemoteConfiguration.VitalsUpdateFrequency.NEVER
            )
        )

        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.vitalsMonitorUpdateFrequency)
            .isEqualTo(VitalsUpdateFrequency.NEVER)
    }

    @Test
    fun `M keep existing vitalsMonitorUpdateFrequency W applyRemoteConfiguration { RC omits vitalsUpdateFrequency }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(applicationId = fakeAppId)
        )
        val expectedValue = testedRumConfiguration.featureConfiguration.vitalsMonitorUpdateFrequency

        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.vitalsMonitorUpdateFrequency).isEqualTo(expectedValue)
    }

    // endregion

    // region trackSlowFrames

    @Test
    fun `M set slowFramesConfiguration to DEFAULT W applyRemoteConfiguration { trackSlowFrames=true, null dev config }`(
        @StringForgery fakeAppId: String
    ) {
        // Given - developer explicitly disabled slow frames (null)
        val baseline = RumConfiguration.Builder(fakeApplicationId)
            .setSlowFramesConfiguration(null)
            .build()
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                trackSlowFrames = true
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.slowFramesConfiguration)
            .isEqualTo(SlowFramesConfiguration.DEFAULT)
    }

    @Test
    fun `M preserve developer's slowFramesConfiguration W applyRemoteConfiguration { trackSlowFrames=true }`(
        @StringForgery fakeAppId: String,
        @LongForgery(min = 1L) fakeThresholdNs: Long
    ) {
        // Given - developer set a custom SlowFramesConfiguration
        val fakeCustomConfig = SlowFramesConfiguration(maxSlowFrameThresholdNs = fakeThresholdNs)
        val baseline = RumConfiguration.Builder(fakeApplicationId)
            .setSlowFramesConfiguration(fakeCustomConfig)
            .build()
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                trackSlowFrames = true
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then - developer's custom config is preserved
        assertThat(result.featureConfiguration.slowFramesConfiguration)
            .isEqualTo(fakeCustomConfig)
    }

    @Test
    fun `M set slowFramesConfiguration to null W applyRemoteConfiguration { RC trackSlowFrames=false }`(
        @StringForgery fakeAppId: String
    ) {
        // Given - developer had it enabled
        val baseline = RumConfiguration.Builder(fakeApplicationId)
            .setSlowFramesConfiguration(SlowFramesConfiguration.DEFAULT)
            .build()
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                trackSlowFrames = false
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.slowFramesConfiguration).isNull()
    }

    @Test
    fun `M keep existing slowFramesConfiguration W applyRemoteConfiguration { RC omits trackSlowFrames }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(applicationId = fakeAppId)
        )
        val expectedValue = testedRumConfiguration.featureConfiguration.slowFramesConfiguration

        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.slowFramesConfiguration).isEqualTo(expectedValue)
    }

    // endregion

    // region longTask

    @Test
    fun `M disable longTaskTrackingStrategy W applyRemoteConfiguration { RC longTask enabled=false }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                longTask = RemoteConfiguration.LongTask(enabled = false)
            )
        )

        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.longTaskTrackingStrategy).isNull()
    }

    @Test
    fun `M enable longTaskTrackingStrategy with default threshold W applyRemoteConfiguration { longTask=true }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                longTask = RemoteConfiguration.LongTask(enabled = true, threshold = null)
            )
        )

        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        val strategy = result.featureConfiguration.longTaskTrackingStrategy
        assertThat(strategy).isInstanceOf(MainLooperLongTaskStrategy::class.java)
        assertThat((strategy as MainLooperLongTaskStrategy).thresholdMs)
            .isEqualTo(RumFeature.DEFAULT_LONG_TASK_THRESHOLD_MS)
    }

    @Test
    fun `M enable longTaskTrackingStrategy with RC threshold W applyRemoteConfiguration { longTask=true }`(
        @StringForgery fakeAppId: String,
        @LongForgery(min = 1L, max = 10000L) fakeThresholdMs: Long
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                longTask = RemoteConfiguration.LongTask(enabled = true, threshold = fakeThresholdMs)
            )
        )

        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        val strategy = result.featureConfiguration.longTaskTrackingStrategy
        assertThat(strategy).isInstanceOf(MainLooperLongTaskStrategy::class.java)
        assertThat((strategy as MainLooperLongTaskStrategy).thresholdMs).isEqualTo(fakeThresholdMs)
    }

    @Test
    fun `M preserve developer's threshold W applyRemoteConfiguration { longTask=true, no RC threshold }`(
        @StringForgery fakeAppId: String,
        @LongForgery(min = 101L, max = 10000L) fakeCustomThresholdMs: Long
    ) {
        // Given - developer set a custom long task threshold
        val baseline = RumConfiguration.Builder(fakeApplicationId)
            .trackLongTasks(fakeCustomThresholdMs)
            .build()
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(
                applicationId = fakeAppId,
                longTask = RemoteConfiguration.LongTask(enabled = true, threshold = null)
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then - developer's custom threshold is preserved
        val strategy = result.featureConfiguration.longTaskTrackingStrategy
        assertThat(strategy).isInstanceOf(MainLooperLongTaskStrategy::class.java)
        assertThat((strategy as MainLooperLongTaskStrategy).thresholdMs).isEqualTo(fakeCustomThresholdMs)
    }

    @Test
    fun `M keep existing longTaskTrackingStrategy W applyRemoteConfiguration { RC omits longTask }`(
        @StringForgery fakeAppId: String
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            rum = RemoteConfiguration.Rum(applicationId = fakeAppId)
        )
        val expectedStrategy = testedRumConfiguration.featureConfiguration.longTaskTrackingStrategy

        // When
        val result = testedRumConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.featureConfiguration.longTaskTrackingStrategy).isEqualTo(expectedStrategy)
    }

    // endregion
}
