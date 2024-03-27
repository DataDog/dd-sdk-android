/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.telemetry

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.DeviceInfo
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.CALLER_CLASS
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.DEVICE_ARCHITECTURE
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.DEVICE_BRAND
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.DEVICE_KEY
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.DEVICE_MODEL
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.EXECUTION_TIME
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.IS_SUCCESSFUL
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.METHOD_CALLED_METRIC_NAME
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.METHOD_CALL_OPERATION_NAME
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.METRIC_TYPE_VALUE
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.OPERATION_NAME
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.OS_BUILD
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.OS_KEY
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.OS_NAME
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.OS_VERSION
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MetricBase.Companion.METRIC_TYPE
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
            callerClass = fakeCallerClass,
            logger = mockInternalLogger,
            startTime = fakeStartTime,
            internalSdkCore = mockInternalSdkCore
        )
    }

    @Test
    fun `M call logger with correct title W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.sendMetric(false)

        // Then
        verify(mockInternalLogger).logMetric(lambdaCaptor.capture(), any())
        lambdaCaptor.firstValue.run {
            val title = this()
            assertThat(title).isEqualTo(METHOD_CALLED_METRIC_NAME)
        }
    }

    @Test
    fun `M call logger with correct execution time W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.sendMetric(false)

        // Then
        verify(mockInternalLogger).logMetric(any(), mapCaptor.capture())
        val executionTime = mapCaptor.firstValue[EXECUTION_TIME] as Long

        assertThat(executionTime).isLessThan(System.nanoTime() - fakeStartTime)
    }

    @Test
    fun `M call logger with correct operation name W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.sendMetric(false)

        // Then
        verify(mockInternalLogger).logMetric(any(), mapCaptor.capture())
        val operationName = mapCaptor.firstValue[OPERATION_NAME] as String

        assertThat(operationName).isEqualTo(METHOD_CALL_OPERATION_NAME)
    }

    @Test
    fun `M call logger with correct caller class W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.sendMetric(false)

        // Then
        verify(mockInternalLogger).logMetric(any(), mapCaptor.capture())
        val callerClass = mapCaptor.firstValue[CALLER_CLASS] as String

        assertThat(callerClass).isEqualTo(fakeCallerClass)
    }

    @Test
    fun `M call logger with correct isSuccessful value W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.sendMetric(fakeStatus)

        // Then
        verify(mockInternalLogger).logMetric(any(), mapCaptor.capture())
        val isSuccessful = mapCaptor.firstValue[IS_SUCCESSFUL] as Boolean

        assertThat(isSuccessful).isEqualTo(fakeStatus)
    }

    @Test
    fun `M call logger with correct metric type value W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.sendMetric(fakeStatus)

        // Then
        verify(mockInternalLogger).logMetric(any(), mapCaptor.capture())
        val metricTypeValue = mapCaptor.firstValue[METRIC_TYPE] as String

        assertThat(metricTypeValue).isEqualTo(METRIC_TYPE_VALUE)
    }

    @Test
    fun `M call logger with correct device model W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.sendMetric(fakeStatus)

        // Then
        verify(mockInternalLogger).logMetric(any(), mapCaptor.capture())
        val deviceMap = mapCaptor.firstValue[DEVICE_KEY] as Map<*, *>
        val deviceModel = deviceMap[DEVICE_MODEL]

        assertThat(deviceModel).isEqualTo(fakeDeviceModel)
    }

    @Test
    fun `M call logger with correct device brand W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.sendMetric(fakeStatus)

        // Then
        verify(mockInternalLogger).logMetric(any(), mapCaptor.capture())
        val deviceMap = mapCaptor.firstValue[DEVICE_KEY] as Map<*, *>
        val deviceBrand = deviceMap[DEVICE_BRAND]

        assertThat(deviceBrand).isEqualTo(fakeDeviceBrand)
    }

    @Test
    fun `M call logger with correct device architecture W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.sendMetric(fakeStatus)

        // Then
        verify(mockInternalLogger).logMetric(any(), mapCaptor.capture())
        val deviceMap = mapCaptor.firstValue[DEVICE_KEY] as Map<*, *>
        val deviceArchitecture = deviceMap[DEVICE_ARCHITECTURE]

        assertThat(deviceArchitecture).isEqualTo(fakeDeviceArchitecture)
    }

    @Test
    fun `M call logger with correct os name W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.sendMetric(fakeStatus)

        // Then
        verify(mockInternalLogger).logMetric(any(), mapCaptor.capture())
        val deviceMap = mapCaptor.firstValue[OS_KEY] as Map<*, *>
        val osName = deviceMap[OS_NAME]

        assertThat(osName).isEqualTo(fakeOsName)
    }

    @Test
    fun `M call logger with correct os version W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.sendMetric(fakeStatus)

        // Then
        verify(mockInternalLogger).logMetric(any(), mapCaptor.capture())
        val deviceMap = mapCaptor.firstValue[OS_KEY] as Map<*, *>
        val osVersion = deviceMap[OS_VERSION]

        assertThat(osVersion).isEqualTo(fakeOsVersion)
    }

    @Test
    fun `M call logger with correct os build W sendMetric()`() {
        // When
        testedMethodCalledTelemetry.sendMetric(fakeStatus)

        // Then
        verify(mockInternalLogger).logMetric(any(), mapCaptor.capture())
        val deviceMap = mapCaptor.firstValue[OS_KEY] as Map<*, *>
        val osBuild = deviceMap[OS_BUILD]

        assertThat(osBuild).isEqualTo(fakeOsBuild)
    }
}
