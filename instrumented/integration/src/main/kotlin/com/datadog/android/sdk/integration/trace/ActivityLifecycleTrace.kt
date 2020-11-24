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
import com.datadog.android.DatadogConfig
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getTrackingConsent
import com.datadog.android.tracing.AndroidTracer
import com.datadog.opentracing.DDSpan
import fr.xgouchet.elmyr.Forge
import io.opentracing.Scope
import java.util.LinkedList

internal class ActivityLifecycleTrace : AppCompatActivity() {

    private val forge = Forge()

    lateinit var tracer: AndroidTracer
    private val sentSpans = LinkedList<DDSpan>()
    private val sentLogs = LinkedList<Pair<Int, String>>()
    lateinit var activityStartScope: Scope
    lateinit var activityResumeScope: Scope

    // region Activity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = DatadogConfig.Builder(
            RuntimeConfig.DD_TOKEN,
            RuntimeConfig.INTEGRATION_TESTS_ENVIRONMENT
        )
            .useCustomLogsEndpoint(RuntimeConfig.logsEndpointUrl)
            .useCustomTracesEndpoint(RuntimeConfig.tracesEndpointUrl)
            .build()

        Datadog.initialize(this, intent.getTrackingConsent(), config)

        tracer = RuntimeConfig.tracer()
        setContentView(R.layout.main_activity_layout)
    }

    override fun onStart() {
        super.onStart()
        activityStartScope = buildSpan(forge.anAlphabeticalString())
    }

    override fun onResume() {
        super.onResume()
        activityResumeScope = buildSpan(forge.anAlphabeticalString())
    }

    override fun onPause() {
        super.onPause()
        activityResumeScope.close()
    }

    override fun onStop() {
        super.onStop()
        activityStartScope.close()
    }

    // endregion

    // region Tests

    fun getSentSpans(): LinkedList<DDSpan> {
        return sentSpans
    }

    fun getSentLogs(): LinkedList<Pair<Int, String>> {
        return sentLogs
    }

    // endregion

    // region Internal

    private fun buildSpan(title: String): Scope {
        val scope = tracer.buildSpan(title).startActive(true)
        val ddSpan = tracer.activeSpan() as DDSpan
        ddSpan.log(title)
        sentLogs.add(Log.VERBOSE to title)
        sentSpans.add(ddSpan)
        return scope
    }

    // endregion
}
