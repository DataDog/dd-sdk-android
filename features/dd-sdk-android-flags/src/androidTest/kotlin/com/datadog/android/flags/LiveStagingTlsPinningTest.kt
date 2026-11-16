/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature.Companion.FLAGS_FEATURE_NAME
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.flags.internal.FlagsFeature
import com.datadog.android.flags.internal.protection.ResponseProtectionResolver
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.privacy.TrackingConsent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Opt-in live test against the Datadog staging flag assignment endpoint, on a real Android TLS stack.
 *
 * The test is skipped unless the instrumentation argument `ddFlagsStagingClientToken` is set:
 * ```
 * ./gradlew :features:dd-sdk-android-flags:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.ddFlagsStagingClientToken=<staging client token>
 * ```
 * Never commit the token.
 */
@RunWith(AndroidJUnit4::class)
internal class LiveStagingTlsPinningTest {

    private lateinit var clientToken: String
    private val sdkCores = mutableListOf<String>()

    @Before
    fun setUp() {
        val token = InstrumentationRegistry.getArguments().getString(TOKEN_ARGUMENT)
        assumeTrue("Set the $TOKEN_ARGUMENT instrumentation argument to run this test", !token.isNullOrBlank())
        clientToken = checkNotNull(token)
        Datadog.setVerbosity(Log.VERBOSE)
    }

    @After
    fun tearDown() {
        sdkCores.forEach { Datadog.stopInstance(it) }
    }

    @Test
    fun mustEvaluateTheFlag_whenDatadogStagingV1() {
        // Given
        val (sdkCore, client) = createClient(TlsPinningProfile.DATADOG_STAGING_V1)

        // When
        val failure = setContext(client)

        // Then
        val feature = (sdkCore as FeatureSdkCore).getFeature(FLAGS_FEATURE_NAME)?.unwrap<FlagsFeature>()
        Log.i(TAG, "case 1 endpoint=${feature?.assignmentEndpoint} failure=$failure")
        assertThat(feature?.assignmentEndpoint).isEqualTo("$PROTECTED_ORIGIN/precompute-assignments")
        assertThat(failure).isNull()
        assertThat(client.resolveStringValue(FLAG_KEY, DEFAULT_VALUE)).isEqualTo(EXPECTED_VALUE)
    }

    @Test
    fun mustServeTheDefault_whenIssuerProfileWithUnrelatedPins() {
        // Given
        val profile = TlsPinningProfile.custom(
            PROTECTED_ORIGIN,
            TlsPinTarget.ISSUER,
            listOf(UNRELATED_PIN_A, UNRELATED_PIN_B)
        )
        val (_, client) = createClient(profile)

        // When
        val failure = setContext(client)

        // Then
        Log.i(TAG, "case 2 failure=$failure")
        assertThat(failure).isNotNull()
        assertThat(client.resolveStringValue(FLAG_KEY, DEFAULT_VALUE)).isEqualTo(DEFAULT_VALUE)
    }

    @Test
    fun mustEvaluateTheFlag_whenLeafProfileWithTheLiveLeafKey() {
        // Given
        val profile = TlsPinningProfile.custom(
            PROTECTED_ORIGIN,
            TlsPinTarget.LEAF,
            listOf(LIVE_LEAF_PIN, UNRELATED_PIN_A)
        )
        val (_, client) = createClient(profile)

        // When
        val failure = setContext(client)

        // Then
        Log.i(TAG, "case 3 failure=$failure")
        assertThat(failure).isNull()
        assertThat(client.resolveStringValue(FLAG_KEY, DEFAULT_VALUE)).isEqualTo(EXPECTED_VALUE)
    }

    @Test
    fun mustEvaluateTheFlag_whenNoResponseProtection() {
        // Given
        val (_, client) = createClient(null)

        // When
        val failure = setContext(client)

        // Then
        Log.i(TAG, "case 4 failure=$failure")
        assertThat(failure).isNull()
        assertThat(client.resolveStringValue(FLAG_KEY, DEFAULT_VALUE)).isEqualTo(EXPECTED_VALUE)
    }

