/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.serialization

import com.datadog.android.flags.internal.model.Assignment
import com.datadog.android.flags.internal.model.DependencyFlagKeyEncryption
import com.datadog.android.flags.internal.model.FlagKeyObfuscation
import com.datadog.android.flags.internal.model.FlagsState
import com.datadog.android.flags.internal.model.PrecomputedDependencyEvaluation
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.model.PrecomputedFlagEvaluationError
import com.datadog.android.flags.internal.model.SplitFeature
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import org.json.JSONException
import org.json.JSONObject

/** Generated model adapters, legacy JSON conversions, and encoding validation. */
@Suppress(
    "TooManyFunctions",
    "StringLiteralDuplication",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "MagicNumber",
    "ThrowsCount",
    "UnsafeThirdPartyFunctionCall",
    "ThrowingInternalException"
) // Wire validation intentionally keeps every rejected union shape explicit and local.
internal object FlagsJson {
    fun readConsent(json: JsonObject): Boolean {
        val value = json.get(OBSERVE_FULL_EVALUATION_DATA)
        return value?.isJsonPrimitive == true && value.asJsonPrimitive.isBoolean && value.asBoolean
    }

    @Throws(JsonParseException::class)
    @Suppress("ThrowsCount") // Report each invalid assignment field.
    fun readAssignment(json: JsonObject): PrecomputedFlag {
        // Preserve JSONObject's string conversion, including JSON variants and explicit null.
        STRING_FIELDS.forEach { field ->
            val value = json.get(field) ?: throw JsonParseException("Missing assignment field: $field")
            json.addProperty(field, stringValue(value))
        }
        val doLog = json.get("doLog")
        val booleanValue = doLog?.takeIf { it.isJsonPrimitive }?.asString
        if (!booleanValue.equals("true", ignoreCase = true) && !booleanValue.equals("false", ignoreCase = true)) {
            throw JsonParseException("Invalid assignment doLog")
        }
        // Gson accepts the same boolean strings; reject other values before it coerces them to false.
        // Existing readers ignore null and nonnumeric serial IDs.
        val serialId = json.get("serialId")
        if (serialId?.isJsonPrimitive != true || !serialId.asJsonPrimitive.isNumber) json.remove("serialId")
        val features = SplitFeaturesJson.normalize(json.get(FEATURES))
        if (features == null) json.remove(FEATURES) else json.add(FEATURES, features)
        val dependencyEvaluations = readDependencyEvaluations(json.get(DEPENDENCY_EVALUATIONS))

        if (!json.has("extraLogging")) json.add("extraLogging", JsonObject())
        // An unsupported variationType stays in the flag, so that the flag resolves with PARSE_ERROR.
        val assignment = Assignment.fromJsonObject(json)

        @Suppress("UnsafeThirdPartyFunctionCall") // Normalize JSONObject failures for the response mapper.
        val extraLogging = try {
            JSONObject(assignment.extraLogging.toJson().toString())
        } catch (exception: JSONException) {
            throw JsonParseException("Invalid assignment extraLogging", exception)
        }
        return PrecomputedFlag(
            variationType = assignment.variationType,
            variationValue = assignment.variationValue,
            doLog = assignment.doLog,
            allocationKey = assignment.allocationKey,
            variationKey = assignment.variationKey,
            extraLogging = extraLogging,
            reason = assignment.reason,
            serialId = assignment.serialId,
            features = assignment.features.orEmpty().map { feature ->
                SplitFeature(feature.key, SplitFeaturesJson.value(feature.value), feature.destinations)
            },
            dependencyEvaluations = dependencyEvaluations
        )
    }

    fun writeAssignment(flag: PrecomputedFlag): Assignment = Assignment(
        variationType = flag.variationType,
        variationValue = flag.variationValue,
        doLog = flag.doLog,
        allocationKey = flag.allocationKey,
        variationKey = flag.variationKey,
        extraLogging = Assignment.ExtraLogging.fromJson(flag.extraLogging.toString()),
        reason = flag.reason,
        serialId = flag.serialId,
        features = flag.features.takeIf { it.isNotEmpty() }?.map { feature ->
            Assignment.Feature(feature.key, SplitFeaturesJson.modelValue(feature.value), feature.destinations)
        }
    )

    fun writeAssignmentJson(flag: PrecomputedFlag): JsonObject = writeAssignment(
        flag
    ).toJson().asJsonObject.also { json ->
        writeDependencyEvaluations(flag.dependencyEvaluations)?.let { json.add(DEPENDENCY_EVALUATIONS, it) }
    }

