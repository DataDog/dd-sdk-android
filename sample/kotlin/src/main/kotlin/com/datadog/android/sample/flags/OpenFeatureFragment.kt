/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.flags

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.datadog.android.sample.R
import dev.openfeature.kotlin.sdk.Client
import dev.openfeature.kotlin.sdk.OpenFeatureAPI
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import dev.openfeature.kotlin.sdk.exceptions.ErrorCode
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Fragment demonstrating OpenFeature SDK integration with Datadog Flags.
 */
internal class OpenFeatureFragment :
    Fragment(),
    View.OnClickListener {

    private lateinit var flagKeyInput: EditText
    private lateinit var defaultValueInput: EditText
    private lateinit var typeSpinner: Spinner
    private lateinit var resultTextView: TextView
    private lateinit var providerStateTextView: TextView

    // region Fragment

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_openfeature, container, false)
        flagKeyInput = rootView.findViewById(R.id.flag_key)
        defaultValueInput = rootView.findViewById(R.id.default_value)
        typeSpinner = rootView.findViewById(R.id.type_spinner)
        resultTextView = rootView.findViewById(R.id.result_text)
        providerStateTextView = rootView.findViewById(R.id.provider_state_text)
        rootView.findViewById<View>(R.id.evaluate_flag).setOnClickListener(this)

        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.flag_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            typeSpinner.adapter = adapter
        }

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeProviderState()
    }

    private fun observeProviderState() {
        val provider = OpenFeatureAPI.getProvider()
        if (provider == null) {
            updateProviderState(STATE_NOT_SET)
            return
        }

        // Display initial state
        updateProviderState(STATE_INITIALIZING)

        // Observe state changes
        viewLifecycleOwner.lifecycleScope.launch {
            provider.observe()
                .catch {
                    updateProviderState(STATE_ERROR)
                }
                .collect { event ->
                    val stateName = when (event) {
                        is OpenFeatureProviderEvents.ProviderReady ->
                            STATE_READY
                        is OpenFeatureProviderEvents.ProviderStale ->
                            STATE_STALE
                        is OpenFeatureProviderEvents.ProviderError ->
                            STATE_ERROR
                        is OpenFeatureProviderEvents.ProviderConfigurationChanged ->
                            STATE_CONFIG_CHANGED
                        else -> event::class.simpleName ?: "Unknown"
                    }
                    updateProviderState(stateName)
                }
        }
    }

    private fun updateProviderState(state: String) {
        providerStateTextView.text = state
    }

    // endregion

    // region View.OnClickListener

    override fun onClick(v: View) {
        val flagKey = flagKeyInput.text.toString()
        val defaultValue = defaultValueInput.text.toString()
        val selectedType = typeSpinner.selectedItemPosition

        if (flagKey.isEmpty()) {
            resultTextView.text = "Error: Flag key cannot be empty"
            return
        }

        evaluateFlagAndDisplayResult(flagKey, defaultValue, selectedType)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun evaluateFlagAndDisplayResult(flagKey: String, defaultValue: String, selectedType: Int) {
        try {
            val client = OpenFeatureAPI.getClient()
            val result = when (selectedType) {
                FLAG_TYPE_BOOLEAN -> evaluateBooleanFlag(client, flagKey, defaultValue)
                FLAG_TYPE_STRING -> evaluateStringFlag(client, flagKey, defaultValue)
                FLAG_TYPE_INTEGER -> evaluateIntegerFlag(client, flagKey, defaultValue)
                FLAG_TYPE_DOUBLE -> evaluateDoubleFlag(client, flagKey, defaultValue)
                else -> "Unknown type selected"
            }
            resultTextView.text = result
        } catch (e: Exception) {
            displayEvaluationError(e)
        }
    }

    private fun evaluateBooleanFlag(client: Client, flagKey: String, defaultValue: String): String {
        val default = defaultValue.toBooleanStrictOrNull() ?: false
        val details = client.getBooleanDetails(flagKey, default)
        return formatEvaluationDetails(
            "Boolean",
            flagKey,
            details.value,
            details.reason,
            details.variant,
            details.errorCode,
            details.errorMessage
        )
    }

    private fun evaluateStringFlag(client: Client, flagKey: String, defaultValue: String): String {
        val details = client.getStringDetails(flagKey, defaultValue)
        return formatEvaluationDetails(
            "String",
            flagKey,
            details.value,
            details.reason,
            details.variant,
            details.errorCode,
            details.errorMessage
        )
    }

    private fun evaluateIntegerFlag(client: Client, flagKey: String, defaultValue: String): String {
        val default = defaultValue.toIntOrNull() ?: 0
        val details = client.getIntegerDetails(flagKey, default)
        return formatEvaluationDetails(
            "Integer",
            flagKey,
            details.value,
            details.reason,
            details.variant,
            details.errorCode,
            details.errorMessage
        )
    }

    private fun evaluateDoubleFlag(client: Client, flagKey: String, defaultValue: String): String {
        val default = defaultValue.toDoubleOrNull() ?: 0.0
        val details = client.getDoubleDetails(flagKey, default)
        return formatEvaluationDetails(
            "Double",
            flagKey,
            details.value,
            details.reason,
            details.variant,
            details.errorCode,
            details.errorMessage
        )
    }

    private fun displayEvaluationError(e: Exception) {
        val errorMsg = buildString {
            appendLine("Evaluation Error")
            appendLine()
            appendLine("Error: ${e.message}")
            appendLine()
            appendLine("Stack trace:")
            appendLine(e.stackTraceToString())
        }
        resultTextView.text = errorMsg
    }

    // endregion

    // region Formatting

    private fun formatEvaluationDetails(
        type: String,
        flagKey: String,
        value: Any?,
        reason: String?,
        variant: String?,
        errorCode: ErrorCode?,
        errorMessage: String?
    ): String = buildString {
        appendLine("Evaluation Details")
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLine()

        appendLine("Flag Key: $flagKey")
        appendLine("Type: $type")
        appendLine("Value: $value")

        if (reason != null) {
            appendLine()
            appendLine("Reason: $reason")
            appendLine("  ${getReasonExplanation(reason)}")
        }

        if (variant != null) {
            appendLine()
            appendLine("Variant: $variant")
        }

        if (errorCode != null) {
            appendLine()
            appendLine("Error Code: $errorCode")
        }

        if (errorMessage != null) {
            appendLine("Error Message: $errorMessage")
        }

        appendLine()
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    private fun getReasonExplanation(reason: String): String = when (reason.uppercase()) {
        "STATIC" -> "Flag value is static (configured in code)"
        "DEFAULT" -> "Using default value (flag not found or provider not ready)"
        "TARGETING_MATCH" -> "Flag matched targeting rules"
        "SPLIT" -> "Flag evaluation used percentage rollout"
        "DISABLED" -> "Flag is disabled"
        "CACHED" -> "Value returned from cache"
        "ERROR" -> "An error occurred during evaluation"
        "STALE" -> "Value may be stale (using cached value, refresh failed)"
        "UNKNOWN" -> "Reason is unknown or not provided"
        else -> "Evaluation reason: $reason"
    }

    // endregion

    companion object {
        private const val FLAG_TYPE_BOOLEAN = 0
        private const val FLAG_TYPE_STRING = 1
        private const val FLAG_TYPE_INTEGER = 2
        private const val FLAG_TYPE_DOUBLE = 3

        private const val STATE_NOT_SET = "NOT_SET"
        private const val STATE_INITIALIZING = "INITIALIZING"
        private const val STATE_READY = "READY"
        private const val STATE_STALE = "STALE"
        private const val STATE_ERROR = "ERROR"
        private const val STATE_CONFIG_CHANGED = "CONFIG_CHANGED"
    }
}
