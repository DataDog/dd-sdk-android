/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.log

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.log.Logger
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getTrackingConsent
import fr.xgouchet.elmyr.Forge

internal class ActivityLifecycleLogs : AppCompatActivity() {

    private val forge = Forge()

    private val randomAttributes = forge.aMap { anAlphabeticalString() to anHexadecimalString() }
    private val sentMessages = mutableListOf<Pair<Int, String>>()

    lateinit var logger: Logger

    // region Activity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = DatadogConfig.Builder(
            RuntimeConfig.DD_TOKEN,
            RuntimeConfig.INTEGRATION_TESTS_ENVIRONMENT
        ).useCustomLogsEndpoint(RuntimeConfig.logsEndpointUrl)
            .useCustomTracesEndpoint(RuntimeConfig.tracesEndpointUrl)
            .build()

        val trackingConsent = intent.getTrackingConsent()
        Datadog.initialize(this, trackingConsent, config)

        logger = RuntimeConfig.logger()
        setContentView(R.layout.main_activity_layout)

        log(Log.INFO, "ActivityLifecycleLogs: onCreate ${System.nanoTime()}")
        logRandom()
    }

    override fun onStart() {
        super.onStart()
        log(Log.INFO, "ActivityLifecycleLogs: onStart ${System.nanoTime()}")
        logRandom()
    }

    override fun onResume() {
        super.onResume()
        log(Log.INFO, "ActivityLifecycleLogs: onResume ${System.nanoTime()}")
        logRandom()
    }

    // endregion

    // region Tests

    fun getSentMessages(): List<Pair<Int, String>> {
        return sentMessages.toList()
    }

    fun localAttributes(): Map<String, String> {
        return randomAttributes
    }

    // endregion

    // region Internal

    private fun logRandom() {
        val level = forge.anElementFrom(
            Log.DEBUG,
            Log.VERBOSE,
            Log.INFO,
            Log.WARN,
            Log.ERROR,
            Log.ASSERT
        )
        val message = forge.anAlphabeticalString()
        log(level, message)
    }

    private fun log(level: Int, message: String) {
        logger.log(level, message, null, randomAttributes)
        sentMessages.add(level to message)
    }

    // endregion
}
