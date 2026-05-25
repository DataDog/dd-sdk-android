/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net.info

import android.telephony.TelephonyManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CarrierInfoResolverTest {

    lateinit var testedResolver: CarrierInfoResolver

    @Mock
    lateinit var mockTelephonyManager: TelephonyManager

    @Mock
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedResolver = CarrierInfoResolver(
            mockTelephonyManager,
            mockInternalLogger,
            mockBuildSdkVersionProvider
        )
    }

    @Test
    fun `M return simCarrierIdName W carrierName {API 28+}`(@StringForgery simCarrierIdName: String) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn true
        whenever(mockTelephonyManager.simCarrierIdName) doReturn simCarrierIdName

        // When / Then
        assertThat(testedResolver.carrierName).isEqualTo(simCarrierIdName)
    }

    @Test
    fun `M return null W carrierName {API 28+, simCarrierIdName is null}`() {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn true
        whenever(mockTelephonyManager.simCarrierIdName) doReturn null

        // When / Then
        assertThat(testedResolver.carrierName).isNull()
    }

    @Test
    fun `M log error and return null W carrierName {API 28+, simCarrierIdName throws}`(@StringForgery message: String) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn true
        val exception = SecurityException(message)
        whenever(mockTelephonyManager.simCarrierIdName) doThrow exception

        // When
        val result = testedResolver.carrierName

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            CarrierInfoResolver.ERROR_CARRIER_NAME,
            exception
        )
    }

    @Test
    fun `M return networkOperatorName W carrierName {API 24-27}`(@StringForgery operatorName: String) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn false
        whenever(mockTelephonyManager.networkOperatorName) doReturn operatorName

        // When / Then
        assertThat(testedResolver.carrierName).isEqualTo(operatorName)
    }

    @Test
    fun `M return null W carrierName {API 24-27, networkOperatorName is empty}`() {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn false
        whenever(mockTelephonyManager.networkOperatorName) doReturn ""

        // When / Then
        assertThat(testedResolver.carrierName).isNull()
    }

    @Test
    fun `M return null W carrierName {API 24-27, networkOperatorName is null}`() {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn false
        whenever(mockTelephonyManager.networkOperatorName) doReturn null

        // When / Then
        assertThat(testedResolver.carrierName).isNull()
    }

    @Test
    fun `M log error and return null W carrierName {API 24-27, networkOperatorName throws}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn false
        val exception = SecurityException(message)
        whenever(mockTelephonyManager.networkOperatorName) doThrow exception

        // When
        val result = testedResolver.carrierName

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            CarrierInfoResolver.ERROR_CARRIER_NAME,
            exception
        )
    }

    @Test
    fun `M return simCarrierId as Long W carrierId {API 28+}`(@IntForgery(min = 1) simCarrierId: Int) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn true
        whenever(mockTelephonyManager.simCarrierId) doReturn simCarrierId

        // When / Then
        assertThat(testedResolver.carrierId).isEqualTo(simCarrierId.toLong())
    }

    @Test
    fun `M return null W carrierId {API 28+, simCarrierId is UNKNOWN_CARRIER_ID}`() {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn true
        whenever(mockTelephonyManager.simCarrierId) doReturn TelephonyManager.UNKNOWN_CARRIER_ID

        // When / Then
        assertThat(testedResolver.carrierId).isNull()
    }

    @Test
    fun `M log error and return null W carrierId {API 28+, simCarrierId throws}`(@StringForgery message: String) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn true
        val exception = SecurityException(message)
        whenever(mockTelephonyManager.simCarrierId) doThrow exception

        // When
        val result = testedResolver.carrierId

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            CarrierInfoResolver.ERROR_CARRIER_ID,
            exception
        )
    }

    @Test
    fun `M return networkOperator as Long W carrierId {API 24-27, numeric MCC+MNC}`(forge: Forge) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn false
        val operatorCode = forge.anInt(min = 10_000, max = 999_999).toString()
        whenever(mockTelephonyManager.networkOperator) doReturn operatorCode

        // When / Then
        assertThat(testedResolver.carrierId).isEqualTo(operatorCode.toLong())
    }

    @Test
    fun `M return null W carrierId {API 24-27, networkOperator is non-numeric}`(
        @StringForgery(regex = "[A-Za-z]+") operatorCode: String
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn false
        whenever(mockTelephonyManager.networkOperator) doReturn operatorCode

        // When / Then
        assertThat(testedResolver.carrierId).isNull()
    }

    @Test
    fun `M return null W carrierId {API 24-27, networkOperator is empty}`() {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn false
        whenever(mockTelephonyManager.networkOperator) doReturn ""

        // When / Then
        assertThat(testedResolver.carrierId).isNull()
    }

    @Test
    fun `M return null W carrierId {API 24-27, networkOperator is null}`() {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn false
        whenever(mockTelephonyManager.networkOperator) doReturn null

        // When / Then
        assertThat(testedResolver.carrierId).isNull()
    }

    @Test
    fun `M log error and return null W carrierId {API 24-27, networkOperator throws}`(@StringForgery message: String) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn false
        val exception = SecurityException(message)
        whenever(mockTelephonyManager.networkOperator) doThrow exception

        // When
        val result = testedResolver.carrierId

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            CarrierInfoResolver.ERROR_CARRIER_ID,
            exception
        )
    }

    @Test
    fun `M resolve carrierId W carrierName fails on cellular API 28+`(
        @StringForgery message: String,
        @IntForgery(min = 1) simCarrierId: Int
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn true
        val exception = SecurityException(message)
        whenever(mockTelephonyManager.simCarrierIdName) doThrow exception
        whenever(mockTelephonyManager.simCarrierId) doReturn simCarrierId

        // When / Then
        assertThat(testedResolver.carrierName).isNull()
        assertThat(testedResolver.carrierId).isEqualTo(simCarrierId.toLong())
    }

    @Test
    fun `M resolve carrierName W carrierId fails on cellular API 28+`(
        @StringForgery message: String,
        @StringForgery carrierName: String
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastP) doReturn true
        val exception = SecurityException(message)
        whenever(mockTelephonyManager.simCarrierIdName) doReturn carrierName
        whenever(mockTelephonyManager.simCarrierId) doThrow exception

        // When / Then
        assertThat(testedResolver.carrierName).isEqualTo(carrierName)
        assertThat(testedResolver.carrierId).isNull()
    }
}