    @Throws(JsonParseException::class)
    fun readEvaluationError(json: JsonObject): PrecomputedFlagEvaluationError {
        val reason = json.get("reason")
        val errorCode = json.get("errorCode")
        val errorMessage = json.get("errorMessage")
        val invalidReason = reason?.isJsonPrimitive != true || !reason.asJsonPrimitive.isString ||
            reason.asString != "ERROR"
        val invalidErrorCode = errorCode?.isJsonPrimitive != true || !errorCode.asJsonPrimitive.isString ||
            errorCode.asString != "GENERAL"
        if (invalidReason || invalidErrorCode ||
            (
                errorMessage != null &&
                    (
                        errorMessage.isJsonPrimitive.not() || errorMessage.asJsonPrimitive.isString.not() ||
                            errorMessage.asString.isEmpty()
                        )
                )
        ) {
            throw JsonParseException("Invalid precomputed flag evaluation error")
        }
        return PrecomputedFlagEvaluationError(
            errorCode = errorCode.asString,
            errorMessage = errorMessage?.asString,
            dependencyEvaluations = readDependencyEvaluations(json.get(DEPENDENCY_EVALUATIONS))
        )
    }

    fun writeEvaluationError(error: PrecomputedFlagEvaluationError): JsonObject = JsonObject().apply {
        addProperty("reason", "ERROR")
        addProperty("errorCode", error.errorCode)
        error.errorMessage?.let { addProperty("errorMessage", it) }
        writeDependencyEvaluations(error.dependencyEvaluations)?.let { add(DEPENDENCY_EVALUATIONS, it) }
    }

    @Throws(JsonParseException::class)
    fun validateDependencyEncryption(
        flags: Collection<PrecomputedFlag>,
        errors: Collection<PrecomputedFlagEvaluationError>,
        obfuscation: FlagKeyObfuscation?
    ) {
        val dependencies = flags.flatMap { it.dependencyEvaluations } + errors.flatMap { it.dependencyEvaluations }
        if (dependencies.isNotEmpty() && obfuscation != null && !obfuscation.dependencyKeyEncryption) {
            throw JsonParseException("Unsupported dependency flag-key encryption scheme")
        }
        dependencies.forEach { dependency ->
            val key = dependency.flagKey
            if (obfuscation != null &&
                (
                    key.length < MIN_ENCRYPTED_DEPENDENCY_KEY_LENGTH || key.length % 4 == 1 ||
                        key.any { it !in BASE64_URL_ALPHABET }
                    )
            ) {
                throw JsonParseException("Invalid encrypted dependency flag key")
            }
        }
    }

    @Throws(JsonParseException::class)
    fun readState(json: JsonObject, obfuscation: FlagKeyObfuscation?): FlagsState {
        // Preserve old caches: ignore the unused map and accept JSONObject's context conversions.
        json.remove(if (obfuscation == null) "encodedFlags" else "flags")
        json.remove(if (obfuscation == null) "encodedFlagEvaluationErrors" else "flagEvaluationErrors")
        val context = objectValue(json.get("evaluationContext"))
        if (context.get("attributes") !is JsonObject) context.remove("attributes")
        context.get("targetingKey")?.let { context.addProperty("targetingKey", stringValue(it)) }
        if (json.get("lastUpdateTimestamp")?.isJsonPrimitive != true) {
            @Suppress("ThrowingInternalException") // Caught by the cache deserializer.
            throw JsonParseException("Invalid persisted flag timestamp")
        }
        json.addProperty(OBSERVE_FULL_EVALUATION_DATA, readConsent(json))
        return FlagsState.fromJsonObject(json)
    }

    @Throws(JsonParseException::class)
    @Suppress("ThrowsCount") // Report each invalid encoding field before generated readers can coerce it.
    fun readObfuscation(json: JsonObject): FlagKeyObfuscation? {
        val obfuscated = json.get("obfuscated")
        val isBoolean = obfuscated?.isJsonPrimitive == true && obfuscated.asJsonPrimitive.isBoolean
        val isPlaintext = obfuscated == null || (isBoolean && !obfuscated.asBoolean)
        if (isPlaintext && !json.has("obfuscation")) return null
        if (!isBoolean || !obfuscated.asBoolean) {
            @Suppress("ThrowingInternalException") // Caught by the response or cache deserializer.
            throw JsonParseException("Invalid flag-key obfuscation metadata")
        }
        val descriptor = objectValue(json.get("obfuscation"))
        // Generated string readers coerce primitives. The encoding contract requires JSON strings.
        listOf("scheme", "salt").forEach { field ->
            val value = descriptor.get(field)
            if (value?.isJsonPrimitive != true || !value.asJsonPrimitive.isString) {
                @Suppress("ThrowingInternalException") // Caught by the response or cache deserializer.
                throw JsonParseException("Invalid flag-key obfuscation $field")
            }
        }
        val encoding = FlagsState.Obfuscation.fromJsonObject(descriptor)
        if (encoding.scheme != FlagKeyObfuscation.SCHEME) {
            @Suppress("ThrowingInternalException") // Caught by the response or cache deserializer.
            throw JsonParseException("Unsupported flag-key obfuscation scheme")
        }
        val dependencyEncryption = descriptor.get("dependencyKeyEncryption")
        val supportedDependencyEncryption = if (dependencyEncryption == null) {
            false
        } else {
            val dependencyDescriptor = objectValue(dependencyEncryption)
            val scheme = dependencyDescriptor.get("scheme")
            scheme?.isJsonPrimitive == true && scheme.asJsonPrimitive.isString &&
                scheme.asString == DependencyFlagKeyEncryption.SCHEME
        }
        return FlagKeyObfuscation.fromSalt(encoding.salt, supportedDependencyEncryption)
    }

