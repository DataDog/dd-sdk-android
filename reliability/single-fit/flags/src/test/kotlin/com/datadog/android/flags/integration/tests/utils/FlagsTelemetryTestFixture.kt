/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration.tests.utils

import com.datadog.android.api.feature.Feature
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.stub.StubExecutorService
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.integration.FlagsClientTest.Companion.createStubSdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import org.json.JSONObject
import java.util.concurrent.ExecutorService

/** Real Flags and RUM features using the shared single-FIT transport, storage, and event fixtures. */
internal class FlagsTelemetryTestFixture(forge: Forge) : AutoCloseable {
    val stubCore = createStubSdkCore(forge)
    var pauseAssignments = false
    private val pendingAssignments = ArrayDeque<Runnable>()
    val core = object : InternalSdkCore by stubCore {
        override fun createSingleThreadExecutorService(executorContext: String): ExecutorService {
            val executor = StubExecutorService(executorContext)
            return object : ExecutorService by executor {
                override fun execute(command: Runnable) {
                    if (executorContext == "flags-network" && pauseAssignments) {
                        pendingAssignments.add(command)
                    } else {
                        executor.execute(command)
                    }
                }
            }
        }
    }

    init {
        stubCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        Rum.enable(RumConfiguration.Builder(forge.anAlphabeticalString()).trackNonFatalAnrs(false).build(), core)
        restartFlags()
        GlobalRumMonitor.get(core).startView("view", "view")
        assignments()
    }

    fun restartFlags() {
        Flags.enable(
            FlagsConfiguration.Builder()
                .useCustomFlagEndpoint("https://flags.example.test/assignments")
                .initializationTimeout(0)
                .build(),
            core
        )
    }

    fun assignments(enabled: Boolean = true) {
        val body = precomputedAssignmentsBody(
            mapOf(
                "enabled" to assignment("boolean", enabled, if (enabled) "on" else "off"),
                "untracked" to assignment("boolean", true).copy(doLog = false),
                "text" to assignment("string", "assigned"),
                "integer" to assignment("integer", 7),
                "number" to assignment("number", 2.5),
                "object" to assignment("object", JSONObject().put("assigned", true)),
                "malformed" to assignment("boolean", "invalid").copy(doLog = false),
                "future-reason" to assignment("boolean", true).copy(doLog = false, reason = "FUTURE_REASON")
            )
        )
        stubCore.stubNetworkResponses { httpResponse(it, body = body) }
    }

    fun failAssignments() {
        stubCore.stubNetworkResponses { httpResponse(it, code = 500) }
    }

    fun completeNextAssignment() {
        pendingAssignments.removeFirst().run()
    }

    fun events(feature: String): List<JsonObject> = stubCore.eventsWritten(feature).map {
        JsonParser.parseString(it.eventData).asJsonObject
    }

    fun evaluationCount(): Int = events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME).sumOf {
        it["evaluation_count"].asInt
    }

    fun flush() {
        stubCore.stopFeatures()
    }

    override fun close() {
        stubCore.stopFeatures()
    }

    private fun assignment(type: String, value: Any, variant: String = "on") = FakeAssignment(
        variationType = type,
        variationValue = value,
        variationKey = variant,
        allocationKey = "allocation"
    )

    companion object {
        const val CONTROL = "dd_evaluation_telemetry_opt_out"
    }
}
