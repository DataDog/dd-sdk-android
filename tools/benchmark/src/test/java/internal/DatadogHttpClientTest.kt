/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package internal

import com.datadog.benchmark.DatadogExporterConfiguration
import com.datadog.benchmark.internal.DatadogHttpClient
import com.datadog.benchmark.internal.MetricRequestBodyBuilder
import com.datadog.benchmark.internal.SpanRequestBodyBuilder
import com.datadog.benchmark.internal.model.BenchmarkContext
import com.datadog.benchmark.internal.model.SpanEvent
import forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentelemetry.sdk.metrics.data.MetricData
import okhttp3.Call
import okhttp3.Request
import okhttp3.RequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
class DatadogHttpClientTest {

    @Mock
    private lateinit var mockedCallFactory: Call.Factory

    @Mock
    private lateinit var mockedMetricRequestBodyBuilder: MetricRequestBodyBuilder

    @Mock
    private lateinit var mockedSpanRequestBodyBuilder: SpanRequestBodyBuilder

    @Forgery
    private lateinit var fakeBenchmarkContext: BenchmarkContext

    @Forgery
    private lateinit var fakeExporterConfiguration: DatadogExporterConfiguration

    private lateinit var datadogHttpClient: DatadogHttpClient

    @StringForgery
    private lateinit var fakeMetricJson: String

    @StringForgery
    private lateinit var fakeSpanJson: String

    @Forgery
    private lateinit var metricsData: List<MetricData>

    @Forgery
    private lateinit var spanEvents: List<SpanEvent>

    private val contentTypeJson = "application/json; charset=utf-8"
    private val contentTypePlainText = "text/plain;charset=UTF-8"

    @BeforeEach
    fun `set up`() {
        datadogHttpClient = DatadogHttpClient(
            context = fakeBenchmarkContext,
            exporterConfiguration = fakeExporterConfiguration,
            callFactory = mockedCallFactory,
            metricRequestBodyBuilder = mockedMetricRequestBodyBuilder,
            spanRequestBuilder = mockedSpanRequestBodyBuilder
        )
        whenever(mockedMetricRequestBodyBuilder.build(metricsData)).thenReturn(fakeMetricJson)
        whenever(mockedSpanRequestBodyBuilder.build(any())).thenReturn(fakeSpanJson)
    }

    @Test
    fun `M send correct payload W uploadMetric()`() {
        // Given

        // When
        datadogHttpClient.uploadMetric(
            metricsData
        )

        // Then
        argumentCaptor<Request> {
            verify(mockedCallFactory, times(1)).newCall(capture())

            verifyRequestBody(
                firstValue.body,
                fakeMetricJson,
                contentTypeJson
            )
        }
    }

    @Test
    fun `M send correct payload W uploadSpanEvent()`() {
        // Given

        // When
        datadogHttpClient.uploadSpanEvent(
            spanEvents
        )

        // Then
        argumentCaptor<Request> {
            verify(mockedCallFactory, times(1)).newCall(capture())

            verifyRequestBody(
                firstValue.body,
                fakeSpanJson,
                contentTypePlainText
            )
        }
    }

    private fun verifyRequestBody(
        body: RequestBody?,
        expectedBody: String,
        contentType: String?
    ) {
        checkNotNull(body)
        if (contentType == null) {
            assertThat(body.contentType()).isNull()
        } else {
            assertThat(body.contentType().toString()).isEqualTo(contentType)
        }
        assertThat(body.contentLength()).isEqualTo(expectedBody.length.toLong())
    }
}
