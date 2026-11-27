/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.serialization

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Validates the dependent-flag fields of a precomputed response like the browser SDK does. Evaluation
 * doesn't read them, and encrypted dependency keys are checked for shape only, never decrypted.
 */
internal class DependentFlagsJson(attributes: JsonObject, private val obfuscated: Boolean) {
    private val encryptedKeys = (attributes.get("obfuscation") as? JsonObject)
        ?.let { it.get("dependencyKeyEncryption") as? JsonObject }
        ?.let { stringOrNull(it.get("scheme")) } == DEPENDENCY_KEY_ENCRYPTION_SCHEME

    /** Error results belong in `flagEvaluationErrors`, not in assignments. */
    fun isValidAssignment(flag: JsonObject): Boolean =
        stringOrNull(flag.get(REASON)) != ERROR && validDependencies(flag.get(DEPENDENCY_EVALUATIONS))

    fun isValidEvaluationError(value: JsonElement): Boolean {
        val error = value as? JsonObject
        return error != null && stringOrNull(error.get(REASON)) == ERROR &&
            stringOrNull(error.get(ERROR_CODE)) == "GENERAL" && validErrorMessage(error) &&
            validDependencies(error.get(DEPENDENCY_EVALUATIONS))
    }

    private fun validDependencies(value: JsonElement?): Boolean {
        if (value == null) return true
        val keys = mutableSetOf<String>()
        return value is JsonArray && value.all { evaluation ->
            val key = dependencyKey(evaluation)
            key != null && isDependencyKey(key) && keys.add(key)
        }
    }

    /** Returns the flag key of a valid dependency evaluation, or null. */
    private fun dependencyKey(value: JsonElement): String? {
        val evaluation = value as? JsonObject ?: return null
        val presence = evaluation.get(IS_PRESENT_IN_ASSIGNMENT)
        val valid = if (stringOrNull(evaluation.get(REASON)) == ERROR) {
            !stringOrNull(evaluation.get(ERROR_CODE)).isNullOrEmpty() && validErrorMessage(evaluation) &&
                presence == null
        } else {
            val validPresence = presence?.isJsonPrimitive == true && presence.asJsonPrimitive.isBoolean
            validPresence && listOf(REASON, ERROR_CODE, ERROR_MESSAGE).all { evaluation.get(it)?.isJsonNull ?: true }
        }
        return stringOrNull(evaluation.get("flagKey"))?.takeIf { valid && it.isNotEmpty() }
    }

    private fun isDependencyKey(key: String): Boolean = when {
        !obfuscated -> true
        // Without a supported key envelope, an obfuscated response accepts only empty dependency lists.
        !encryptedKeys -> false
        // Unpadded base64url: a length of 4n + 1 cannot encode whole bytes.
        else -> key.length % BASE64_BLOCK_LENGTH != 1 && key.matches(ENCRYPTED_KEY)
    }

    private fun validErrorMessage(json: JsonObject): Boolean =
        json.get(ERROR_MESSAGE)?.let { !stringOrNull(it).isNullOrEmpty() } ?: true

    private fun stringOrNull(value: JsonElement?): String? =
        if (value?.isJsonPrimitive == true && value.asJsonPrimitive.isString) value.asString else null

    private companion object {
        const val REASON = "reason"
        const val ERROR = "ERROR"
        const val ERROR_CODE = "errorCode"
        const val ERROR_MESSAGE = "errorMessage"
        const val DEPENDENCY_EVALUATIONS = "dependencyEvaluations"
        const val IS_PRESENT_IN_ASSIGNMENT = "isPresentInAssignment"
        const val DEPENDENCY_KEY_ENCRYPTION_SCHEME = "dependency-key-hkdf-sha256-aes-256-gcm-v1"
        const val BASE64_BLOCK_LENGTH = 4
        val ENCRYPTED_KEY = Regex("[A-Za-z0-9_-]{38,}")
    }
}
