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
import com.datadog.android.log.Logger
import com.datadog.android.log.Logs
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getForgeSeed
import com.datadog.android.sdk.utils.getTrackingConsent
import com.datadog.android.trace.Traces
import fr.xgouchet.elmyr.Forge
import java.util.Random

internal class ActivityLifecycleLogs : AppCompatActivity() {

    private val forge by lazy { Forge().apply { seed = intent.getForgeSeed() } }

    private val randomAttributes by lazy { forge.aMap { anAlphabeticalString() to anHexadecimalString() } }
    private val sentMessages = mutableListOf<Pair<Int, String>>()

    lateinit var logger: Logger

    // region Activity

    @Suppress("CheckInternal")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val credentials = RuntimeConfig.credentials()
        val config = RuntimeConfig.configBuilder().build()
        val trackingConsent = intent.getTrackingConsent()

        Datadog.setVerbosity(Log.VERBOSE)
        val sdkCore = Datadog.initialize(this, credentials, config, trackingConsent)
        checkNotNull(sdkCore)
        val featureActivations = mutableListOf(
            { Logs.enable(RuntimeConfig.logsConfigBuilder().build(), sdkCore) },
            { Traces.enable(RuntimeConfig.tracesConfigBuilder().build(), sdkCore) }
        )
        featureActivations.shuffled(Random(intent.getForgeSeed())).forEach { it() }

        logger = RuntimeConfig.logger(sdkCore)
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
