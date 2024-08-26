/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package internal

import com.datadog.tools.unit.extensions.ProhibitLeavingStaticMocksExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentelemetry.sdk.metrics.data.DoublePointData
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.MetricDataType
import io.opentelemetry.sdk.metrics.data.PointData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ProhibitLeavingStaticMocksExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
class MetricRequestBodyBuilderTest {

    private lateinit var metricRequestBodyBuilder: com.datadog.benchmark.internal.MetricRequestBodyBuilder

    @Forgery
    private lateinit var metricContext: com.datadog.benchmark.internal.model.MetricContext

    @BeforeEach
    fun `set up`() {
        metricRequestBodyBuilder = com.datadog.benchmark.internal.MetricRequestBodyBuilder(metricContext)
    }

    @Test
    fun `M create a proper request W buildJsonElement()`(@Forgery metricData: List<MetricData>) {
        val jsonElement = metricRequestBodyBuilder.buildJsonElement(
            metricData
        )

        MetricRequestAssert
            .assertThat(jsonElement.asJsonObject)
            .hasMetricDataArray("series", metricData.size) { metricIndex ->
                hasMetric(metricData[metricIndex].name)
                hasPoints(metricData[metricIndex].data.points.size) { pointIndex ->
                    val point = metricData[metricIndex].data.points.toList()[pointIndex]
                    hasTimestamp(TimeUnit.NANOSECONDS.toSeconds(point.startEpochNanos))
                    hasValue(resolveValue(point))
                }
                hasTags(
                    listOf(
                        "device_model:${metricContext.deviceModel}",
                        "os_version:${metricContext.osVersion}",
                        "run:${metricContext.run}",
                        "scenario:${metricContext.scenario}",
                        "application_id:${metricContext.applicationId}"
                    )
                )
                hasMetricType(resolveMetricType(metricData[metricIndex].type).value)
                hasUnit(metricData[metricIndex].unit)
            }
    }

    private fun resolveMetricType(type: MetricDataType): com.datadog.benchmark.internal.model.MetricType {
        return when (type) {
            MetricDataType.LONG_GAUGE, MetricDataType.DOUBLE_GAUGE ->
                com.datadog.benchmark.internal.model.MetricType.GAUGE

            MetricDataType.LONG_SUM, MetricDataType.DOUBLE_SUM ->
                com.datadog.benchmark.internal.model.MetricType.COUNT
            else -> com.datadog.benchmark.internal.model.MetricType.UNSPECIFIED
        }
    }

    private fun resolveValue(pointData: PointData): Double {
        return when (pointData) {
            is DoublePointData -> pointData.value
            is LongPointData -> pointData.value.toDouble()
            else -> 0.0
        }
    }
}
