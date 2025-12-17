/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import org.assertj.core.api.Assertions.assertThat
import org.chromium.net.CronetEngine
import org.chromium.net.RequestFinishedInfo
import org.chromium.net.UrlRequest
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal class CronetApiInstrumentationTest {

    @Test
    fun `M have expected methods W RequestFinishedInfo_Listener`() {
        // Given
        val expectedMethods = setOf(
            "onRequestFinished(org.chromium.net.RequestFinishedInfo): void",
            "getExecutor(): java.util.concurrent.Executor"
        )

        // When
        val actualMethods = getPublicMethodSignatures(RequestFinishedInfo.Listener::class.java)

        // Then
        assertSameMethods(actualMethods, expectedMethods)
    }

    @Test
    fun `M have expected methods W CronetEngine`() {
        // Given
        val expectedMethods = setOf(
            "shutdown(): void",
            "openConnection(java.net.URL): java.net.URLConnection",
            "stopNetLog(): void",
            "getVersionString(): java.lang.String",
            "startNetLogToFile(java.lang.String, boolean): void",
            "getGlobalMetricsDeltas(): [B",
            "newUrlRequestBuilder(java.lang.String, org.chromium.net.UrlRequest\$Callback, " +
                "java.util.concurrent.Executor): org.chromium.net.UrlRequest\$Builder",
            "getActiveRequestCount(): int",
            "addRequestFinishedListener(org.chromium.net.RequestFinishedInfo\$Listener): void",
            "getHttpRttMs(): int",
            "getTransportRttMs(): int",
            "startNetLogToDisk(java.lang.String, boolean, int): void",
            "bindToNetwork(long): void",
            "getEffectiveConnectionType(): int",
            "addRttListener(org.chromium.net.NetworkQualityRttListener): void",
            "removeRttListener(org.chromium.net.NetworkQualityRttListener): void",
            "addThroughputListener(org.chromium.net.NetworkQualityThroughputListener): void",
            "removeThroughputListener(org.chromium.net.NetworkQualityThroughputListener): void",
            "createURLStreamHandlerFactory(): java.net.URLStreamHandlerFactory",
            "newBidirectionalStreamBuilder(java.lang.String, org.chromium.net.BidirectionalStream\$Callback, " +
                "java.util.concurrent.Executor): org.chromium.net.BidirectionalStream\$Builder",
            "removeRequestFinishedListener(org.chromium.net.RequestFinishedInfo\$Listener): void",
            "getDownstreamThroughputKbps(): int",
            "configureNetworkQualityEstimatorForTesting(boolean, boolean, boolean): void"
        )

        // When
        val actualMethods = getPublicMethodSignatures(CronetEngine::class.java)

        // Then
        assertSameMethods(actualMethods, expectedMethods)
    }

    @Test
    fun `M have expected methods W CronetEngine_Builder`() {
        // Given
        val expectedMethods = setOf(
            "getDefaultUserAgent(): java.lang.String",
            "enableQuic(boolean): org.chromium.net.CronetEngine\$Builder",
            "setStoragePath(java.lang.String): org.chromium.net.CronetEngine\$Builder",
            "setUserAgent(java.lang.String): org.chromium.net.CronetEngine\$Builder",
            "setLibraryLoader(org.chromium.net.CronetEngine\$Builder\$LibraryLoader): " +
                "org.chromium.net.CronetEngine\$Builder",
            "enableHttp2(boolean): org.chromium.net.CronetEngine\$Builder",
            "enableSdch(boolean): org.chromium.net.CronetEngine\$Builder",
            "enableBrotli(boolean): org.chromium.net.CronetEngine\$Builder",
            "enableHttpCache(int, long): org.chromium.net.CronetEngine\$Builder",
            "addQuicHint(java.lang.String, int, int): org.chromium.net.CronetEngine\$Builder",
            "addPublicKeyPins(java.lang.String, java.util.Set, boolean, java.util.Date): " +
                "org.chromium.net.CronetEngine\$Builder",
            "enablePublicKeyPinningBypassForLocalTrustAnchors(boolean): org.chromium.net.CronetEngine\$Builder",
            "setThreadPriority(int): org.chromium.net.CronetEngine\$Builder",
            "enableNetworkQualityEstimator(boolean): org.chromium.net.CronetEngine\$Builder",
            "setQuicOptions(org.chromium.net.QuicOptions): org.chromium.net.CronetEngine\$Builder",
            "setQuicOptions(org.chromium.net.QuicOptions\$Builder): org.chromium.net.CronetEngine\$Builder",
            "setDnsOptions(org.chromium.net.DnsOptions): org.chromium.net.CronetEngine\$Builder",
            "setDnsOptions(org.chromium.net.DnsOptions\$Builder): org.chromium.net.CronetEngine\$Builder",
            "setConnectionMigrationOptions(org.chromium.net.ConnectionMigrationOptions): " +
                "org.chromium.net.CronetEngine\$Builder",
            "setConnectionMigrationOptions(org.chromium.net.ConnectionMigrationOptions\$Builder): " +
                "org.chromium.net.CronetEngine\$Builder",
            "setProxyOptions(org.chromium.net.ProxyOptions): org.chromium.net.CronetEngine\$Builder",
            "build(): org.chromium.net.CronetEngine"
        )

        // When
        val actualMethods = getPublicMethodSignatures(CronetEngine.Builder::class.java)

        // Then
        assertSameMethods(actualMethods, expectedMethods)
    }

    @Test
    fun `M have expected methods W UrlRequest`() {
        // Given
        val expectedMethods = setOf(
            "getStatus(org.chromium.net.UrlRequest\$StatusListener): void",
            "start(): void",
            "read(java.nio.ByteBuffer): void",
            "cancel(): void",
            "isDone(): boolean",
            "followRedirect(): void"
        )

        // When
        val actualMethods = getPublicMethodSignatures(UrlRequest::class.java)

        // Then
        assertSameMethods(actualMethods, expectedMethods)
    }

    @Test
    fun `M have expected methods W UrlRequest_Builder`() {
        // Given
        val expectedMethods = setOf(
            "build(): org.chromium.net.UrlRequest",
            "setPriority(int): org.chromium.net.UrlRequest\$Builder",
            "addHeader(java.lang.String, java.lang.String): org.chromium.net.UrlRequest\$Builder",
            "setHttpMethod(java.lang.String): org.chromium.net.UrlRequest\$Builder",
            "disableCache(): org.chromium.net.UrlRequest\$Builder",
            "setUploadDataProvider(org.chromium.net.UploadDataProvider, java.util.concurrent.Executor): " +
                "org.chromium.net.UrlRequest\$Builder",
            "allowDirectExecutor(): org.chromium.net.UrlRequest\$Builder",
            "addRequestAnnotation(java.lang.Object): org.chromium.net.UrlRequest\$Builder",
            "bindToNetwork(long): org.chromium.net.UrlRequest\$Builder",
            "setTrafficStatsTag(int): org.chromium.net.UrlRequest\$Builder",
            "setTrafficStatsUid(int): org.chromium.net.UrlRequest\$Builder",
            "setRequestFinishedListener(org.chromium.net.RequestFinishedInfo\$Listener): " +
                "org.chromium.net.UrlRequest\$Builder",
            "setRawCompressionDictionary([B, java.nio.ByteBuffer, java.lang.String): " +
                "org.chromium.net.UrlRequest\$Builder"
        )

        // When
        val actualMethods = getPublicMethodSignatures(UrlRequest.Builder::class.java)

        // Then
        assertSameMethods(actualMethods, expectedMethods)
    }

    companion object {
        private fun assertSameMethods(
            actualMethods: Set<String>,
            expectedMethods: Set<String>
        ) = assertThat(actualMethods)
            .overridingErrorMessage {
                if (actualMethods.size < expectedMethods.size) {
                    val methodsList = (expectedMethods - actualMethods).map(::escapeMethodSignature)
                    "Missing methods: $methodsList"
                } else {
                    val methodsList = (actualMethods - expectedMethods).map(::escapeMethodSignature)
                    "Extra methods: $methodsList"
                }.replace("]", "\n]")
            }
            .isEqualTo(expectedMethods)

        private fun escapeMethodSignature(signature: String): String = "\n\"$signature\"".replace("$", "\\$")

        private fun getPublicMethodSignatures(clazz: Class<*>): Set<String> = clazz.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .map { it.toSignatureString() }
            .toSet()

        private fun Method.toSignatureString(): String {
            val params = parameterTypes.joinToString(", ") { it.name }
            return "$name($params): ${returnType.name}"
        }
    }
}
