/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("DEPRECATION")

package com.datadog.android.sdk.rules

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.datadog.android.Datadog
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.okhttp.RecordingDispatcher
import com.datadog.android.sdk.utils.addForgeSeed
import com.datadog.android.sdk.utils.addTrackingConsent
import com.datadog.android.sdk.utils.overrideProcessImportance
import fr.xgouchet.elmyr.Forge
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

internal open class MockServerActivityTestRule<T : Activity>(
    val activityClass: Class<T>,
    val keepRequests: Boolean = false,
    val trackingConsent: TrackingConsent = TrackingConsent.PENDING
) : ActivityTestRule<T>(activityClass) {

    private val mockWebServer: MockWebServer = MockWebServer()

    private val requests = mutableListOf<HandledRequest>()

    val forge = Forge()

    // region ActivityTestRule

    override fun getActivityIntent(): Intent {
        return Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            activityClass
        ).apply {
            addTrackingConsent(trackingConsent)
            addForgeSeed(forge.seed)
        }
    }

    override fun beforeActivityLaunched() {
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .cacheDir.deleteRecursively()
        requests.clear()
        mockWebServer.start()
        mockWebServer.dispatcher = RecordingDispatcher(keepRequests) { request, jsonBody, content ->
            requests.add(
                HandledRequest(
                    url = request.requestUrl.toString(),
                    headers = request.headers,
                    method = request.method,
                    jsonBody = jsonBody,
                    textBody = content,
                    requestBuffer = request.body.clone()
                )
            )
        }

        getConnectionUrl().let {
            RuntimeConfig.logsEndpointUrl = "$it/$LOGS_URL_SUFFIX"
            RuntimeConfig.tracesEndpointUrl = "$it/$TRACES_URL_SUFFIX"
            RuntimeConfig.rumEndpointUrl = "$it/$RUM_URL_SUFFIX"
            RuntimeConfig.sessionReplayEndpointUrl = "$it/$SESSION_REPlAY_URL_SUFFIX"
        }
        overrideProcessImportance()
        super.beforeActivityLaunched()
    }

    override fun afterActivityFinished() {
        mockWebServer.shutdown()

        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .cacheDir.deleteRecursively()
        GlobalRumMonitor.get().stopSession()
        Datadog.stopInstance()

        super.afterActivityFinished()
    }

    // endregion

    // region TestRule

    override fun apply(base: Statement, description: Description): Statement {
        val original = super.apply(base, description)
        return object : Statement() {
            override fun evaluate() {
                try {
                    original.evaluate()
                } catch (t: Throwable) {
                    Log.e(TAG, "Test run failed with Forge seed = ${forge.seed}")
                    throw t
                }
            }
        }
    }

    // endregion

    // region MockServerRule

    fun getRequests(): List<HandledRequest> {
        Log.i(TAG, "Caught ${requests.size} requests")
        return requests.toList()
    }

    fun getRequests(endpoint: String): List<HandledRequest> {
        val filteredRequests = requests.filter { it.url?.startsWith(endpoint) ?: false }.toList()
        Log.i(TAG, "Caught ${filteredRequests.size} requests for endpoint: $endpoint")
        return filteredRequests
    }

    fun getConnectionUrl(): String = mockWebServer.url("/").toString().removeSuffix("/")

    // endregion

    // region Internal

    internal fun File.deleteRecursively(onFileDeleted: (fileName: String) -> Unit) {
        walkBottomUp()
            .forEach {
                it.delete()
                onFileDeleted(it.canonicalPath)
            }
    }

    // endregion

    companion object {
        private const val TAG = "MockServerActivityTestRule"

        const val LOGS_URL_SUFFIX = "logs"
        const val TRACES_URL_SUFFIX = "traces"
        const val RUM_URL_SUFFIX = "rum"
        const val SESSION_REPlAY_URL_SUFFIX = "session-replay"
    }
}
