/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.webview.internal.rum.WebViewRumFeature
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.net.URL

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogEventBridgeTest {

    lateinit var testedDatadogEventBridge: DatadogEventBridge

    @Mock
    lateinit var mockWebViewEventConsumer: MixedWebViewEventConsumer

    @StringForgery
    lateinit var fakePrivacyLevel: String

    @Mock
    lateinit var mockWebViewRumFeature: WebViewRumFeature

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedDatadogEventBridge = DatadogEventBridge(
            mockWebViewEventConsumer,
            emptyList(),
            fakePrivacyLevel,
            mockWebViewRumFeature,
            mockInternalLogger
        )
    }

    @Test
    fun `M delegate to WebEventConsumer W send()`(@StringForgery fakeEvent: String) {
        // When
        testedDatadogEventBridge.send(fakeEvent)

        // Then
        verify(mockWebViewEventConsumer).consume(fakeEvent)
    }

    @Test
    fun `M return sanitized webViewTrackingHosts W getAllowedWebViewHosts() { allow IP addresses }`(
        @StringForgery(
            regex = "(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}" +
                "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])"
        ) hosts: List<String>
    ) {
        // Given
        val expectedHosts = hosts.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        testedDatadogEventBridge = DatadogEventBridge(
            mock(),
            hosts,
            fakePrivacyLevel,
            mockWebViewRumFeature,
            mockInternalLogger
        )

        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        assertThat(allowedWebViewHosts).isEqualTo(expectedHosts)
    }

    @Test
    fun `M return sanitized webViewTrackingHosts W getAllowedWebViewHosts() { allow host names }`(
        @StringForgery(
            regex = "(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])\\.)+" +
                "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])"
        ) hosts: List<String>
    ) {
        // Given
        val expectedHosts = hosts.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        testedDatadogEventBridge = DatadogEventBridge(
            mockWebViewEventConsumer,
            hosts,
            fakePrivacyLevel,
            mockWebViewRumFeature,
            mockInternalLogger
        )

        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        assertThat(allowedWebViewHosts).isEqualTo(expectedHosts)
    }

    @Test
    fun `M return sanitized webViewTrackingHosts W getAllowedWebViewHosts() { allow URLs }`(
        @StringForgery(
            regex = "(https|http)://([a-z][a-z0-9-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}"
        ) hosts: List<String>
    ) {
        // Given
        val expectedHosts = hosts.map { URL(it).host }
            .joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        testedDatadogEventBridge = DatadogEventBridge(
            mockWebViewEventConsumer,
            hosts,
            fakePrivacyLevel,
            mockWebViewRumFeature,
            mockInternalLogger
        )

        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        assertThat(allowedWebViewHosts).isEqualTo(expectedHosts)
    }

    @Test
    fun `M return sanitized plain hosts and wildcard patterns W getAllowedWebViewHosts() { wildcard patterns }`() {
        // Given
        val patterns = listOf("*.example.com", "preview-*.shopist.io", "example.net")
        testedDatadogEventBridge = DatadogEventBridge(
            mockWebViewEventConsumer,
            patterns,
            fakePrivacyLevel,
            mockWebViewRumFeature,
            mockInternalLogger
        )

        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        // Plain hosts are sanitized first, then wildcard patterns are appended.
        assertThat(allowedWebViewHosts)
            .isEqualTo("[\"example.net\",\"*.example.com\",\"preview-*.shopist.io\"]")
    }

    @Test
    fun `M drop invalid patterns W getAllowedWebViewHosts() { invalid patterns }`() {
        // Given
        val patterns = listOf("*.example.com", "*.foo.*.bar", "in valid", "EXAMPLE.NET")
        testedDatadogEventBridge = DatadogEventBridge(
            mockWebViewEventConsumer,
            patterns,
            fakePrivacyLevel,
            mockWebViewRumFeature,
            mockInternalLogger
        )

        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        // "in valid" is dropped by the host sanitizer, "*.foo.*.bar" by the pattern validator.
        // "EXAMPLE.NET" is a plain host so it keeps the sanitizer's behavior (no lowercasing).
        assertThat(allowedWebViewHosts).isEqualTo("[\"EXAMPLE.NET\",\"*.example.com\"]")
    }

    @Test
    fun `M drop bare TLD and empty entries W getAllowedWebViewHosts() { edge cases }`() {
        // Given
        val patterns = listOf("com", "", "*", "*.co.uk", "*.example.com")
        testedDatadogEventBridge = DatadogEventBridge(
            mockWebViewEventConsumer,
            patterns,
            fakePrivacyLevel,
            mockWebViewRumFeature,
            mockInternalLogger
        )

        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        // "com" (bare TLD) and "" are wildcard-free, so HostsSanitizer drops them.
        // "*" and "*.co.uk" are dropped by the pattern validator because a wildcard must match
        // subdomains of a registrable domain (they are too broad).
        assertThat(allowedWebViewHosts).isEqualTo("[\"*.example.com\"]")
    }

    @Test
    fun `M strip URLs from plain hosts W getAllowedWebViewHosts() { url in patterns }`() {
        // Given
        val patterns = listOf("https://foo.com", "*.shopist.io")
        testedDatadogEventBridge = DatadogEventBridge(
            mockWebViewEventConsumer,
            patterns,
            fakePrivacyLevel,
            mockWebViewRumFeature,
            mockInternalLogger
        )

        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        // "https://foo.com" is a wildcard-free entry, so it is URL-stripped to its host by the
        // sanitizer rather than being dropped for containing invalid characters.
        assertThat(allowedWebViewHosts).isEqualTo("[\"foo.com\",\"*.shopist.io\"]")
    }

    @Test
    fun `M merge sanitized hosts and patterns W getAllowedWebViewHosts() { mixed }`() {
        // Given
        val hosts = listOf("example.com", "*.shopist.io")
        testedDatadogEventBridge = DatadogEventBridge(
            mockWebViewEventConsumer,
            hosts,
            fakePrivacyLevel,
            mockWebViewRumFeature,
            mockInternalLogger
        )

        // When
        val allowedWebViewHosts = testedDatadogEventBridge.getAllowedWebViewHosts()

        // Then
        assertThat(allowedWebViewHosts).isEqualTo("[\"example.com\",\"*.shopist.io\"]")
    }

    @Test
    fun `M return the provided privacy level W getPrivacyLevel()`() {
        // When
        val privacyLevel = testedDatadogEventBridge.getPrivacyLevel()

        // Then
        assertThat(privacyLevel).isEqualTo(fakePrivacyLevel)
    }

    @Test
    fun `M return the supported capabilities W getCapabilities()`() {
        // Given
        val expectedCapabilities = "[\"records\"]"

        // When
        val capabilities = testedDatadogEventBridge.getCapabilities()

        // Then
        assertThat(capabilities).isEqualTo(expectedCapabilities)
    }

    @Test
    fun `M return true W getIsTraceSampled() { tracked session, sampled }`() {
        // Given
        val sessionId = "c5b3c4ab-fa4a-4de9-8199-a522131ec48a"
        whenever(mockWebViewRumFeature.cachedRumContext).thenReturn(
            mapOf(
                "session_id" to sessionId,
                "session_state" to "TRACKED",
                "session_sample_rate" to 100f
            )
        )
        whenever(mockWebViewRumFeature.cachedTracingContext)
            .thenReturn(mapOf("okhttp_interceptor_sample_rate" to 100f))

        // When
        val result = testedDatadogEventBridge.getIsTraceSampled()

        // Then
        assertThat(result).isEqualTo("true")
    }

    @Test
    fun `M return false W getIsTraceSampled() { tracked session, not sampled }`() {
        // Given
        // This UUID hashes to ~50.68%, so at combined rate 40% (100% session * 40% trace) it's not sampled
        val sessionId = "c5b3c4ab-fa4a-4de9-8199-a522131ec48a"
        whenever(mockWebViewRumFeature.cachedRumContext).thenReturn(
            mapOf(
                "session_id" to sessionId,
                "session_state" to "TRACKED",
                "session_sample_rate" to 100f
            )
        )
        whenever(mockWebViewRumFeature.cachedTracingContext)
            .thenReturn(mapOf("okhttp_interceptor_sample_rate" to 40f))

        // When
        val result = testedDatadogEventBridge.getIsTraceSampled()

        // Then
        assertThat(result).isEqualTo("false")
    }

    @Test
    fun `M return null W getIsTraceSampled() { no rum feature }`() {
        // Given
        testedDatadogEventBridge = DatadogEventBridge(
            mockWebViewEventConsumer,
            emptyList(),
            fakePrivacyLevel,
            null,
            mockInternalLogger
        )

        // When
        val result = testedDatadogEventBridge.getIsTraceSampled()

        // Then
        assertThat(result).isEqualTo("null")
    }

    @Test
    fun `M return null W getIsTraceSampled() { no session id }`() {
        // Given
        whenever(mockWebViewRumFeature.cachedRumContext).thenReturn(emptyMap())

        // When
        val result = testedDatadogEventBridge.getIsTraceSampled()

        // Then
        assertThat(result).isEqualTo("null")
    }

    @Test
    fun `M return null W getIsTraceSampled() { session not tracked }`() {
        // Given
        whenever(mockWebViewRumFeature.cachedRumContext).thenReturn(
            mapOf(
                "session_id" to "some-session-id",
                "session_state" to "NOT_TRACKED"
            )
        )

        // When
        val result = testedDatadogEventBridge.getIsTraceSampled()

        // Then
        assertThat(result).isEqualTo("null")
    }

    @Test
    fun `M return null W getIsTraceSampled() { no trace sample rate configured }`() {
        // Given
        whenever(mockWebViewRumFeature.cachedRumContext).thenReturn(
            mapOf(
                "session_id" to "some-session-id",
                "session_state" to "TRACKED",
                "session_sample_rate" to 100f
            )
        )
        whenever(mockWebViewRumFeature.cachedTracingContext).thenReturn(emptyMap())

        // When
        val result = testedDatadogEventBridge.getIsTraceSampled()

        // Then
        assertThat(result).isEqualTo("null")
    }

    @Test
    fun `M return null W getIsTraceSampled() { no session sample rate }`() {
        // Given
        whenever(mockWebViewRumFeature.cachedRumContext).thenReturn(
            mapOf(
                "session_id" to "some-session-id",
                "session_state" to "TRACKED"
            )
        )

        // When
        val result = testedDatadogEventBridge.getIsTraceSampled()

        // Then
        assertThat(result).isEqualTo("null")
    }

    @Test
    fun `M update decision W getIsTraceSampled() { session rolls over to a different decision }`() {
        // Given
        // UUID hashes to ~50.68%: sampled at combined 60% (100% * 60%), not sampled at combined 30% (50% * 60%)
        val sessionId = "c5b3c4ab-fa4a-4de9-8199-a522131ec48a"
        whenever(mockWebViewRumFeature.cachedTracingContext)
            .thenReturn(mapOf("okhttp_interceptor_sample_rate" to 60f))
        whenever(mockWebViewRumFeature.cachedRumContext)
            .thenReturn(
                mapOf("session_id" to sessionId, "session_state" to "TRACKED", "session_sample_rate" to 100f)
            )
            .thenReturn(
                mapOf("session_id" to sessionId, "session_state" to "TRACKED", "session_sample_rate" to 50f)
            )

        // When
        val result1 = testedDatadogEventBridge.getIsTraceSampled()
        val result2 = testedDatadogEventBridge.getIsTraceSampled()

        // Then: first sampled (combined 60%), second not sampled (combined 30%, UUID hashes at ~50.68%)
        assertThat(result1).isEqualTo("true")
        assertThat(result2).isEqualTo("false")
    }

    @Test
    fun `M return null W getIsTraceSampled() { session stops after being tracked }`() {
        // Given: active tracked session
        val sessionId = "c5b3c4ab-fa4a-4de9-8199-a522131ec48a"
        whenever(mockWebViewRumFeature.cachedTracingContext)
            .thenReturn(mapOf("okhttp_interceptor_sample_rate" to 100f))
        whenever(mockWebViewRumFeature.cachedRumContext)
            .thenReturn(
                mapOf("session_id" to sessionId, "session_state" to "TRACKED", "session_sample_rate" to 100f)
            )
            .thenReturn(
                mapOf("session_id" to sessionId, "session_state" to "NOT_TRACKED")
            )

        // When
        val resultBeforeStop = testedDatadogEventBridge.getIsTraceSampled()
        val resultAfterStop = testedDatadogEventBridge.getIsTraceSampled()

        // Then
        assertThat(resultBeforeStop).isEqualTo("true")
        assertThat(resultAfterStop).isEqualTo("null")
    }

    @Test
    fun `M return null then decision W getIsTraceSampled() { new session starts after stop }`() {
        // Given: no active session initially
        whenever(mockWebViewRumFeature.cachedTracingContext)
            .thenReturn(mapOf("okhttp_interceptor_sample_rate" to 100f))
        whenever(mockWebViewRumFeature.cachedRumContext)
            .thenReturn(
                mapOf("session_id" to "old-session-id", "session_state" to "NOT_TRACKED")
            )
            .thenReturn(
                mapOf(
                    "session_id" to "c5b3c4ab-fa4a-4de9-8199-a522131ec48a",
                    "session_state" to "TRACKED",
                    "session_sample_rate" to 100f
                )
            )

        // When
        val resultNoSession = testedDatadogEventBridge.getIsTraceSampled()
        val resultNewSession = testedDatadogEventBridge.getIsTraceSampled()

        // Then
        assertThat(resultNoSession).isEqualTo("null")
        assertThat(resultNewSession).isEqualTo("true")
    }
}
