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
import com.datadog.android.flags.internal.EventsProcessor
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.ResolutionDetails
import org.json.JSONObject

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
 * @param valueConverter handles type conversion for flag values.
 */
internal class DatadogFlagsClient(
    private val featureSdkCore: FeatureSdkCore,
    private val evaluationsManager: EvaluationsManager,
    private val flagsRepository: FlagsRepository,
    private val flagsConfiguration: FlagsConfiguration,
    private val rumEvaluationLogger: RumEvaluationLogger,
    private val processor: EventsProcessor,
    private val valueConverter: FlagValueConverter
) : FlagsClient {

    // region FlagsClient

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
     * Resolves a boolean flag value.
     *
     * If the flag cannot be found, is not a boolean, or an error occurs, the default value is returned.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be found or resolved for any reason. Cannot be null.
     * @return The boolean value of the flag, or the default value if the flag cannot be resolved for any reason.
     */
    override fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean =
        resolveTyped(resolveInternal(flagKey, defaultValue))

    /**
     * Resolves a string flag value.
     *
     * If the flag cannot be found, is not a string, or an error occurs, the default value is returned.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be found resolved for any reason. Cannot be null.
     * @return The string value of the flag, or the default value if the flag cannot be resolved for any reason.
     */
    override fun resolveStringValue(flagKey: String, defaultValue: String): String =
        resolveTyped(resolveInternal(flagKey, defaultValue))

    /**
     * Resolves an integer flag value.
     *
     * If the flag cannot be found, is not an integer, or an error occurs, the default value is returned.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be found or resolved for any reason. Cannot be null.
     * @return The integer value of the flag, or the default value if the flag cannot be resolved for any reason.
     */
    override fun resolveIntValue(flagKey: String, defaultValue: Int): Int =
        resolveTyped(resolveInternal(flagKey, defaultValue))

    /**
     * Resolves a long integer flag value.
     *
     * If the flag cannot be found, is not a long integer, or an error occurs, the default value is returned.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be found or resolved for any reason. Cannot be null.
     * @return The long value of the flag, or the default value if the flag cannot be resolved for any reason.
     */
    override fun resolveLongValue(flagKey: String, defaultValue: Long): Long =
        resolveTyped(resolveInternal(flagKey, defaultValue))

    /**
     * Resolves a double flag value.
     *
     * If the flag cannot be found, is not a double, or an error occurs, the default value is returned.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be found or resolved for any reason. Cannot be null.
     * @return The double value of the flag, or the default value if the flag cannot be resolved for any reason.
     */
    override fun resolveDoubleValue(flagKey: String, defaultValue: Double): Double =
        resolveTyped(resolveInternal(flagKey, defaultValue))

    /**
     * Resolves a structured flag value.
     *
     * If the flag cannot be found or an error occurs, the default value is returned.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be found or resolved for any reason. Cannot be null.
     * @return The JSON object value of the flag, or the default value if the flag cannot be resolved for any reason.
     */
    override fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject =
        resolveTyped(resolveInternal(flagKey, defaultValue))

    private fun writeExposureEvent(name: String, data: PrecomputedFlag, context: EvaluationContext) {
        processor.processEvent(
            flagName = name,
            context = context,
            data = data
        )
    }

    private fun logEvaluation(key: String, value: Any) {
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
        val resolution = resolveInternal(flagKey, defaultValue)

        return when (resolution) {
            is InternalResolution.Success -> {
                if (flagsConfiguration.trackExposures) {
                    writeExposureEvent(flagKey, resolution.flag, resolution.context)
                }
                createSuccessResolution(resolution.flag, resolution.value)
            }
            is InternalResolution.Error -> {
                createErrorResolution(
                    flagKey = flagKey,
                    defaultValue = resolution.defaultValue,
                    errorCode = resolution.errorCode,
                    errorMessage = resolution.errorMessage
                )
            }
        }
    }

    // endregion

    // region InternalResolution

    private sealed class InternalResolution<T> {
        abstract val flagKey: String

        data class Success<T>(
            override val flagKey: String,
            val value: T,
            val flag: PrecomputedFlag,
            val context: EvaluationContext
        ) : InternalResolution<T>()

        data class Error<T>(
            override val flagKey: String,
            val defaultValue: T,
            val errorCode: ErrorCode,
            val errorMessage: String
        ) : InternalResolution<T>()
    }

    /**
     * Core resolution method that unifies all flag resolution logic.
     *
     * This pure function (no side effects) is the single source of truth for:
     * - Fetching flags from the repository
     * - Validating type compatibility via [FlagValueConverter]
     * - Parsing flag values using the [FlagValueConverter]
     *
     * @param T The type of the flag value to resolve
     * @param flagKey The flag key to resolve
     * @param defaultValue The default value (also determines expected type)
     * @return [InternalResolution.Success] with parsed value and metadata if resolution succeeded,
     *         [InternalResolution.Error] with default value and error details otherwise
     */
    @Suppress("ReturnCount") // Early returns improve readability by avoiding nested conditionals
    private fun <T> resolveInternal(
        flagKey: String,
        defaultValue: T
    ): InternalResolution<T> {
        val flagAndContext = flagsRepository.getPrecomputedFlagWithContext(flagKey)
        if (flagAndContext == null) {
            return InternalResolution.Error(
                flagKey = flagKey,
                defaultValue = defaultValue,
                errorCode = ErrorCode.FLAG_NOT_FOUND,
                errorMessage = "Flag not found"
            )
        }

        val (flag, context) = flagAndContext

        val parsedValue = valueConverter.convert(
            variationValue = flag.variationValue,
            variationType = flag.variationType,
            defaultValue = defaultValue
        )

        if (parsedValue == null) {
            // Determine the error type based on type compatibility
            val errorCode: ErrorCode
            val errorMessage: String

            // Check if the error was due to type mismatch or parse error
            if (!valueConverter.isTypeCompatible(flag.variationType, defaultValue)) {
                errorCode = ErrorCode.TYPE_MISMATCH
                errorMessage =
                    "Flag has type '${flag.variationType}' but ${valueConverter.getTypeName(
                        defaultValue
                    )} was requested"
            } else {
                errorCode = ErrorCode.PARSE_ERROR
                errorMessage =
                    "Failed to parse value '${flag.variationValue}' as ${valueConverter.getTypeName(defaultValue)}"

                // Log parse errors for JSONObject types (which may fail due to malformed JSON)
                if (defaultValue is JSONObject) {
                    featureSdkCore.internalLogger.log(
                        level = InternalLogger.Level.ERROR,
                        target = InternalLogger.Target.USER,
                        messageBuilder = { "Failed to parse JSON for key: $flagKey" },
                        throwable = null
                    )
                }
            }

            return InternalResolution.Error(
                flagKey = flagKey,
                defaultValue = defaultValue,
                errorCode = errorCode,
                errorMessage = errorMessage
            )
        }

        return InternalResolution.Success(
            flagKey = flagKey,
            value = parsedValue,
            flag = flag,
            context = context
        )
    }

    // endregion

    // region Type Checking

    /**
     * Typed resolution layer that handles side effects for convenience methods.
     *
     * This helper method acts as a middle layer to the public API, handling:
     * - Exposure event tracking on successful resolution (via [writeExposureEvent])
     * - Type mismatch warning logs for developer feedback (via [InternalLogger])
     * - Returning the typed value on success or the default value on error
     *
     * @param T The type of the flag value
     * @param resolution The [InternalResolution] result from [resolveInternal]
     * @return The resolved value from [InternalResolution.Success.value] on success,
     *         or [InternalResolution.Error.defaultValue] on error
     */
    private fun <T> resolveTyped(resolution: InternalResolution<T>): T = when (resolution) {
        is InternalResolution.Success -> {
            if (flagsConfiguration.trackExposures) {
                writeExposureEvent(resolution.flagKey, resolution.flag, resolution.context)
            }

            if (flagsConfiguration.rumIntegrationEnabled) {
                logEvaluation(
                    key = resolution.flagKey,
                    value = resolution.value as Any
                )
            }

            resolution.value
        }
        is InternalResolution.Error -> {
            // Only log type mismatches as warnings to help developers identify configuration issues.
            // Other errors (FLAG_NOT_FOUND, PARSE_ERROR) are expected in normal operation.
            if (resolution.errorCode == ErrorCode.TYPE_MISMATCH) {
                featureSdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { "Flag '${resolution.flagKey}': ${resolution.errorMessage}" }
                )
            }
            resolution.defaultValue
        }
    }

    // endregion

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
