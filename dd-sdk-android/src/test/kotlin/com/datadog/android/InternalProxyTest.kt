/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.system.AppVersionProvider
import com.datadog.android.telemetry.internal.Telemetry
import com.datadog.android.utils.assertj.JsonElementAssert
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.DatadogCore
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.datadog.android.webview.internal.MixedWebViewEventConsumer
import com.datadog.android.webview.internal.log.WebViewLogsFeature
import com.datadog.android.webview.internal.rum.WebViewRumEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumFeature
import com.google.gson.JsonObject
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class InternalProxyTest {

    @Mock
    lateinit var mockCoreFeature: DatadogCore

    @Test
    fun `M proxy telemetry to RumMonitor W debug()`(
        @StringForgery message: String
    ) {
        // Given
        val mockTelemetry = mock<Telemetry>()
        val proxy = _InternalProxy(mockTelemetry, mockCoreFeature)

        // When
        proxy._telemetry.debug(message)

        // Then
        verify(mockTelemetry).debug(message)
    }

    @Test
    fun `M proxy telemetry to RumMonitor W error()`(
        @StringForgery message: String,
        @StringForgery stack: String,
        @StringForgery kind: String
    ) {
        // Given
        val mockTelemetry = mock<Telemetry>()
        val proxy = _InternalProxy(mockTelemetry, mockCoreFeature)

        // When
        proxy._telemetry.error(message, stack, kind)

        // Then
        verify(mockTelemetry).error(message, stack, kind)
    }

    @Test
    fun `M proxy telemetry to RumMonitor W error({message, throwable})`(
        @StringForgery message: String,
        @Forgery throwable: Throwable
    ) {
        // Given
        val mockTelemetry = mock<Telemetry>()
        val proxy = _InternalProxy(mockTelemetry, mockCoreFeature)

        // When
        proxy._telemetry.error(message, throwable)

        // Then
        verify(mockTelemetry).error(message, throwable)
    }

    @Test
    fun `M set app version W setCustomAppVersion()`(
        @StringForgery version: String
    ) {
        // Given
        val mockAppVersionProvider = mock<AppVersionProvider>()
        val mockCore = mock<CoreFeature>()
        whenever(mockCoreFeature.coreFeature) doReturn mockCore
        whenever(mockCore.packageVersionProvider) doReturn mockAppVersionProvider
        val proxy = _InternalProxy(telemetry = mock(), mockCoreFeature)

        // When
        proxy.setCustomAppVersion(version)

        // Then
        verify(mockAppVersionProvider).version = version
    }

    @Test
    fun `M pass web view event to RumWebEventConsumer W consumeWebViewEvent()`(
        forge: Forge
    ) {
        // Given
        val fakeBundledEvent = forge.getForgery<JsonObject>()
        val fakeRumEventType = forge.anElementFrom(WebViewRumEventConsumer.RUM_EVENT_TYPES)
        val fakeWebEvent = bundleWebEvent(fakeBundledEvent, fakeRumEventType)

        val mockWebViewRumFeature = mock<WebViewRumFeature>()
        val mockWebViewLogsFeature = mock<WebViewLogsFeature>()
        val mockRumDataWriter = mock<DataWriter<Any>>()
        val mockLogsDataWriter = mock<DataWriter<JsonObject>>()
        whenever(mockCoreFeature.webViewRumFeature) doReturn mockWebViewRumFeature
        whenever(mockCoreFeature.webViewLogsFeature) doReturn mockWebViewLogsFeature
        whenever(mockWebViewRumFeature.dataWriter) doReturn mockRumDataWriter
        whenever(mockWebViewLogsFeature.dataWriter) doReturn mockLogsDataWriter

        val mockWebRumFeatureScope = mock<FeatureScope>()
        val mockWebLogsFeatureScope = mock<FeatureScope>()
        whenever(mockCoreFeature.getFeature(WebViewRumFeature.WEB_RUM_FEATURE_NAME))doReturn
            mockWebRumFeatureScope
        whenever(mockCoreFeature.getFeature(WebViewLogsFeature.WEB_LOGS_FEATURE_NAME)) doReturn
            mockWebLogsFeatureScope

        val mockDatadogContext = mock<DatadogContext>()
        val mockEventBatchWriter = mock<EventBatchWriter>()
        val proxy = _InternalProxy(telemetry = mock(), mockCoreFeature)

        // When
        proxy.consumeWebviewEvent(fakeWebEvent.toString())
        argumentCaptor<(DatadogContext, EventBatchWriter) -> Unit> {
            verify(mockWebRumFeatureScope).withWriteContext(any(), capture())
            firstValue(mockDatadogContext, mockEventBatchWriter)
        }

        // Then
        argumentCaptor<JsonObject> {
            verify(mockRumDataWriter).write(any(), capture())
            JsonElementAssert.assertThat(firstValue).isEqualTo(fakeBundledEvent)
        }
    }
}

private fun bundleWebEvent(
    fakeBundledEvent: JsonObject?,
    eventType: String?
): JsonObject {
    val fakeWebEvent = JsonObject()
    fakeBundledEvent?.let {
        fakeWebEvent.add(MixedWebViewEventConsumer.EVENT_KEY, it)
    }
    eventType?.let {
        fakeWebEvent.addProperty(MixedWebViewEventConsumer.EVENT_TYPE_KEY, it)
    }
    return fakeWebEvent
}
