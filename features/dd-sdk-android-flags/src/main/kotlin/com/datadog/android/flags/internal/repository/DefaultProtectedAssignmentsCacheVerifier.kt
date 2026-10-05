/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.net.AssignmentPayloadVerifier
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsRequestFactory
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsVerifier
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import okhttp3.Protocol
import okhttp3.Response
import java.nio.charset.StandardCharsets

@Suppress(
    "RequireInternal",
    "UnsafeThirdPartyFunctionCall"
) // Cache verification catches all failures and returns no assignments.
internal class DefaultProtectedAssignmentsCacheVerifier(
    private val requestFactory: PrecomputedAssignmentsRequestFactory,
    private val payloadVerifier: AssignmentPayloadVerifier,
    private val precomputeMapper: PrecomputeMapper,
    private val internalLogger: InternalLogger
) : ProtectedAssignmentsCacheVerifier {

    @Suppress("TooGenericExceptionCaught")
    override fun verify(
        entry: FlagsStateEntry,
        context: EvaluationContext,
        datadogContext: DatadogContext
    ): Map<String, PrecomputedFlag>? = try {
        val envelope = requireNotNull(entry.protectedEnvelope)
        val responseBody = requireNotNull(entry.rawResponseBody).toByteArray(StandardCharsets.UTF_8)
        val request = requireNotNull(
            requestFactory.create(
                context = context,
                datadogContext = datadogContext,
                nonceOverride = envelope.requestNonce
            )
        )
        require(envelope.responseStatus in SUCCESS_STATUS_RANGE) {
            "Persisted protected assignment status is not successful"
        }
        val responseBuilder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(envelope.responseStatus)
            .message("Persisted protected assignment")
            .header(
                PrecomputedAssignmentsVerifier.SIGNATURE_VERSION_HEADER,
                PrecomputedAssignmentsVerifier.SIGNATURE_VERSION
            )
            .header(PrecomputedAssignmentsVerifier.RULES_REVISION_HEADER, envelope.rulesRevision)
            .header(PrecomputedAssignmentsVerifier.ISSUED_AT_HEADER, envelope.issuedAt.toString())
            .header(PrecomputedAssignmentsVerifier.EXPIRES_AT_HEADER, envelope.expiresAt.toString())
            .header(PrecomputedAssignmentsVerifier.CERTIFICATE_ID_HEADER, envelope.certificateId)
            .header(PrecomputedAssignmentsVerifier.CERTIFICATE_HEADER, envelope.certificate)
            .header(PrecomputedAssignmentsVerifier.SIGNATURE_HEADER, envelope.signature)
        envelope.authorizationPolicyVersion?.let {
            responseBuilder.header(PrecomputedAssignmentsVerifier.AUTHORIZATION_POLICY_VERSION_HEADER, it)
        }

        val verifiedEnvelope = payloadVerifier.verify(request, responseBuilder.build(), responseBody)
        require(verifiedEnvelope == envelope) { "Persisted protected assignment scope changed" }
        precomputeMapper.map(entry.rawResponseBody)
    } catch (e: Exception) {
        internalLogger.log(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { "Rejected persisted protected flag assignments" },
            throwable = e,
            onlyOnce = true
        )
        null
    }

    private companion object {
        val SUCCESS_STATUS_RANGE = 200..299
    }
}
