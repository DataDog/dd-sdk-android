/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
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
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.lang.ref.WeakReference

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumAppStartupTelemetryReporterTest {
    private lateinit var internalLogger: FakeInternalLogger

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

        reporter = RumAppStartupTelemetryReporterImpl(
            internalLogger = internalLogger,
            appStartupTimeNs = appStartupTimeNanos,
            contentProviderCreationTimeNs = contentProviderCreationTimeNanos,
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
            durationNs = forge.aLong(min = 0, max = 10000)
        )

        // When
        reporter.reportTTID(info, appStartIndex)

        // Then
        val (message, propertiesMap) = internalLogger.lastMetric!!

        @Suppress("UNCHECKED_CAST")
        val innerMap: Map<String, Any?> =
            propertiesMap["app_launch_ttid"] as Map<String, Any?>

        assertThat(message).isEqualTo("[Mobile Metric] App Launch TTID")
        assertThat(innerMap).containsExactlyEntriesOf(
            buildMap {
                put("scenario", info.scenario.name)
                put("duration_ns", info.durationNs)
                put("index_in_session", appStartIndex)
                put("cp_process_start_diff_ns", contentProviderCreationTimeNanos - appStartupTimeNanos)
                put("process_start_importance", processStartImportance)
                put("has_saved_instance_state", info.scenario.hasSavedInstanceStateBundle)

                info.scenario.appStartActivityOnCreateGapNs?.let {
                    put("app_start_activity_on_create_gap_ns", it)
                }
            }
        )

        assertThat(propertiesMap["metric_type"])
            .isEqualTo("app launch ttid")

        assertThat(propertiesMap).containsOnlyKeys(
            "app_launch_ttid",
            "metric_type"
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