    @Throws(JsonParseException::class)
    fun validateKeys(keys: Iterator<String>) {
        keys.forEach {
            if (it.length != DIGEST_HEX_LENGTH ||
                it.any { character -> character !in '0'..'9' && character !in 'a'..'f' }
            ) {
                @Suppress("ThrowingInternalException") // Caught by the response or cache deserializer.
                throw JsonParseException("Invalid encoded flag key")
            }
        }
    }

    fun stringValue(value: JsonElement): String =
        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else value.toString()

    @Throws(JsonParseException::class)
    fun objectValue(value: Any?): JsonObject = (value as? JsonObject)
        ?: throw JsonParseException("Expected a JSON object")

    @Throws(JsonParseException::class)
    private fun readDependencyEvaluations(value: JsonElement?): List<PrecomputedDependencyEvaluation> {
        if (value == null) return emptyList()
        if (!value.isJsonArray) throw JsonParseException("Invalid dependency evaluations")
        val keys = mutableSetOf<String>()
        return value.asJsonArray.map { entry ->
            val json = objectValue(entry)
            val flagKey = json.get("flagKey")
            if (flagKey?.isJsonPrimitive != true || !flagKey.asJsonPrimitive.isString ||
                flagKey.asString.isEmpty() || !keys.add(flagKey.asString)
            ) {
                throw JsonParseException("Invalid dependency flag key")
            }
            val reason = json.get("reason")
            val errorCode = json.get("errorCode")
            val errorMessage = json.get("errorMessage")
            val isPresent = json.get("isPresentInAssignment")
            if (reason?.isJsonPrimitive == true && reason.asJsonPrimitive.isString && reason.asString == "ERROR") {
                if (errorCode?.isJsonPrimitive != true || !errorCode.asJsonPrimitive.isString ||
                    errorCode.asString.isEmpty() || isPresent != null ||
                    (
                        errorMessage != null &&
                            (
                                errorMessage.isJsonPrimitive.not() || errorMessage.asJsonPrimitive.isString.not() ||
                                    errorMessage.asString.isEmpty()
                                )
                        )
                ) {
                    throw JsonParseException("Invalid dependency evaluation error")
                }
                PrecomputedDependencyEvaluation.Error(flagKey.asString, errorCode.asString, errorMessage?.asString)
            } else {
                if (reason != null || errorCode != null || errorMessage != null ||
                    isPresent?.isJsonPrimitive != true || !isPresent.asJsonPrimitive.isBoolean
                ) {
                    throw JsonParseException("Invalid successful dependency evaluation")
                }
                PrecomputedDependencyEvaluation.Success(flagKey.asString, isPresent.asBoolean)
            }
        }
    }

    private fun writeDependencyEvaluations(
        dependencies: List<PrecomputedDependencyEvaluation>
    ): com.google.gson.JsonArray? = dependencies.takeIf { it.isNotEmpty() }?.let { evaluations ->
        com.google.gson.JsonArray().apply {
            evaluations.forEach { dependency ->
                add(
                    JsonObject().apply {
                        addProperty("flagKey", dependency.flagKey)
                        when (dependency) {
                            is PrecomputedDependencyEvaluation.Success ->
                                addProperty("isPresentInAssignment", dependency.isPresentInAssignment)
                            is PrecomputedDependencyEvaluation.Error -> {
                                addProperty("reason", "ERROR")
                                addProperty("errorCode", dependency.errorCode)
                                dependency.errorMessage?.let { addProperty("errorMessage", it) }
                            }
                        }
                    }
                )
            }
        }
    }

    private val STRING_FIELDS = listOf("variationType", "variationValue", "allocationKey", "variationKey", "reason")
    private const val DIGEST_HEX_LENGTH = 64
    private const val OBSERVE_FULL_EVALUATION_DATA = "observeFullEvaluationData"
    private const val FEATURES = "features"
    private const val DEPENDENCY_EVALUATIONS = "dependencyEvaluations"
    private const val MIN_ENCRYPTED_DEPENDENCY_KEY_LENGTH = 38
    private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
}
