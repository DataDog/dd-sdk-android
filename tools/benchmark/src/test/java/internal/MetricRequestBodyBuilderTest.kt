/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package internal

import com.datadog.tools.unit.extensions.ProhibitLeavingStaticMocksExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.google.gson.JsonParser
import forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.data.Data
import io.opentelemetry.sdk.metrics.data.DoublePointData
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.MetricDataType
import io.opentelemetry.sdk.metrics.data.PointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableDoublePointData
import io.opentelemetry.sdk.resources.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
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
    private lateinit var benchmarkContext: com.datadog.benchmark.internal.model.BenchmarkContext

    @BeforeEach
    fun `set up`() {
        metricRequestBodyBuilder = com.datadog.benchmark.internal.MetricRequestBodyBuilder(benchmarkContext)
    }

    @Test
    fun `M create a proper request W buildJsonElement()`(@Forgery metricData: List<MetricData>) {
        val json = metricRequestBodyBuilder.build(
            metricData
        )

        MetricRequestAssert
            .assertThat(JsonParser.parseString(json).asJsonObject)
            .hasMetricDataArray("series", metricData.size) { metricIndex ->
                hasMetric(metricData[metricIndex].name)
                hasPoints(metricData[metricIndex].data.points.size) { pointIndex ->
                    val point = metricData[metricIndex].data.points.toList()[pointIndex]
                    hasTimestamp(TimeUnit.NANOSECONDS.toSeconds(point.startEpochNanos))
                    hasValue(resolveValue(point))
                }
                hasTags(
                    listOf(
                        "device_model:${benchmarkContext.deviceModel}",
                        "os_version:${benchmarkContext.osVersion}",
                        "run:${benchmarkContext.run}",
                        "scenario:${benchmarkContext.scenario}",
                        "application_id:${benchmarkContext.applicationId}",
                        "env:${benchmarkContext.env}"
                    )
                )
                hasMetricType(resolveMetricType(metricData[metricIndex].type).value)
                hasUnit(metricData[metricIndex].unit)
            }
    }

    // region sdk benchmark

    @Test
    fun `M resolve track tag W build { has track data }`(
        forge: Forge
    ) {
        // Given
        val fakeTrackName = forge.aString()
        val mockMetricData = createMockMetricData(forge, fakeTrackName)

        // When
        val listMetricData = listOf(mockMetricData)
        val json = metricRequestBodyBuilder.build(
            listMetricData
        )

        // Then
        MetricRequestAssert
            .assertThat(JsonParser.parseString(json).asJsonObject)
            .hasMetricDataArray("series", listMetricData.size) { metricIndex ->
                hasMetric(listMetricData[metricIndex].name)
                hasPoints(listMetricData[metricIndex].data.points.size) { pointIndex ->
                    val point = listMetricData[metricIndex].data.points.toList()[pointIndex]
                    hasTimestamp(TimeUnit.NANOSECONDS.toSeconds(point.startEpochNanos))
                    hasValue(resolveValue(point))
                }
                hasTags(
                    listOf(
                        "device_model:${benchmarkContext.deviceModel}",
                        "os_version:${benchmarkContext.osVersion}",
                        "run:${benchmarkContext.run}",
                        "scenario:${benchmarkContext.scenario}",
                        "application_id:${benchmarkContext.applicationId}",
                        "env:${benchmarkContext.env}",
                        "track:$fakeTrackName"
                    )
                )
                hasMetricType(resolveMetricType(listMetricData[metricIndex].type).value)
                hasUnit(listMetricData[metricIndex].unit)
            }
    }

    @Test
    fun `M not resolve track tag W build { no track data }`(
        forge: Forge
    ) {
        // Given
        val mockMetricData = createMockMetricData(forge, null)

        // When
        val listMetricData = listOf(mockMetricData)
        val json = metricRequestBodyBuilder.build(
            listMetricData
        )

        // Then
        MetricRequestAssert
            .assertThat(JsonParser.parseString(json).asJsonObject)
            .hasMetricDataArray("series", listMetricData.size) { metricIndex ->
                hasMetric(listMetricData[metricIndex].name)
                hasPoints(listMetricData[metricIndex].data.points.size) { pointIndex ->
                    val point = listMetricData[metricIndex].data.points.toList()[pointIndex]
                    hasTimestamp(TimeUnit.NANOSECONDS.toSeconds(point.startEpochNanos))
                    hasValue(resolveValue(point))
                }
                hasTags(
                    listOf(
                        "device_model:${benchmarkContext.deviceModel}",
                        "os_version:${benchmarkContext.osVersion}",
                        "run:${benchmarkContext.run}",
                        "scenario:${benchmarkContext.scenario}",
                        "application_id:${benchmarkContext.applicationId}",
                        "env:${benchmarkContext.env}"
                    )
                )
                hasMetricType(resolveMetricType(listMetricData[metricIndex].type).value)
                hasUnit(listMetricData[metricIndex].unit)
            }
    }

    // endregion

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

    private fun createMockMetricData(forge: Forge, fakeTrackName: String?): MetricData {
        val trackAttribute = if (fakeTrackName != null) {
            Attributes.empty().toBuilder().put("track", fakeTrackName).build()
        } else {
            Attributes.empty()
        }

        val mockMetricData: MetricData = mock()
        val mockData: Data<PointData> = mock()
        val mockResourceAttributes: Attributes = mock()
        val mockResource: Resource = mock()
        whenever(mockMetricData.type).thenReturn(mock())
        whenever(mockMetricData.name).thenReturn(forge.aString())
        whenever(mockMetricData.unit).thenReturn(forge.aString())
        whenever(mockResource.attributes).thenReturn(mockResourceAttributes)
        whenever(mockMetricData.data).thenReturn(mockData)
        whenever(mockMetricData.resource).thenReturn(mockResource)
        whenever(mockMetricData.data.points).thenReturn(
            listOf(
                ImmutableDoublePointData.create(
                    forge.aLong(),
                    forge.aLong(),
                    trackAttribute,
                    forge.aDouble()
                )
            )
        )

        return mockMetricData
    }
}
