/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet

import com.datadog.android.cronet.DatadogCronetEngine.Companion.CRONET_NETWORK_INSTRUMENTATION_NAME
import com.datadog.android.cronet.internal.DatadogRequestFinishedInfoListener
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.internal.net.RumResourceInstrumentation
import com.datadog.android.rum.internal.net.RumResourceInstrumentationAssert
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.chromium.net.ConnectionMigrationOptions
import org.chromium.net.CronetEngine
import org.chromium.net.DnsOptions
import org.chromium.net.Proxy
import org.chromium.net.ProxyOptions
import org.chromium.net.QuicOptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Date
import java.util.concurrent.Executor

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogCronetEngineBuilderTest {

    @Mock
    private lateinit var mockBuilderDelegate: CronetEngine.Builder

    @Mock
    private lateinit var mockCronetEngine: CronetEngine

    private lateinit var testedBuilder: DatadogCronetEngine.Builder

    @OptIn(ExperimentalRumApi::class)
    @BeforeEach
    fun `set up`() {
        testedBuilder = DatadogCronetEngine.Builder(
            iCronetEngineBuilder = mock(),
            delegate = mockBuilderDelegate
        )
        whenever(mockBuilderDelegate.build()).thenReturn(mockCronetEngine)
    }

    @Test
    fun `M delegate W defaultUserAgent()`(@StringForgery userAgent: String) {
        // Given
        whenever(mockBuilderDelegate.defaultUserAgent).thenReturn(userAgent)

        // When
        val result = testedBuilder.defaultUserAgent

        // Then
        assertThat(result).isEqualTo(userAgent)
    }

    @Test
    fun `M delegate W setUserAgent()`(@StringForgery userAgent: String) {
        // When
        val builder = testedBuilder.setUserAgent(userAgent)

        // Then
        verify(mockBuilderDelegate).setUserAgent(userAgent)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W setStoragePath()`(@StringForgery path: String) {
        // When
        val builder = testedBuilder.setStoragePath(path)

        // Then
        verify(mockBuilderDelegate).setStoragePath(path)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W setLibraryLoader()`() {
        // Given
        val mockLoader = mock<CronetEngine.Builder.LibraryLoader>()

        // When
        val builder = testedBuilder.setLibraryLoader(mockLoader)

        // Then
        verify(mockBuilderDelegate).setLibraryLoader(mockLoader)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W enableQuic()`(@BoolForgery enableQuic: Boolean) {
        // When
        val builder = testedBuilder.enableQuic(enableQuic)

        // Then
        verify(mockBuilderDelegate).enableQuic(enableQuic)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W enableHttp2()`(@BoolForgery enableHttp2: Boolean) {
        // When
        val builder = testedBuilder.enableHttp2(enableHttp2)

        // Then
        verify(mockBuilderDelegate).enableHttp2(enableHttp2)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `M delegate W enableSdch()`(@BoolForgery enableSdch: Boolean) {
        // When
        val builder = testedBuilder.enableSdch(enableSdch)

        // Then
        verify(mockBuilderDelegate).enableSdch(enableSdch)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W enableBrotli()`(@BoolForgery enableBrotli: Boolean) {
        // When
        val builder = testedBuilder.enableBrotli(enableBrotli)

        // Then
        verify(mockBuilderDelegate).enableBrotli(enableBrotli)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W enableHttpCache()`(
        @LongForgery(min = 0) maxSize: Long,
        forge: Forge
    ) {
        // Given
        val cacheMode = forge.anElementFrom(
            CronetEngine.Builder.HTTP_CACHE_DISK,
            CronetEngine.Builder.HTTP_CACHE_IN_MEMORY,
            CronetEngine.Builder.HTTP_CACHE_DISABLED,
            CronetEngine.Builder.HTTP_CACHE_DISK_NO_HTTP
        )

        // When
        val builder = testedBuilder.enableHttpCache(cacheMode, maxSize)

        // Then
        verify(mockBuilderDelegate).enableHttpCache(cacheMode, maxSize)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W addQuicHint()`(
        @StringForgery host: String,
        @IntForgery(min = 0, max = 65535) port: Int,
        @IntForgery(min = 0, max = 65535) alternatePort: Int
    ) {
        // When
        val builder = testedBuilder.addQuicHint(host, port, alternatePort)

        // Then
        verify(mockBuilderDelegate).addQuicHint(host, port, alternatePort)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W addPublicKeyPins()`(
        forge: Forge
    ) {
        // Given
        val hostName = forge.aString()
        val includeSubdomains = forge.aBool()
        val pinsSha256 = setOf(
            ByteArray(5) { forge.anInt().toByte() },
            ByteArray(5) { forge.anInt().toByte() }
        )
        val expirationDate = forge.getForgery<Date>()

        // When
        val builder = testedBuilder.addPublicKeyPins(hostName, pinsSha256, includeSubdomains, expirationDate)

        // Then
        verify(mockBuilderDelegate).addPublicKeyPins(hostName, pinsSha256, includeSubdomains, expirationDate)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W enablePublicKeyPinningBypassForLocalTrustAnchors()`(
        @BoolForgery value: Boolean
    ) {
        // When
        val builder = testedBuilder.enablePublicKeyPinningBypassForLocalTrustAnchors(value)

        // Then
        verify(mockBuilderDelegate).enablePublicKeyPinningBypassForLocalTrustAnchors(value)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `M delegate W setThreadPriority()`(
        @IntForgery(min = 0) priority: Int
    ) {
        // When
        val builder = testedBuilder.setThreadPriority(priority)

        // Then
        verify(mockBuilderDelegate).setThreadPriority(priority)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W enableNetworkQualityEstimator()`(@BoolForgery value: Boolean) {
        // When
        val builder = testedBuilder.enableNetworkQualityEstimator(value)

        // Then
        verify(mockBuilderDelegate).enableNetworkQualityEstimator(value)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W setQuicOptions() { QuicOptions }`() {
        // Given
        val quicOptions = QuicOptions.builder().build()

        // When
        val builder = testedBuilder.setQuicOptions(quicOptions)

        // Then
        verify(mockBuilderDelegate).setQuicOptions(quicOptions)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W setQuicOptions() { QuicOptionsBuilder }`() {
        // Given
        val quicOptionsBuilder = QuicOptions.builder()

        // When
        val builder = testedBuilder.setQuicOptions(quicOptionsBuilder)

        // Then
        verify(mockBuilderDelegate).setQuicOptions(quicOptionsBuilder)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W setDnsOptions() { DnsOptions }`() {
        // Given
        val dnsOptions = DnsOptions.builder().build()

        // When
        val builder = testedBuilder.setDnsOptions(dnsOptions)

        // Then
        verify(mockBuilderDelegate).setDnsOptions(dnsOptions)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W setDnsOptions() { DnsOptionsBuilder }`() {
        // Given
        val dnsOptionsBuilder = DnsOptions.builder()

        // When
        val builder = testedBuilder.setDnsOptions(dnsOptionsBuilder)

        // Then
        verify(mockBuilderDelegate).setDnsOptions(dnsOptionsBuilder)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W setConnectionMigrationOptions() { ConnectionMigrationOptions }`() {
        // Given
        val connectionMigrationOptions = ConnectionMigrationOptions.builder().build()

        // When
        val builder = testedBuilder.setConnectionMigrationOptions(connectionMigrationOptions)

        // Then
        verify(mockBuilderDelegate).setConnectionMigrationOptions(connectionMigrationOptions)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W setConnectionMigrationOptions() { ConnectionMigrationOptionsBuilder }`() {
        // Given
        val connectionMigrationOptionsBuilder = ConnectionMigrationOptions.builder()

        // When
        val builder = testedBuilder.setConnectionMigrationOptions(connectionMigrationOptionsBuilder)

        // Then
        verify(mockBuilderDelegate).setConnectionMigrationOptions(connectionMigrationOptionsBuilder)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @Test
    fun `M delegate W setProxyOptions()`(@Forgery proxy: Proxy) {
        // Given
        val proxyOptions = ProxyOptions(listOf(proxy))

        // When
        val builder = testedBuilder.setProxyOptions(proxyOptions)

        // Then
        verify(mockBuilderDelegate).setProxyOptions(proxyOptions)
        assertThat(builder).isSameAs(testedBuilder)
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M propagate RumResourceAttributesProvider W setCustomRumInstrumentation()`() {
        // Given
        val customProvider = mock<RumResourceAttributesProvider>()
        whenever(mockBuilderDelegate.build()).thenReturn(mock())
        val customConfig = RumResourceInstrumentation.Configuration()
            .setRumResourceAttributesProvider(customProvider)

        // When
        val builder = testedBuilder.setCustomRumInstrumentation(customConfig)
        val engine = builder.build()

        // Then
        assertThat(builder).isSameAs(testedBuilder)
        check(engine is DatadogCronetEngine)
        RumResourceInstrumentationAssert.assertThat(engine.rumResourceInstrumentation!!)
            .hasRumResourceAttributesProvider(customProvider)
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M propagate sdkInstanceName W setCustomRumInstrumentation()`(@StringForgery sdkInstanceName: String) {
        // Given
        val customConfig = RumResourceInstrumentation.Configuration()
            .setSdkInstanceName(sdkInstanceName)

        // When
        val builder = testedBuilder.setCustomRumInstrumentation(customConfig)
        val engine = builder.build()

        // Then
        check(engine is DatadogCronetEngine)
        assertThat(builder).isSameAs(testedBuilder)
        RumResourceInstrumentationAssert.assertThat(engine.rumResourceInstrumentation!!)
            .hasSdkInstanceName(sdkInstanceName)
    }

    @Test
    fun `M use Cronet as network layer name W build()`() {
        // Given
        whenever(mockBuilderDelegate.build()).thenReturn(mock())

        // When
        val engine = testedBuilder.build()

        // Then
        check(engine is DatadogCronetEngine)
        RumResourceInstrumentationAssert.assertThat(engine.rumResourceInstrumentation!!)
            .hasNetworkLayerName(CRONET_NETWORK_INSTRUMENTATION_NAME)
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M propagate executor W setListenerExecutor()`() {
        // Given
        val customExecutor = mock<Executor>()

        // When
        val builder = testedBuilder.setListenerExecutor(customExecutor)
        val engine = builder.build()

        // Then
        assertThat(builder).isSameAs(testedBuilder)
        check(engine is DatadogCronetEngine)
        argumentCaptor<DatadogRequestFinishedInfoListener> {
            verify(mockCronetEngine).addRequestFinishedListener(capture())
            assertThat(firstValue.executor).isSameAs(customExecutor)
            assertThat(firstValue.rumResourceInstrumentation).isSameAs(engine.rumResourceInstrumentation)
        }
    }
}
