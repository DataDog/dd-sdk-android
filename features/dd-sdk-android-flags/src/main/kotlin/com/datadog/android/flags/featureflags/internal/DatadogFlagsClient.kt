/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.featureflags.FlagsClient
import com.datadog.android.flags.featureflags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.internal.repository.FlagsRepository
import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.flags.internal.FlagsFeature
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.ResolutionDetails
import com.datadog.android.flags.internal.EventsProcessor
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

/**
 * Production implementation of [FlagsClient] that integrates with Datadog's flag evaluation system.
 *
 * This implementation fetches precomputed flag values from the local repository and handles
 * type conversion with appropriate fallback to default values.
 *
 * Thread safety: All resolve methods are thread-safe read operations with no synchronization
 * overhead, designed for high-frequency usage. The [setEvaluationContext] method is thread-safe
 * but triggers asynchronous background operations for fetching updated flag evaluations.
 *
 * @param featureSdkCore the SDK core for logging and internal operations
 * @param evaluationsManager manages flag evaluations and network requests
 * @param flagsRepository local storage for precomputed flag values
 * @param flagsConfiguration configuration for the flags feature
 * @param rumEvaluationLogger responsible for sending flag evaluations to RUM.
 * @param processor responsible for writing exposure batches to be sent to flags backend.
 */
