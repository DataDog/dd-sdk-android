/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.flags.AssignmentAuthorization
import com.datadog.android.flags.AssignmentProtection
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.net.AssignmentAuthorizationStore
import com.datadog.android.flags.internal.net.AssignmentPayloadVerifier
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsRequestFactory
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsVerifier
import com.datadog.android.flags.internal.net.ProtectedAssignmentEnvelope
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DefaultProtectedAssignmentsCacheVerifierTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M restore flags W verify() { signed-only production request scope }`(
        @Forgery fakeDatadogContext: DatadogContext
    ) {
        val context = EvaluationContext("subject-1", mapOf("country" to "US"))
        val envelope = protectedEnvelope(AssignmentProtection.SIGNED)
        val capturedRequest = AtomicReference<Request>()
        val capturedResponse = AtomicReference<Response>()
        val testedVerifier = cacheVerifier(
            protection = AssignmentProtection.SIGNED,
            payloadVerifier = AssignmentPayloadVerifier { request, response, body ->
                capturedRequest.set(request)
                capturedResponse.set(response)
                assertThat(body.decodeToString()).isEqualTo(RAW_RESPONSE_BODY)
                envelope
            }
        )

        val flags = testedVerifier.verify(
            entry = protectedEntry(context, envelope),
            context = context,
            datadogContext = scopedDatadogContext(fakeDatadogContext)
        )

        assertThat(flags?.get("fixture")?.variationValue).isEqualTo("enabled")
        assertThat(capturedRequest.get().url.toString()).isEqualTo(ENDPOINT)
        assertThat(capturedRequest.get().header("dd-client-token")).isEqualTo("client-token")
        assertThat(capturedRequest.get().header("Authorization")).isNull()
        assertThat(capturedRequest.get().header(PrecomputedAssignmentsVerifier.REQUEST_NONCE_HEADER))
            .isEqualTo(envelope.requestNonce)
        assertThat(requestSubject(capturedRequest.get())).isEqualTo(context)
        assertThat(capturedResponse.get().header(PrecomputedAssignmentsVerifier.RULES_REVISION_HEADER))
            .isEqualTo("v1.fixture")
        assertThat(
            capturedResponse.get().header(PrecomputedAssignmentsVerifier.AUTHORIZATION_POLICY_VERSION_HEADER)
        ).isNull()
    }

    @Test
    fun `M restore flags W verify() { signed and authorized production request scope }`(
        @Forgery fakeDatadogContext: DatadogContext
    ) {
        val context = EvaluationContext("subject-1", mapOf("country" to "US"))
        val envelope = protectedEnvelope(AssignmentProtection.SIGNED_AND_AUTHORIZED)
        val capturedRequest = AtomicReference<Request>()
        val testedVerifier = cacheVerifier(
            protection = AssignmentProtection.SIGNED_AND_AUTHORIZED,
            payloadVerifier = AssignmentPayloadVerifier { request, _, _ ->
                capturedRequest.set(request)
                envelope
            }
        )

        val flags = testedVerifier.verify(
            entry = protectedEntry(context, envelope),
            context = context,
            datadogContext = scopedDatadogContext(fakeDatadogContext)
        )

        assertThat(flags?.get("fixture")?.variationValue).isEqualTo("enabled")
        assertThat(capturedRequest.get().header("Authorization")).isEqualTo("Bearer header.payload.signature")
    }

    @Test
    fun `M reject flags W verify() { verified envelope metadata changed }`(
        @Forgery fakeDatadogContext: DatadogContext
    ) {
        val context = EvaluationContext("subject-1", emptyMap())
        val envelope = protectedEnvelope(AssignmentProtection.SIGNED)
        val testedVerifier = cacheVerifier(
            protection = AssignmentProtection.SIGNED,
            payloadVerifier = AssignmentPayloadVerifier { _, _, _ ->
                envelope.copy(rulesRevision = "attacker-controlled")
            }
        )

        val flags = testedVerifier.verify(
            entry = protectedEntry(context, envelope),
            context = context,
            datadogContext = scopedDatadogContext(fakeDatadogContext)
        )

        assertThat(flags).isNull()
    }

    @Test
    fun `M reject flags W verify() { persisted rules revision differs from verified response }`(
        @Forgery fakeDatadogContext: DatadogContext
    ) {
        val context = EvaluationContext("subject-1", emptyMap())
        val verifiedEnvelope = protectedEnvelope(AssignmentProtection.SIGNED)
        val persistedEnvelope = verifiedEnvelope.copy(rulesRevision = "v1.persisted-change")
        val testedVerifier = cacheVerifier(
            protection = AssignmentProtection.SIGNED,
            payloadVerifier = AssignmentPayloadVerifier { _, response, _ ->
                assertThat(response.header(PrecomputedAssignmentsVerifier.RULES_REVISION_HEADER))
                    .isEqualTo(persistedEnvelope.rulesRevision)
                verifiedEnvelope
            }
        )

        val flags = testedVerifier.verify(
            entry = protectedEntry(context, persistedEnvelope),
            context = context,
            datadogContext = scopedDatadogContext(fakeDatadogContext)
        )

        assertThat(flags).isNull()
    }

    private fun cacheVerifier(
        protection: AssignmentProtection,
        payloadVerifier: AssignmentPayloadVerifier
    ): DefaultProtectedAssignmentsCacheVerifier {
        val authorization = if (protection == AssignmentProtection.SIGNED_AND_AUTHORIZED) {
            AssignmentAuthorization("header.payload.signature", Date(Long.MAX_VALUE))
        } else {
            null
        }
        return DefaultProtectedAssignmentsCacheVerifier(
            requestFactory = PrecomputedAssignmentsRequestFactory(
                internalLogger = mockInternalLogger,
                customFlagEndpoint = ENDPOINT,
                authorizationStore = AssignmentAuthorizationStore(authorization),
                assignmentProtection = protection
            ),
            payloadVerifier = payloadVerifier,
            precomputeMapper = PrecomputeMapper(mockInternalLogger),
            internalLogger = mockInternalLogger
        )
    }

    private fun scopedDatadogContext(context: DatadogContext) = context.copy(
        clientToken = "client-token",
        env = "production",
        sdkVersion = "1.2.3",
        featuresContext = emptyMap()
    )

    private fun protectedEntry(
        context: EvaluationContext,
        envelope: ProtectedAssignmentEnvelope
    ) = FlagsStateEntry(
        evaluationContext = context,
        flags = emptyMap(),
        lastUpdateTimestamp = 0L,
        rawResponseBody = RAW_RESPONSE_BODY,
        protectedEnvelope = envelope
    )

    private fun protectedEnvelope(protection: AssignmentProtection) = ProtectedAssignmentEnvelope(
        protection = protection,
        requestNonce = "000102030405060708090a0b0c0d0e0f",
        responseStatus = 200,
        authorizationPolicyVersion = if (protection == AssignmentProtection.SIGNED_AND_AUTHORIZED) {
            "policy-v2"
        } else {
            null
        },
        rulesRevision = "v1.fixture",
        issuedAt = 1_789_096_800L,
        expiresAt = 1_789_097_100L,
        certificateId = "certificate-id",
        certificate = "certificate",
        signature = "signature"
    )

    private fun requestSubject(request: Request): EvaluationContext {
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        val subject = JSONObject(buffer.readUtf8())
            .getJSONObject("data")
            .getJSONObject("attributes")
            .getJSONObject("subject")
        val attributes = subject.getJSONObject("targeting_attributes")
        return EvaluationContext(
            subject.getString("targeting_key"),
            attributes.keys().asSequence().associateWith(attributes::getString)
        )
    }

    private companion object {
        const val ENDPOINT = "https://example.test/precompute-assignments"
        const val RAW_RESPONSE_BODY =
            "{\"data\":{\"id\":\"subject-1\",\"attributes\":{\"flags\":{\"fixture\":{" +
                "\"variationType\":\"string\",\"variationValue\":\"enabled\",\"doLog\":false," +
                "\"allocationKey\":\"allocation\",\"variationKey\":\"variation\"," +
                "\"extraLogging\":{},\"reason\":\"DEFAULT\"}}}}}"
    }
}
