/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.metrics

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.DeviceInfo
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.metrics.MethodCalledTelemetry.Companion.CALLER_CLASS
import com.datadog.android.core.internal.metrics.MethodCalledTelemetry.Companion.EXECUTION_TIME
import com.datadog.android.core.internal.metrics.MethodCalledTelemetry.Companion.IS_SUCCESSFUL
import com.datadog.android.core.internal.metrics.MethodCalledTelemetry.Companion.METHOD_CALLED_METRIC_NAME
import com.datadog.android.core.internal.metrics.MethodCalledTelemetry.Companion.METRIC_TYPE_VALUE
import com.datadog.android.core.internal.metrics.MethodCalledTelemetry.Companion.OPERATION_NAME
import com.datadog.android.core.metrics.PerformanceMetric.Companion.METRIC_TYPE
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class MethodCalledTelemetryTest {
    private lateinit var testedMethodCalledTelemetry: MethodCalledTelemetry

    @StringForgery
    private lateinit var fakeCallerClass: String

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockInternalSdkCore: InternalSdkCore

    @Mock
    lateinit var mockDatadogContext: DatadogContext

    @Mock
    lateinit var mockDeviceInfo: DeviceInfo

    @StringForgery
    lateinit var fakeDeviceModel: String

    @StringForgery
    lateinit var fakeDeviceBrand: String

    @StringForgery
    lateinit var fakeOsName: String

    @StringForgery
    lateinit var fakeOsVersion: String

    @StringForgery
    lateinit var fakeOsBuild: String

    @StringForgery
    lateinit var fakeDeviceArchitecture: String

    @StringForgery
    lateinit var fakeOperationName: String

    private var fakeStartTime: Long = 0
    private var fakeStatus: Boolean = false

    private val lambdaCaptor = argumentCaptor<() -> String>()
    private val mapCaptor = argumentCaptor<Map<String, Any>>()

    @BeforeEach
    fun setup(forge: Forge) {
        fakeStatus = forge.aBool()

        whenever(mockInternalSdkCore.getDatadogContext()).thenReturn(mockDatadogContext)
        whenever(mockDatadogContext.deviceInfo).thenReturn(mockDeviceInfo)
        whenever(mockDeviceInfo.deviceModel).thenReturn(fakeDeviceModel)
        whenever(mockDeviceInfo.deviceBrand).thenReturn(fakeDeviceBrand)
        whenever(mockDeviceInfo.architecture).thenReturn(fakeDeviceArchitecture)
        whenever(mockDeviceInfo.osName).thenReturn(fakeOsName)
        whenever(mockDeviceInfo.osVersion).thenReturn(fakeOsVersion)
        whenever(mockDeviceInfo.deviceBuildId).thenReturn(fakeOsBuild)

        fakeStartTime = System.nanoTime()
        testedMethodCalledTelemetry = MethodCalledTelemetry(
            internalLogger = mockInternalLogger,
            operationName = fakeOperationName,
            callerClass = fakeCallerClass,
            startTime = fakeStartTime
        )
    }

    @Test
    fun `M call logger with correct title W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.stopAndSend(false)

        // Then
        verify(mockInternalLogger).logMetric(lambdaCaptor.capture(), any(), eq(100.0f))
        lambdaCaptor.firstValue.run {
            val title = this()
            assertThat(title).isEqualTo(METHOD_CALLED_METRIC_NAME)
        }
    }

    @Test
    fun `M call logger with correct execution time W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.stopAndSend(false)

        // Then
        verify(mockInternalLogger).logMetric(any(), mapCaptor.capture(), eq(100.0f))
        val executionTime = mapCaptor.firstValue[EXECUTION_TIME] as Long

        assertThat(executionTime).isLessThan(System.nanoTime() - fakeStartTime)
    }

    @Test
    fun `M call logger with correct operation name W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.stopAndSend(false)

        // Then
        verify(mockInternalLogger).logMetric(any(), mapCaptor.capture(), eq(100.0f))
        val operationName = mapCaptor.firstValue[OPERATION_NAME] as String

        assertThat(operationName).isEqualTo(fakeOperationName)
    }

    @Test
    fun `M call logger with correct caller class W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.stopAndSend(false)

        // Then
        verify(mockInternalLogger).logMetric(any(), mapCaptor.capture(), eq(100.0f))
        val callerClass = mapCaptor.firstValue[CALLER_CLASS] as String

        assertThat(callerClass).isEqualTo(fakeCallerClass)
    }

    @Test
    fun `M call logger with correct isSuccessful value W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.stopAndSend(fakeStatus)

        // Then
        verify(mockInternalLogger).logMetric(any(), mapCaptor.capture(), eq(100.0f))
        val isSuccessful = mapCaptor.firstValue[IS_SUCCESSFUL] as Boolean

        assertThat(isSuccessful).isEqualTo(fakeStatus)
    }

    @Test
    fun `M call logger with correct metric type value W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.stopAndSend(fakeStatus)

        // Then
        verify(mockInternalLogger).logMetric(any(), mapCaptor.capture(), eq(100.0f))
        val metricTypeValue = mapCaptor.firstValue[METRIC_TYPE] as String

        assertThat(metricTypeValue).isEqualTo(METRIC_TYPE_VALUE)
    }
}
