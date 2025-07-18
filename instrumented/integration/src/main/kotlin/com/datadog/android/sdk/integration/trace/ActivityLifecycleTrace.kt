/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.trace

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.Datadog
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.log.Logs
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getForgeSeed
import com.datadog.android.sdk.utils.getTrackingConsent
import com.datadog.android.trace.Trace
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.tracer.DatadogTracer
import fr.xgouchet.elmyr.Forge
import java.util.LinkedList
import java.util.Random

internal class ActivityLifecycleTrace : AppCompatActivity() {

    private val forge by lazy { Forge().apply { seed = intent.getForgeSeed() } }

    private lateinit var tracer: DatadogTracer
    private val sentSpans = LinkedList<DatadogSpan>()
    private val sentLogs = LinkedList<Pair<Int, String>>()
    private lateinit var activityStartSpan: DatadogSpan
    private lateinit var activityResumeSpan: DatadogSpan

    // region Activity

    @Suppress("CheckInternal")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = RuntimeConfig.configBuilder().build()
        val trackingConsent = intent.getTrackingConsent()

        Datadog.setVerbosity(Log.VERBOSE)
        val sdkCore = checkNotNull(
            Datadog.initialize(this, config, trackingConsent)
        )

        listOf(
            { Logs.enable(RuntimeConfig.logsConfigBuilder().build(), sdkCore) },
            { Trace.enable(RuntimeConfig.tracesConfigBuilder().build(), sdkCore) }
        )
            .shuffled(Random(intent.getForgeSeed()))
            .forEach { it() }

        tracer = RuntimeConfig.tracer(sdkCore)
        setContentView(R.layout.main_activity_layout)
    }

    override fun onStart() {
        super.onStart()
        activityStartSpan = buildSpan(forge.anAlphabeticalString())
    }

    override fun onResume() {
        super.onResume()
        activityResumeSpan = buildSpan(forge.anAlphabeticalString())
    }

    override fun onPause() {
        super.onPause()
        activityResumeSpan.finish()
    }

    override fun onStop() {
        super.onStop()
        activityStartSpan.finish()
    }

    // endregion

    // region Tests

    fun getSentSpans(): LinkedList<DatadogSpan> {
        return sentSpans
    }

    fun getSentLogs(): LinkedList<Pair<Int, String>> {
        return sentLogs
    }

    fun getDatadogContext(): DatadogContext? {
        return (Datadog.getInstance() as InternalSdkCore).getDatadogContext()
    }

    // endregion

    // region Internal

    private fun buildSpan(title: String): DatadogSpan {
        val span = tracer.buildSpan(title).start()
        checkNotNull(tracer.activateSpan(span)) { "Span activation failed" }
        val ddSpan = tracer.activeSpan() as DatadogSpan
        ddSpan.logMessage(title)
        sentLogs.add(Log.VERBOSE to title)
        sentSpans.add(ddSpan)
        return ddSpan
    }

    // endregion
}
