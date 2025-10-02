/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.internal.metric.FakeInternalLogger
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumAppStartupTelemetryReporterTest {
    private lateinit var internalLogger: FakeInternalLogger

    @Mock
    private lateinit var sdkCore: InternalSdkCore

    @LongForgery
    private var contentProviderCreationTimeNanos: Long = 0

    @IntForgery
    private var processStartImportance: Int = 0

    @LongForgery
    private var appStartupTimeNanos: Long = 0

    private lateinit var reporter: RumAppStartupTelemetryReporterImpl

    @BeforeEach
    fun `set up`() {
        internalLogger = FakeInternalLogger()
        whenever(sdkCore.appStartTimeNs) doReturn appStartupTimeNanos

        reporter = RumAppStartupTelemetryReporterImpl(
            internalLogger = internalLogger,
            sdkCore = sdkCore,
            contentProviderCreationTimeNanos = contentProviderCreationTimeNanos,
            processStartImportance = processStartImportance
        )
    }

    @ParameterizedTest
    @MethodSource("testScenarios")
    fun `M call logMetric W reportTTID`(
        scenario: RumStartupScenario,
        forge: Forge
    ) {
        // Given
        val appStartIndex = forge.anInt(min = 0)

        val info = RumTTIDInfo(
            scenario = scenario,
            duration = forge.anInt(min = 0, max = 10000).milliseconds
        )

        // When
        reporter.reportTTID(info, appStartIndex)

        // Then
        val (message, propertiesMap) = internalLogger.lastMetric!!

        @Suppress("UNCHECKED_CAST")
        val innerMap: Map<String, Any?> =
            propertiesMap[RumAppStartupTelemetryReporterImpl.KEY_APP_LAUNCH_TTID] as Map<String, Any?>

        assertThat(message).isEqualTo(RumAppStartupTelemetryReporterImpl.METRIC_NAME)
        assertThat(innerMap).containsExactlyEntriesOf(
            buildMap {
                put(RumAppStartupTelemetryReporterImpl.KEY_SCENARIO, info.scenario.name)
                put(RumAppStartupTelemetryReporterImpl.KEY_TTID_DURATION, info.duration.inWholeMilliseconds)
                put(RumAppStartupTelemetryReporterImpl.KEY_INDEX_IN_SESSION, appStartIndex)
                put(
                    RumAppStartupTelemetryReporterImpl.KEY_CP_PROCESS_START_DIFF_NS,
                    contentProviderCreationTimeNanos - appStartupTimeNanos
                )
                put(RumAppStartupTelemetryReporterImpl.KEY_PROCESS_START_IMPORTANCE, processStartImportance)
                put(
                    RumAppStartupTelemetryReporterImpl.KEY_HAS_SAVED_INSTANCE_STATE,
                    info.scenario.hasSavedInstanceStateBundle
                )

                info.scenario.gap?.let {
                    put(RumAppStartupTelemetryReporterImpl.KEY_GAP, it.inWholeMilliseconds)
                }
            }
        )

        assertThat(propertiesMap[RumAppStartupTelemetryReporterImpl.KEY_METRIC_TYPE])
            .isEqualTo(RumAppStartupTelemetryReporterImpl.METRIC_TYPE_VALUE)

        assertThat(propertiesMap).containsOnlyKeys(
            RumAppStartupTelemetryReporterImpl.KEY_APP_LAUNCH_TTID,
            RumAppStartupTelemetryReporterImpl.KEY_METRIC_TYPE
        )
    }

    companion object {
        @JvmStatic
        fun testScenarios(): List<RumStartupScenario> {
            val forge = Forge().apply {
                Configurator().configure(this)
            }

            val weakActivity = WeakReference(mock<Activity>())

            return forge.testRumStartupScenarios(weakActivity)
        }
    }
}
