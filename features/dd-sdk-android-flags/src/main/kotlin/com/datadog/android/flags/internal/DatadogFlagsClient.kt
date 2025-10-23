/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.repository.FlagsRepository
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ResolutionDetails
import com.datadog.android.flags.model.ResolutionReason
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
 */
internal class DatadogFlagsClient(
    private val featureSdkCore: FeatureSdkCore,
    private val evaluationsManager: EvaluationsManager,
    private val flagsRepository: FlagsRepository,
    private val flagsConfiguration: FlagsConfiguration,
    private val rumEvaluationLogger: RumEvaluationLogger,
    private val processor: EventsProcessor
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
        resolveValue(flagKey, defaultValue)

    /**
     * Resolves a string flag value.
     *
     * If the flag cannot be found, is not a string, or an error occurs, the default value is returned.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be found or resolved for any reason. Cannot be null.
     * @return The string value of the flag, or the default value if the flag cannot be resolved for any reason.
     */
    override fun resolveStringValue(flagKey: String, defaultValue: String): String = resolveValue(flagKey, defaultValue)

    /**
     * Resolves an integer flag value.
     *
     * If the flag cannot be found, is not an integer, or an error occurs, the default value is returned.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be found or resolved for any reason. Cannot be null.
     * @return The integer value of the flag, or the default value if the flag cannot be resolved for any reason.
     */
    override fun resolveIntValue(flagKey: String, defaultValue: Int): Int = resolveValue(flagKey, defaultValue)

    /**
     * Resolves a double flag value.
     *
     * If the flag cannot be found, is not a double, or an error occurs, the default value is returned.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be found or resolved for any reason. Cannot be null.
     * @return The double value of the flag, or the default value if the flag cannot be resolved for any reason.
     */
    override fun resolveDoubleValue(flagKey: String, defaultValue: Double): Double = resolveValue(flagKey, defaultValue)

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
        resolveValue(flagKey, defaultValue)

    /**
     * Resolves a flag value with detailed resolution information.
     *
     * @param T The type of the flag value (Boolean, String, Int, Double, or JSONObject).
     * @param flagKey The name of the flag to query.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return [ResolutionDetails] with either the parsed value and metadata, or an error.
     */
    override fun <T : Any> resolve(flagKey: String, defaultValue: T): ResolutionDetails<T> {
        val resolution = readAndParseAssignment(flagKey, defaultValue)

        return when (resolution) {
            is InternalResolution.Success -> {
                trackResolution(resolution)
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

    // region Private Implementation

    /**
     * Resolves a flag value by chaining internal resolution and tracking.
     *
     * This helper method simplifies the convenience methods by encapsulating the common pattern
     * of resolving a flag internally and then processing it through the tracking layer.
     *
     * @param T The type of the flag value
     * @param flagKey The name of the flag to query
     * @param defaultValue The default value to return if resolution fails
     * @return The resolved value or the default value
     */
    private fun <T : Any> resolveValue(flagKey: String, defaultValue: T): T =
        resolveTracked(readAndParseAssignment(flagKey, defaultValue))

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

    // endregion

    // region InternalResolution

    private sealed class InternalResolution<T : Any> {
        abstract val flagKey: String

        data class Success<T : Any>(
            override val flagKey: String,
            val value: T,
            val flag: PrecomputedFlag,
            val context: EvaluationContext
        ) : InternalResolution<T>()

        data class Error<T : Any>(
            override val flagKey: String,
            val defaultValue: T,
            val errorCode: ErrorCode,
            val errorMessage: String
        ) : InternalResolution<T>()
    }

    /**
     * Fetches a flag from the repository and parses its value to the expected type.
     *
     * This side-effect free function is the single source of truth for:
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
    @Suppress("ReturnCount") // Early returns for improved readability
    private fun <T : Any> readAndParseAssignment(
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

        val conversionResult = FlagValueConverter.convert(
            variationValue = flag.variationValue,
            variationType = flag.variationType,
            targetType = defaultValue::class
        )

        return conversionResult.fold(
            onSuccess = { parsedValue ->
                InternalResolution.Success(
                    flagKey = flagKey,
                    value = parsedValue,
                    flag = flag,
                    context = context
                )
            },
            onFailure = { exception ->
                val errorCode: ErrorCode
                val errorMessage: String

                when (exception) {
                    is TypeMismatchException -> {
                        errorCode = ErrorCode.TYPE_MISMATCH
                        errorMessage = exception.message ?: "Type mismatch"
                    }
                    else -> {
                        errorCode = ErrorCode.PARSE_ERROR
                        val typeName = FlagValueConverter.getTypeName(defaultValue::class)
                        errorMessage = "Failed to parse value '${flag.variationValue}' as $typeName"

                        featureSdkCore.internalLogger.log(
                            InternalLogger.Level.WARN,
                            InternalLogger.Target.MAINTAINER,
                            { "Flag '$flagKey': $errorMessage - ${exception.message}" }
                        )
                    }
                }

                InternalResolution.Error(
                    flagKey = flagKey,
                    defaultValue = defaultValue,
                    errorCode = errorCode,
                    errorMessage = errorMessage
                )
            }
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
     * @param resolution The [InternalResolution] result from [readAndParseAssignment]
     * @return The resolved value from [InternalResolution.Success.value] on success,
     *         or [InternalResolution.Error.defaultValue] on error
     */
    private fun <T : Any> resolveTracked(resolution: InternalResolution<T>): T = when (resolution) {
        is InternalResolution.Success -> {
            trackResolution(resolution)
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

    private fun <T : Any> createSuccessResolution(precomputedFlag: PrecomputedFlag, value: T): ResolutionDetails<T> =
        ResolutionDetails(
            value = value,
            variant = precomputedFlag.variationKey.takeIf { it.isNotBlank() },
            reason = parseReason(precomputedFlag.reason),
            errorCode = null,
            errorMessage = null,
            flagMetadata = extractMetadata(precomputedFlag.extraLogging)
        )

    private fun <T : Any> createErrorResolution(
        flagKey: String,
        defaultValue: T,
        errorCode: ErrorCode,
        errorMessage: String
    ): ResolutionDetails<T> = ResolutionDetails(
        value = defaultValue,
        variant = null,
        reason = ResolutionReason.ERROR,
        errorCode = errorCode,
        errorMessage = "Flag '$flagKey': $errorMessage",
        flagMetadata = null
    )

    private fun parseReason(reasonString: String): ResolutionReason? {
        if (reasonString.isBlank()) {
            return null
        }

        return try {
            ResolutionReason.valueOf(reasonString)
        } catch (e: IllegalArgumentException) {
            featureSdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.TELEMETRY,
                { "Unknown resolution reason: $reasonString - ${e.message}" }
            )
            // Unknown reason string - return null
            null
        }
    }

    private fun extractMetadata(extraLogging: JSONObject): Map<String, Any> {
        if (extraLogging.length() == 0) {
            return emptyMap()
        }

        val metadata = mutableMapOf<String, Any>()
        extraLogging.keys().forEach { key ->
            val value = extraLogging.opt(key)
            when (value) {
                is String, is Number, is Boolean -> metadata[key] = value
            }
        }

        return metadata
    }

    private fun <T : Any> trackResolution(resolution: InternalResolution.Success<T>) {
        if (resolution.flag.doLog) {
            if (flagsConfiguration.trackExposures) {
                writeExposureEvent(resolution.flagKey, resolution.flag, resolution.context)
            }
            if (flagsConfiguration.rumIntegrationEnabled) {
                logEvaluation(resolution.flagKey, resolution.value)
            }
        }
    }

    // endregion
}