internal class DatadogFlagsClient(
    private val featureSdkCore: FeatureSdkCore,
    private val evaluationsManager: EvaluationsManager,
    private val flagsRepository: FlagsRepository,
    private val flagsConfiguration: FlagsConfiguration,
    private val rumEvaluationLogger: RumEvaluationLogger,
    private val processor: EventsProcessor
) : FlagsClient {

    /**
     * Sets the evaluation context and triggers a background fetch of precomputed flags.
     *
     * This method converts the public [EvaluationContext] to an internal format,
     * validates the context data, and asynchronously fetches updated flag evaluations
     * from the Datadog service. If context validation fails, the operation is logged
     * and silently ignored.
     *
     * This method is thread-safe and non-blocking. Flag updates will be available
     * for subsequent resolve calls once the background operation completes.
     *
     * @param context The evaluation context containing targeting key and attributes.
     * Must contain a valid targeting key; invalid contexts are logged and ignored.
     */
    override fun setEvaluationContext(context: EvaluationContext) {
        if (context.targetingKey.isBlank()) {
            featureSdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { "Invalid evaluation context: targeting key cannot be blank or whitespace-only" }
            )
            return
        }

        evaluationsManager.updateEvaluationsForContext(context)
    }

    /**
     * Resolves a boolean flag value from the local repository.
     *
     * This method performs boolean parsing of the stored flag value.
     * Only "true" and "false" (case-insensitive) string values will be converted;
     * all other values result in the default being returned.
     *
     * This method is thread-safe and performs no network operations.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The boolean value of the flag, or the default value if unavailable or unparseable.
     */
    override fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean =
        getValue(flagKey, defaultValue) { it.lowercase(locale = Locale.US).toBooleanStrictOrNull() }

    /**
     * Resolves a string flag value from the local repository.
     *
     * This method returns the stored string value as-is without any type conversion.
     * This method is thread-safe and performs no network operations.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be retrieved.
     * @return The string value of the flag, or the default value if unavailable.
     */
    override fun resolveStringValue(flagKey: String, defaultValue: String): String =
        getValue(flagKey, defaultValue) { it }

    /**
     * Resolves an integer flag value from the local repository.
     *
     * This method attempts to parse the stored string value as an integer.
     * If parsing fails, the default value is returned. This method is thread-safe
     * and performs no network operations.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The integer value of the flag, or the default value if unavailable or unparseable.
     */
    override fun resolveIntValue(flagKey: String, defaultValue: Int): Int =
        getValue(flagKey, defaultValue) { it.toIntOrNull() }

    /**
     * Resolves a long integer flag value from the local repository.
     *
     * This method attempts to parse the stored string value as a long integer.
     * Useful for values that may exceed Int.MAX_VALUE such as timestamps,
     * large identifiers, or counters.
     *
     * If parsing fails, the default value is returned. This method is thread-safe
     * and performs no network operations.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The long value of the flag, or the default value if unavailable or unparseable.
     */
    override fun resolveLongValue(flagKey: String, defaultValue: Long): Long =
        getValue(flagKey, defaultValue) { it.toLongOrNull() }

    /**
     * Resolves a double flag value from the local repository.
     *
     * This method attempts to parse the stored string value as a double.
     * If parsing fails, the default value is returned. This method is thread-safe
     * and performs no network operations.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The double value of the flag, or the default value if unavailable or unparseable.
     */
    override fun resolveDoubleValue(flagKey: String, defaultValue: Double): Double =
        getValue(flagKey, defaultValue) { it.toDoubleOrNull() }

    /**
     * Resolves a structured flag value from the local repository.
     *
     * This method attempts to parse the stored string value as a JSON object.
     * If parsing fails, an error is logged and the default value is returned.
     * This method is thread-safe and performs no network operations.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The JSON object value of the flag, or the default value if unavailable or unparseable.
     */
    override fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject =
        getValue(flagKey, defaultValue) {
            try {
                JSONObject(it)
            } catch (e: JSONException) {
                featureSdkCore.internalLogger.log(
                    level = InternalLogger.Level.ERROR,
                    target = InternalLogger.Target.USER,
                    messageBuilder = { ERROR_PARSING_JSON.format(Locale.US, flagKey) },
                    throwable = e
                )
                defaultValue
            }
        }

    private fun <T> getValue(flagKey: String, defaultValue: T, converter: (String) -> T?): T {
        val flagAndContext = flagsRepository.getPrecomputedFlagWithContext(flagKey)
        if (flagAndContext != null) {
            val (precomputedFlag, context) = flagAndContext
            val convertedValue = precomputedFlag.variationValue.let(converter)

            if (convertedValue == null) {
                return defaultValue
            }

        val evaluationContext = flagsRepository.getEvaluationContext()
        if (evaluationContext == null) {
            /**
             * this might happen if a previous session saved precomputed flags and a new session did not provide a valid context
             */
            featureSdkCore.internalLogger.log(
                target = InternalLogger.Target.MAINTAINER,
                level = InternalLogger.Level.ERROR,
                messageBuilder = { ERROR_NO_EVALUATION_CONTEXT }
            )
        } else if (flagsConfiguration.trackExposures) {
            writeExposureEvent(flagKey, precomputedFlag, evaluationContext)
        }

            if (flagsConfiguration.rumIntegrationEnabled) {
                logEvaluation(
                    key = flagKey,
                    value = convertedValue
                )
            }
            return convertedValue
        }

        return defaultValue
    }

    private fun writeExposureEvent(name: String, data: PrecomputedFlag, context: EvaluationContext) {
        processor.processEvent(
            flagName = name,
            context = context,
            data = data
        )
    }

    private fun logEvaluation(
        key: String,
        value: Any
    ) {
        rumEvaluationLogger.logEvaluation(
            flagKey = key,
            value = value
        )
    }

    /**
     * Resolves a flag value with detailed resolution information.
     *
     * This is the core resolution method that centralizes all flag resolution logic:
     * 1. Retrieves the precomputed flag from the repository
     * 2. Returns FLAG_NOT_FOUND error if the flag doesn't exist
     * 3. Parses the flag value based on the target type (T)
     * 4. Returns TYPE_MISMATCH/PARSE_ERROR if parsing fails
     * 5. Returns success with variant, reason, and metadata if parsing succeeds
     *
     * Type-specific parsing:
     * - Boolean: Strict parsing (only "true"/"false")
     * - String: No conversion needed
     * - Int: Parse to integer
     * - Long: Parse to long integer
     * - Double: Parse to double
     * - JSONObject: Parse JSON with error logging
     *
     * @param T The type of the flag value (Boolean, String, Int, Long, Double, or JSONObject).
     * @param flagKey The name of the flag to query.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return [ResolutionDetails] with either the parsed value and metadata, or an error.
     */
    override fun <T> resolve(flagKey: String, defaultValue: T): ResolutionDetails<T> {
        val flagAndContext = flagsRepository.getPrecomputedFlagWithContext(flagKey)

        if (flagAndContext == null) {
            return createErrorResolution(
                flagKey = flagKey,
                defaultValue = defaultValue,
                errorCode = ErrorCode.FLAG_NOT_FOUND,
                errorMessage = "Flag not found"
            )
        }

        val (precomputedFlag, context) = flagAndContext
        val rawValue = precomputedFlag.variationValue

        // Parse based on target type (inferred from defaultValue)
        @Suppress("UNCHECKED_CAST")
        val parsedValue: T? = when (defaultValue) {
            is Boolean -> rawValue.toBooleanStrictOrNull() as? T
            is String -> rawValue as? T
            is Int -> rawValue.toIntOrNull() as? T
            is Long -> rawValue.toLongOrNull() as? T
            is Double -> rawValue.toDoubleOrNull() as? T
            is JSONObject -> {
                try {
                    JSONObject(rawValue) as? T
                } catch (e: JSONException) {
                    featureSdkCore.internalLogger.log(
                        level = InternalLogger.Level.ERROR,
                        target = InternalLogger.Target.USER,
                        messageBuilder = { "Failed to parse JSON for key: $flagKey" },
                        throwable = e
                    )
                    // Return null to trigger PARSE_ERROR below
                    null
                }
            }
            else -> null
        }

        return if (parsedValue != null) {
            // Track exposure event for successfully resolved flags
            if (flagsConfiguration.trackExposures) {
                writeExposureEvent(flagKey, precomputedFlag, context)
            }
            createSuccessResolution(precomputedFlag, parsedValue)
        } else {
            // Determine appropriate error code
            val errorCode = if (defaultValue is JSONObject) {
                ErrorCode.PARSE_ERROR
            } else {
                ErrorCode.TYPE_MISMATCH
            }

            val errorMessage = if (defaultValue is JSONObject) {
                "Flag value cannot be parsed as JSON"
            } else {
                "Flag value '$rawValue' cannot be parsed to the requested type"
            }

            createErrorResolution(
                flagKey = flagKey,
                defaultValue = defaultValue,
                errorCode = errorCode,
                errorMessage = errorMessage
            )
        }
    }

    private fun writeExposureEvent(flagKey: String, data: PrecomputedFlag, context: EvaluationContext?) {
        if (context != null) {
            featureSdkCore
                .getFeature(FLAGS_FEATURE_NAME)
                ?.unwrap<FlagsFeature>()?.processor?.processEvent(
                    flagName = flagKey,
                    context = context,
                    data = data
                )
        }
    }

    private companion object {
        private const val ERROR_NO_EVALUATION_CONTEXT =
            "No evaluation context found, exposures cannot be sent to the flags backend. " +
                "Please call client.setContext with a valid context."
        private const val ERROR_PARSING_JSON = "Failed to parse JSON for key: %s"
    }