    @Test
    fun mustExposeTheValidatedChain_whenDatadogStagingV1() {
        // Given
        val client = protectedOkHttpClient(TlsPinningProfile.DATADOG_STAGING_V1)

        // When
        val response = client.newCall(assignmentRequest()).execute()

        // Then
        val chain = response.handshake?.peerCertificates.orEmpty().map { it as X509Certificate }
        chain.forEachIndexed { index, certificate ->
            Log.i(
                TAG,
                "validated chain[$index] subject=${certificate.subjectX500Principal.name} " +
                    "issuer=${certificate.issuerX500Principal.name} " +
                    "pin=${okhttp3.CertificatePinner.pin(certificate)}"
            )
        }
        val body = response.body?.string().orEmpty()
        Log.i(
            TAG,
            "direct request code=${response.code} url=${response.request.url} containsFlag=${body.contains(FLAG_KEY)}"
        )
        assertThat(response.code).isEqualTo(200)
        assertThat(response.request.url.host).isEqualTo("protected.ff-cdn.datad0g.com")
        assertThat(body).contains(FLAG_KEY)
    }

    @Test
    fun mustFailInTheHandshake_whenIssuerProfileWithUnrelatedPins() {
        // Given
        val profile = TlsPinningProfile.custom(
            PROTECTED_ORIGIN,
            TlsPinTarget.ISSUER,
            listOf(UNRELATED_PIN_A, UNRELATED_PIN_B)
        )
        val client = protectedOkHttpClient(profile)

        // When / Then
        assertThatThrownBy { client.newCall(assignmentRequest()).execute() }
            .isInstanceOf(SSLPeerUnverifiedException::class.java)
            .satisfies({ Log.i(TAG, "direct unrelated pins failure=$it") })
    }

    // region Helpers

    private fun createClient(profile: TlsPinningProfile?): Pair<SdkCore, FlagsClient> {
        val instanceName = "flags-e2e-${UUID.randomUUID()}"
        val configuration = Configuration.Builder(clientToken, ENV).useSite(DatadogSite.STAGING).build()
        val sdkCore = checkNotNull(
            Datadog.initialize(
                instanceName,
                InstrumentationRegistry.getInstrumentation().targetContext,
                configuration,
                TrackingConsent.GRANTED
            )
        )
        sdkCores.add(instanceName)
        val flagsConfiguration = FlagsConfiguration.Builder()
            .apply {
                if (profile != null) {
                    responseProtection(ResponseProtection.tlsPinning(ResponseProtectionMode.REQUIRED, profile))
                }
            }
            .build()
        Flags.enable(flagsConfiguration, sdkCore)
        return sdkCore to FlagsClient.Builder(sdkCore = sdkCore).build()
    }

    private fun setContext(client: FlagsClient): Throwable? {
        val latch = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        client.setEvaluationContext(
            EvaluationContext(TARGETING_KEY, emptyMap()),
            object : EvaluationContextCallback {
                override fun onSuccess() = latch.countDown()

                override fun onFailure(error: Throwable) {
                    failure.set(error)
                    latch.countDown()
                }
            }
        )
        assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()
        return failure.get()
    }

    private fun protectedOkHttpClient(profile: TlsPinningProfile): OkHttpClient {
        val resolved = ResponseProtectionResolver.resolve(
            ResponseProtection.tlsPinning(ResponseProtectionMode.REQUIRED, profile),
            null
        )
        return OkHttpClient.Builder().apply { resolved.configure(this) }.build()
    }

    private fun assignmentRequest(): Request {
        val body = """
            {"data":{"type":"precompute-assignments-request","attributes":{
            "env":{"dd_env":"$ENV"},
            "source":{"sdk_name":"dd-sdk-android","sdk_version":"e2e"},
            "subject":{"targeting_key":"$TARGETING_KEY","targeting_attributes":{}}}}}
        """.trimIndent()
        return Request.Builder()
            .url("$PROTECTED_ORIGIN/precompute-assignments")
            .header("dd-client-token", clientToken)
            .post(body.toRequestBody("application/vnd.api+json".toMediaType()))
            .build()
    }

    // endregion

    private companion object {
        const val TAG = "FlagsLiveTlsPinning"
        const val TOKEN_ARGUMENT = "ddFlagsStagingClientToken"
        const val ENV = "staging"
        const val TARGETING_KEY = "e2e-user-1"
        const val FLAG_KEY = "dogmail-assignment-flag-treatment-control"
        const val EXPECTED_VALUE = "treatment"
        const val DEFAULT_VALUE = "default"
        const val PROTECTED_ORIGIN = "https://protected.ff-cdn.datad0g.com"
        const val LIVE_LEAF_PIN = "sha256/hgvFJ1yECRJcFdzcv5AwkIyeo1LphKXbqKSUP4TYpCk="
        const val UNRELATED_PIN_A = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        const val UNRELATED_PIN_B = "sha256/AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
        const val TIMEOUT_SECONDS = 30L
    }
}
