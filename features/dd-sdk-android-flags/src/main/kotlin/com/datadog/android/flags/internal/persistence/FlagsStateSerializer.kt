/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.JsonKeys
import com.datadog.android.flags.internal.model.PrecomputedFlag
import org.json.JSONException
import org.json.JSONObject

/**
 * Serializer for converting FlagsStateEntry objects to JSON strings for datastore persistence.
 */
internal class FlagsStateSerializer(private val internalLogger: InternalLogger) : Serializer<FlagsStateEntry> {

    @Suppress("TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall") // JSON failures return no persisted state.
    override fun serialize(model: FlagsStateEntry): String = try {
        val json = JSONObject()

        val contextJson = JSONObject().apply {
            put(JsonKeys.TARGETING_KEY.value, model.evaluationContext.targetingKey)
            put(JsonKeys.ATTRIBUTES.value, serializeAttributes(model.evaluationContext.attributes))
        }
        json.put(JsonKeys.EVALUATION_CONTEXT.value, contextJson)

        val flagsJson = JSONObject()
        model.flags.forEach { (key, flag) ->
            flagsJson.put(key, serializePrecomputedFlag(flag))
        }
        json.put(JsonKeys.FLAGS.value, flagsJson)

        json.put(JsonKeys.LAST_UPDATE_TIMESTAMP.value, model.lastUpdateTimestamp)

        val protectedEnvelope = model.protectedEnvelope
        val rawResponseBody = model.rawResponseBody
        if (protectedEnvelope != null && rawResponseBody != null) {
            json.put(JsonKeys.RAW_RESPONSE_BODY.value, rawResponseBody)
            json.put(
                JsonKeys.PROTECTED_ENVELOPE.value,
                JSONObject()
                    .put(PROTECTION, protectedEnvelope.protection.name)
                    .put(REQUEST_NONCE, protectedEnvelope.requestNonce)
                    .put(RESPONSE_STATUS, protectedEnvelope.responseStatus)
                    .put(AUTHORIZATION_POLICY_VERSION, protectedEnvelope.authorizationPolicyVersion)
                    .put(RULES_REVISION, protectedEnvelope.rulesRevision)
                    .put(ISSUED_AT, protectedEnvelope.issuedAt)
                    .put(EXPIRES_AT, protectedEnvelope.expiresAt)
                    .put(CERTIFICATE_ID, protectedEnvelope.certificateId)
                    .put(CERTIFICATE, protectedEnvelope.certificate)
                    .put(SIGNATURE, protectedEnvelope.signature)
            )
        }

        json.toString()
    } catch (e: JSONException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Failed to serialize FlagsStateEntry to JSON" },
            e
        )
        ""
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
    private fun serializeAttributes(attributes: Map<String, Any>): JSONObject {
        val attributesJson = JSONObject()
        attributes.forEach { (key, value) ->
            attributesJson.put(key, value)
        }
        return attributesJson
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
    private fun serializePrecomputedFlag(flag: PrecomputedFlag): JSONObject = JSONObject().apply {
        put(JsonKeys.VARIATION_TYPE.value, flag.variationType)
        put(JsonKeys.VARIATION_VALUE.value, flag.variationValue)
        put(JsonKeys.DO_LOG.value, flag.doLog)
        put(JsonKeys.ALLOCATION_KEY.value, flag.allocationKey)
        put(JsonKeys.VARIATION_KEY.value, flag.variationKey)
        put(JsonKeys.EXTRA_LOGGING.value, flag.extraLogging)
        put(JsonKeys.REASON.value, flag.reason)
        flag.serialId?.let { put(JsonKeys.SERIAL_ID.value, it) }
    }

    private companion object {
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