// region Helper Methods

    /**
     * Creates a successful resolution details from a precomputed flag.
     *
     * This helper method maps the PrecomputedFlag data to a ResolutionDetails structure,
     * extracting the variant, reason, and metadata fields according to the OpenFeature spec.
     *
     * @param T The type of the flag value.
     * @param precomputedFlag The precomputed flag data from the repository.
     * @param value The typed value after successful parsing.
     * @return [ResolutionDetails] with the resolved value and associated metadata.
     */
    private fun <T> createSuccessResolution(precomputedFlag: PrecomputedFlag, value: T): ResolutionDetails<T> =
        ResolutionDetails(
            value = value,
            variant = precomputedFlag.variationKey.takeIf { it.isNotBlank() },
            reason = precomputedFlag.reason.takeIf { it.isNotBlank() },
            errorCode = null,
            errorMessage = null,
            flagMetadata = extractMetadata(precomputedFlag.extraLogging)
        )

    /**
     * Creates an error resolution details with the default value.
     *
     * This helper method creates a ResolutionDetails structure for error scenarios,
     * populating the error code, error message, and reason according to OpenFeature spec.
     *
     * @param T The type of the flag value.
     * @param flagKey The flag key that was attempted to be resolved.
     * @param defaultValue The default value to return.
     * @param errorCode The error code indicating the type of failure.
     * @param errorMessage A human-readable description of the error.
     * @return [ResolutionDetails] with the default value and error information.
     */
    private fun <T> createErrorResolution(
        flagKey: String,
        defaultValue: T,
        errorCode: ErrorCode,
        errorMessage: String
    ): ResolutionDetails<T> = ResolutionDetails(
        value = defaultValue,
        variant = null,
        reason = "ERROR",
        errorCode = errorCode,
        errorMessage = "Flag '$flagKey': $errorMessage",
        flagMetadata = null
    )

    /**
     * Extracts metadata from a JSONObject into an immutable Map.
     *
     * This helper method converts the extraLogging JSONObject from PrecomputedFlag
     * into a Map<String, Any> suitable for the ResolutionDetails metadata field.
     * Only string, number, and boolean values are included; other types are omitted.
     * The returned map is immutable to prevent external modifications.
     *
     * @param extraLogging The JSONObject containing additional flag metadata.
     * @return An immutable map of metadata, or null if the JSONObject is empty.
     */
    private fun extractMetadata(extraLogging: JSONObject): Map<String, Any>? {
        if (extraLogging.length() == 0) {
            return null
        }

        val metadata = mutableMapOf<String, Any>()
        extraLogging.keys().forEach { key ->
            val value = extraLogging.opt(key)
            // Only include primitive types as per OpenFeature spec
            when (value) {
                is String, is Number, is Boolean -> metadata[key] = value
                // Skip other types (JSONObject, JSONArray, null)
            }
        }

        return metadata.takeIf { it.isNotEmpty() }?.toMap()
    }

// endregion
}
