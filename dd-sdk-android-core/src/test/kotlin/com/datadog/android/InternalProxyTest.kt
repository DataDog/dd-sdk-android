/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.DatadogCore
import com.datadog.android.core.internal.system.AppVersionProvider
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class InternalProxyTest {

    @Test
    fun `M proxy telemetry to RumMonitor W debug()`(
        @StringForgery message: String
    ) {
        // Given
        val mockSdkCore = mock<DatadogCore>()
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        val proxy = _InternalProxy(mockSdkCore)

        // When
        proxy._telemetry.debug(message)

        // Then
        verify(mockRumFeatureScope).sendEvent(
            mapOf(
                "type" to "telemetry_debug",
                "message" to message
            )
        )
    }

    @Test
    fun `M proxy telemetry to RumMonitor W error()`(
        @StringForgery message: String,
        @StringForgery stack: String,
        @StringForgery kind: String
    ) {
        // Given
        val mockSdkCore = mock<DatadogCore>()
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        val proxy = _InternalProxy(mockSdkCore)

        // When
        proxy._telemetry.error(message, stack, kind)

        // Then
        verify(mockRumFeatureScope).sendEvent(
            mapOf(
                "type" to "telemetry_error",
                "message" to message,
                "stacktrace" to stack,
                "kind" to kind
            )
        )
    }

    @Test
    fun `M proxy telemetry to RumMonitor W error({message, throwable})`(
        @StringForgery message: String,
        @Forgery throwable: Throwable
    ) {
        // Given
        val mockSdkCore = mock<DatadogCore>()
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        val proxy = _InternalProxy(mockSdkCore)

        // When
        proxy._telemetry.error(message, throwable)

        // Then
        verify(mockRumFeatureScope).sendEvent(
            mapOf(
                "type" to "telemetry_error",
                "message" to message,
                "throwable" to throwable
            )
        )
    }

    @Test
    fun `M set app version W setCustomAppVersion()`(
        @StringForgery version: String
    ) {
        // Given
        val mockSdkCore = mock<DatadogCore>()
        val mockAppVersionProvider = mock<AppVersionProvider>()
        val mockCoreFeature = mock<CoreFeature>()
        whenever(mockCoreFeature.packageVersionProvider) doReturn mockAppVersionProvider
        whenever(mockSdkCore.coreFeature) doReturn mockCoreFeature
        val proxy = _InternalProxy(mockSdkCore)

        // When
        proxy.setCustomAppVersion(version)

        // Then
        verify(mockAppVersionProvider).version = version
    }
}
