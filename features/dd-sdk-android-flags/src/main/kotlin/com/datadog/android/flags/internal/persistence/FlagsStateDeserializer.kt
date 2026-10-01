/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.flags.AssignmentProtection
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.JsonKeys
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsVerifier
import com.datadog.android.flags.internal.net.ProtectedAssignmentEnvelope
import com.datadog.android.flags.model.EvaluationContext
import org.json.JSONException
import org.json.JSONObject

internal class FlagsStateDeserializer(private val internalLogger: InternalLogger) :
    Deserializer<String, FlagsStateEntry> {

    @Suppress(
        "RequireInternal",
        "TooGenericExceptionCaught",
        "UnsafeThirdPartyFunctionCall"
    ) // Persisted state is untrusted. Any failure rejects the complete record.
    override fun deserialize(model: String): FlagsStateEntry? = try {
        require(model.length <= MAX_PERSISTED_STATE_CHARACTERS) { "Persisted flag state is too large" }
        val json = JSONObject(model)

        @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
        val contextJson = json.getJSONObject(JsonKeys.EVALUATION_CONTEXT.value)
        val evaluationContext = deserializeEvaluationContext(contextJson)

        @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
        val flagsJson = json.getJSONObject(JsonKeys.FLAGS.value)
        val flags = deserializeFlags(flagsJson)

        @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
        val timestamp = json.getLong(JsonKeys.LAST_UPDATE_TIMESTAMP.value)
        val rawResponseBody = json.optString(JsonKeys.RAW_RESPONSE_BODY.value).takeIf { it.isNotEmpty() }
        require(
            rawResponseBody == null ||
                rawResponseBody.toByteArray().size <= PrecomputedAssignmentsVerifier.MAX_RESPONSE_BODY_BYTES
        ) {
            "Persisted protected assignment response is too large"
        }
        val protectedEnvelope = json.optJSONObject(JsonKeys.PROTECTED_ENVELOPE.value)?.let(::deserializeEnvelope)
        require((rawResponseBody == null) == (protectedEnvelope == null)) {
            "Persisted protected assignment state is incomplete"
        }

        FlagsStateEntry(
            evaluationContext = evaluationContext,
            flags = flags,
            lastUpdateTimestamp = timestamp,
            rawResponseBody = rawResponseBody,
            protectedEnvelope = protectedEnvelope
        )
    } catch (e: Exception) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Failed to deserialize FlagsStateEntry from JSON" },
            e
        )

        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.TELEMETRY,
            messageBuilder = { "Failed to parse persisted flag state" },
            throwable = e,
            onlyOnce = true
        )

        null
    }

    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun deserializeEnvelope(json: JSONObject): ProtectedAssignmentEnvelope = ProtectedAssignmentEnvelope(
        protection = AssignmentProtection.valueOf(json.getString(PROTECTION)),
        requestNonce = json.getString(REQUEST_NONCE),
        responseStatus = json.getInt(RESPONSE_STATUS),
        authorizationPolicyVersion = json.optString(AUTHORIZATION_POLICY_VERSION).takeIf { it.isNotEmpty() },
        rulesRevision = json.getString(RULES_REVISION),
        issuedAt = json.getLong(ISSUED_AT),
        expiresAt = json.getLong(EXPIRES_AT),
        certificateId = json.getString(CERTIFICATE_ID),
        certificate = json.getString(CERTIFICATE),
        signature = json.getString(SIGNATURE)
    )

    @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
    private fun deserializeEvaluationContext(contextJson: JSONObject): EvaluationContext {
        val targetingKey = contextJson.getString(JsonKeys.TARGETING_KEY.value)

        val attributes = try {
            deserializeAttributes(contextJson.getJSONObject(JsonKeys.ATTRIBUTES.value))
        } catch (_: JSONException) {
            emptyMap()
        }

        return EvaluationContext(targetingKey, attributes)
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
    private fun deserializeAttributes(attributesJson: JSONObject): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        val keys = attributesJson.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val value = attributesJson.get(key).toString()
            attributes[key] = value
        }

        return attributes
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
    private fun deserializeFlags(flagsJson: JSONObject): Map<String, PrecomputedFlag> {
        val flags = mutableMapOf<String, PrecomputedFlag>()
        val keys = flagsJson.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val flagJson = flagsJson.getJSONObject(key)
            val flag = deserializePrecomputedFlag(flagJson)
            if (flag != null) {
                flags[key] = flag
            }
        }

        return flags
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
    private fun deserializePrecomputedFlag(flagJson: JSONObject): PrecomputedFlag? = try {
        PrecomputedFlag(
            variationType = flagJson.getString(JsonKeys.VARIATION_TYPE.value),
            variationValue = flagJson.getString(JsonKeys.VARIATION_VALUE.value),
            doLog = flagJson.getBoolean(JsonKeys.DO_LOG.value),
            allocationKey = flagJson.getString(JsonKeys.ALLOCATION_KEY.value),
            variationKey = flagJson.getString(JsonKeys.VARIATION_KEY.value),
            extraLogging = flagJson.getJSONObject(JsonKeys.EXTRA_LOGGING.value),
            reason = flagJson.getString(JsonKeys.REASON.value),
            serialId = (flagJson.opt(JsonKeys.SERIAL_ID.value) as? Number)?.toLong()
        )
    } catch (e: JSONException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Failed to deserialize precomputed flag, skipping" },
            e
        )
        null
    }

    private companion object {
        const val MAX_PERSISTED_STATE_CHARACTERS = 3 * 1_024 * 1_024
        const val PROTECTION = "protection"
        const val REQUEST_NONCE = "requestNonce"
        const val RESPONSE_STATUS = "responseStatus"
        const val AUTHORIZATION_POLICY_VERSION = "authorizationPolicyVersion"
        const val RULES_REVISION = "rulesRevision"
        const val ISSUED_AT = "issuedAt"
        const val EXPIRES_AT = "expiresAt"
        const val CERTIFICATE_ID = "certificateId"
        const val CERTIFICATE = "certificate"
        const val SIGNATURE = "signature"
    }
}
